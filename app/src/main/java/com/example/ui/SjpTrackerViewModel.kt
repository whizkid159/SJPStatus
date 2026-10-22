package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class BackgroundBrowserCommand {
    data class PerformHeadlessLogin(val cnic: String, val password: String) : BackgroundBrowserCommand()
    data class FetchAllPortalData(val bearerToken: String) : BackgroundBrowserCommand()
    data class SubmitJobApplicationInBrowser(val jobId: String, val bearerToken: String) : BackgroundBrowserCommand()
}

data class SjpFilterState(
    val searchQuery: String = "",
    val selectedStatus: ApplicationStatus? = null,
    val selectedDepartment: String? = null,
    val onlyWithReminders: Boolean = false,
    val onlyUnreadChanges: Boolean = false
)

data class SjpUiState(
    val allJobs: List<AppliedJobEntity> = emptyList(),
    val filteredJobs: List<AppliedJobEntity> = emptyList(),
    val historyList: List<StatusHistoryEntity> = emptyList(),
    val evaluatedOpenJobs: List<EligibilityEvaluation> = emptyList(),
    val onlyEligibleFilter: Boolean = true,
    val openJobsSearchQuery: String = "",
    val sessionState: SjpSessionState = SjpSessionState(),
    val filterState: SjpFilterState = SjpFilterState(),
    val availableDepartments: List<String> = emptyList(),
    val isSyncing: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isApplyingJobId: String? = null,
    val loginProgressMessage: String? = null,
    val bannerFeedback: String? = null,
    val selectedJobForDetail: AppliedJobEntity? = null,
    val selectedEvaluationForApply: EligibilityEvaluation? = null,
    val initialDetailTab: Int = 0
) {
    val totalCount: Int get() = allJobs.size
    val shortlistedTotalCount: Int
        get() = allJobs.count {
            it.statusEnum == ApplicationStatus.SHORTLISTED ||
                it.statusEnum == ApplicationStatus.TEMP_SHORTLISTED
        }
    val offeredTotalCount: Int
        get() = allJobs.count {
            it.statusEnum == ApplicationStatus.OFFERED ||
                it.statusEnum == ApplicationStatus.TEMP_OFFERED ||
                it.jobOffered ||
                it.hasOfferRecord
        }
    val rejectedTotalCount: Int
        get() = allJobs.count { it.statusEnum == ApplicationStatus.REJECTED }
    val unreadHistoryCount: Int
        get() = historyList.count { it.isUnread }
    val eligibleJobsCount: Int
        get() = evaluatedOpenJobs.count { it.isEligible && !it.isAlreadyApplied }
}

private data class InternalControlState(
    val filterState: SjpFilterState = SjpFilterState(),
    val onlyEligibleFilter: Boolean = true,
    val openJobsSearchQuery: String = "",
    val isSyncing: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isApplyingJobId: String? = null,
    val loginProgressMessage: String? = null,
    val bannerFeedback: String? = null,
    val selectedJobId: String? = null,
    val selectedOpenJobIdForApply: String? = null,
    val initialDetailTab: Int = 0
)

class SjpTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SjpDatabase.getInstance(application)
    private val sessionPrefs = SessionPreferencesRepository(application)
    val repository = SjpRepository(application, database.sjpDao(), sessionPrefs)

    private val controlState = MutableStateFlow(InternalControlState())

    private val _browserCommands = MutableSharedFlow<BackgroundBrowserCommand>(extraBufferCapacity = 4)
    val browserCommands: SharedFlow<BackgroundBrowserCommand> = _browserCommands.asSharedFlow()

    val uiState: StateFlow<SjpUiState> = combine(
        repository.jobsFlow,
        repository.historyFlow,
        repository.openJobsFlow,
        repository.sessionFlow,
        controlState
    ) { jobs, history, openJobs, session, ctrl ->
        val f = ctrl.filterState
        val filtered = jobs.filter { job ->
            val matchesQuery = f.searchQuery.isBlank() ||
                job.jobTitle.contains(f.searchQuery, ignoreCase = true) ||
                job.departmentName.contains(f.searchQuery, ignoreCase = true) ||
                job.rawStatusString.contains(f.searchQuery, ignoreCase = true) ||
                job.applicationId.contains(f.searchQuery, ignoreCase = true) ||
                job.reasonForRejected.contains(f.searchQuery, ignoreCase = true)

            val matchesStatus = f.selectedStatus == null || job.statusEnum == f.selectedStatus
            val matchesDept = f.selectedDepartment == null || job.departmentName == f.selectedDepartment
            val matchesReminder = !f.onlyWithReminders || (job.interviewTimestamp != null && job.interviewTimestamp > 0L)
            val matchesUnread = !f.onlyUnreadChanges || job.hasUnreadChange

            matchesQuery && matchesStatus && matchesDept && matchesReminder && matchesUnread
        }

        val evaluations = openJobs.map { openJob ->
            EligibilityMatcher.evaluate(openJob, session.candidateProfile, jobs)
        }.filter { eval ->
            val matchesSearch = ctrl.openJobsSearchQuery.isBlank() ||
                eval.job.jobTitle.contains(ctrl.openJobsSearchQuery, ignoreCase = true) ||
                eval.job.departmentName.contains(ctrl.openJobsSearchQuery, ignoreCase = true) ||
                eval.job.bpsScale.contains(ctrl.openJobsSearchQuery, ignoreCase = true) ||
                eval.job.requiredMajor.contains(ctrl.openJobsSearchQuery, ignoreCase = true)
            val matchesEligible = !ctrl.onlyEligibleFilter || eval.isEligible || eval.matchScorePercent >= 65
            matchesSearch && matchesEligible
        }.sortedWith(
            compareByDescending<EligibilityEvaluation> { it.isEligible && !it.isAlreadyApplied }
                .thenByDescending { it.matchScorePercent }
        )

        val departments = jobs.map { it.departmentName }.distinct().sorted()
        val selectedJob = ctrl.selectedJobId?.let { id -> jobs.firstOrNull { it.applicationId == id } }
        val selectedEvalForApply = ctrl.selectedOpenJobIdForApply?.let { jId ->
            openJobs.firstOrNull { it.jobId == jId }?.let { oj ->
                EligibilityMatcher.evaluate(oj, session.candidateProfile, jobs)
            }
        }

        SjpUiState(
            allJobs = jobs,
            filteredJobs = filtered,
            historyList = history,
            evaluatedOpenJobs = evaluations,
            onlyEligibleFilter = ctrl.onlyEligibleFilter,
            openJobsSearchQuery = ctrl.openJobsSearchQuery,
            sessionState = session,
            filterState = f,
            availableDepartments = departments,
            isSyncing = ctrl.isSyncing,
            isLoggingIn = ctrl.isLoggingIn,
            isApplyingJobId = ctrl.isApplyingJobId,
            loginProgressMessage = ctrl.loginProgressMessage,
            bannerFeedback = ctrl.bannerFeedback,
            selectedJobForDetail = selectedJob,
            selectedEvaluationForApply = selectedEvalForApply,
            initialDetailTab = ctrl.initialDetailTab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SjpUiState()
    )

    init {
        // Android 16 (API 36) Safe Deferred Startup:
        // Wait until after the first UI frame renders before touching CookieManager or network
        viewModelScope.launch {
            delay(800)
            val session = sessionPrefs.sessionFlow.first()
            val combinedCookies = repository.getCombinedPortalCookies(session.cookieHeader)
            if (combinedCookies.isNotBlank() || session.bearerToken.isNotBlank()) {
                syncFromPortalApi()
            } else if (session.rememberCredentials && session.hasSavedCredentials) {
                loginWithCnicAndPassword(
                    cnic = session.savedCnic,
                    password = session.savedPassword,
                    rememberCredentials = true
                )
            }
        }

        // 30-Minute Background Polling Loop
        viewModelScope.launch {
            while (isActive) {
                val session = sessionPrefs.sessionFlow.first()
                val intervalMs = (session.pollingIntervalMinutes.coerceAtLeast(5)) * 60_000L
                delay(intervalMs)
                val latestSession = sessionPrefs.sessionFlow.first()
                val combinedCookies = repository.getCombinedPortalCookies(latestSession.cookieHeader)
                if (latestSession.autoPollEnabled && (combinedCookies.isNotBlank() || latestSession.bearerToken.isNotBlank())) {
                    _browserCommands.tryEmit(BackgroundBrowserCommand.FetchAllPortalData(latestSession.bearerToken))
                    controlState.value = controlState.value.copy(isSyncing = true)
                    val res = repository.pollEndpointFromNetwork()
                    repository.syncProfileAndOpenJobsFromNetwork()
                    controlState.value = controlState.value.copy(
                        isSyncing = false,
                        bannerFeedback = res.message
                    )
                } else if (latestSession.autoPollEnabled && latestSession.rememberCredentials && latestSession.hasSavedCredentials) {
                    _browserCommands.tryEmit(
                        BackgroundBrowserCommand.PerformHeadlessLogin(
                            latestSession.savedCnic,
                            latestSession.savedPassword
                        )
                    )
                }
            }
        }
    }

    /**
     * Native Login entry point: Saves credentials if requested, attempts direct API login,
     * and dispatches headless DOM login to the invisible background browser engine.
     */
    fun loginWithCnicAndPassword(
        cnic: String,
        password: String,
        rememberCredentials: Boolean
    ) {
        if (cnic.isBlank() || password.isBlank()) {
            controlState.value = controlState.value.copy(
                bannerFeedback = "Please enter both your CNIC and SJP Password."
            )
            return
        }

        viewModelScope.launch {
            sessionPrefs.saveCredentials(cnic, password, rememberCredentials)
            controlState.value = controlState.value.copy(
                isLoggingIn = true,
                isSyncing = true,
                loginProgressMessage = "Authenticating CNIC ${cnic.trim()} with sjp.gos.pk in background…"
            )

            // 1. Trigger headless background browser DOM login
            _browserCommands.tryEmit(
                BackgroundBrowserCommand.PerformHeadlessLogin(cnic.trim(), password)
            )

            // 2. Simultaneously try direct REST API login against usr-svc.sjp.gos.pk
            val directSuccess = repository.attemptDirectApiLogin(cnic.trim(), password)
            if (directSuccess) {
                controlState.value = controlState.value.copy(
                    loginProgressMessage = "Direct SJP API authenticated! Fetching applied jobs & eligibility profile…"
                )
                val syncRes = repository.pollEndpointFromNetwork()
                repository.syncProfileAndOpenJobsFromNetwork()
                controlState.value = controlState.value.copy(
                    isLoggingIn = false,
                    isSyncing = false,
                    loginProgressMessage = null,
                    bannerFeedback = syncRes.message
                )
            } else {
                // Allow background browser DOM login 5 seconds to complete SPA login & token capture
                delay(5500)
                val updatedSession = sessionPrefs.sessionFlow.first()
                if (controlState.value.isLoggingIn) {
                    _browserCommands.tryEmit(
                        BackgroundBrowserCommand.FetchAllPortalData(updatedSession.bearerToken)
                    )
                    delay(2000)
                    val pollRes = repository.pollEndpointFromNetwork()
                    repository.syncProfileAndOpenJobsFromNetwork()
                    controlState.value = controlState.value.copy(
                        isLoggingIn = false,
                        isSyncing = false,
                        loginProgressMessage = null,
                        bannerFeedback = pollRes.message
                    )
                }
            }
        }
    }

    fun updateCandidateEligibilityProfile(profile: CandidateProfile) {
        viewModelScope.launch {
            sessionPrefs.updateCandidateProfile(profile)
            controlState.value = controlState.value.copy(
                bannerFeedback = "Updated your education & eligibility profile (${profile.highestDegreeLevel})"
            )
        }
    }

    fun setOnlyEligibleJobsFilter(onlyEligible: Boolean) {
        controlState.value = controlState.value.copy(onlyEligibleFilter = onlyEligible)
    }

    fun updateOpenJobsSearchQuery(query: String) {
        controlState.value = controlState.value.copy(openJobsSearchQuery = query)
    }

    fun openApplyConfirmationSheet(evaluation: EligibilityEvaluation) {
        controlState.value = controlState.value.copy(
            selectedOpenJobIdForApply = evaluation.job.jobId
        )
    }

    fun closeApplyConfirmationSheet() {
        controlState.value = controlState.value.copy(selectedOpenJobIdForApply = null)
    }

    fun confirmAndApplyToJob(job: OpenJobEntity) {
        viewModelScope.launch {
            controlState.value = controlState.value.copy(
                isApplyingJobId = job.jobId,
                selectedOpenJobIdForApply = null
            )
            val session = sessionPrefs.sessionFlow.first()
            // Trigger both background browser SPA apply and direct API apply
            _browserCommands.tryEmit(
                BackgroundBrowserCommand.SubmitJobApplicationInBrowser(job.jobId, session.bearerToken)
            )
            val result = repository.applyToJobOnPortal(job)
            controlState.value = controlState.value.copy(
                isApplyingJobId = null,
                bannerFeedback = result.message
            )
        }
    }

    fun updateSearchQuery(query: String) {
        val current = controlState.value
        controlState.value = current.copy(
            filterState = current.filterState.copy(searchQuery = query)
        )
    }

    fun selectStatusFilter(status: ApplicationStatus?) {
        val current = controlState.value
        val nextStatus = if (current.filterState.selectedStatus == status) null else status
        controlState.value = current.copy(
            filterState = current.filterState.copy(selectedStatus = nextStatus)
        )
    }

    fun selectDepartmentFilter(department: String?) {
        val current = controlState.value
        val nextDept = if (current.filterState.selectedDepartment == department) null else department
        controlState.value = current.copy(
            filterState = current.filterState.copy(selectedDepartment = nextDept)
        )
    }

    fun toggleRemindersFilter() {
        val current = controlState.value
        controlState.value = current.copy(
            filterState = current.filterState.copy(onlyWithReminders = !current.filterState.onlyWithReminders)
        )
    }

    fun toggleUnreadFilter() {
        val current = controlState.value
        controlState.value = current.copy(
            filterState = current.filterState.copy(onlyUnreadChanges = !current.filterState.onlyUnreadChanges)
        )
    }

    fun clearAllFilters() {
        controlState.value = controlState.value.copy(filterState = SjpFilterState())
    }

    fun openJobDetail(job: AppliedJobEntity, initialTab: Int = 0) {
        viewModelScope.launch {
            if (job.hasUnreadChange) {
                repository.markJobRead(job.applicationId)
            }
        }
        controlState.value = controlState.value.copy(
            selectedJobId = job.applicationId,
            initialDetailTab = initialTab
        )
    }

    fun closeJobDetail() {
        controlState.value = controlState.value.copy(selectedJobId = null)
    }

    fun dismissBannerFeedback() {
        controlState.value = controlState.value.copy(bannerFeedback = null)
    }

    fun syncFromPortalApi() {
        viewModelScope.launch {
            val session = sessionPrefs.sessionFlow.first()
            _browserCommands.tryEmit(BackgroundBrowserCommand.FetchAllPortalData(session.bearerToken))
            controlState.value = controlState.value.copy(isSyncing = true)
            val result = repository.pollEndpointFromNetwork()
            repository.syncProfileAndOpenJobsFromNetwork()
            controlState.value = controlState.value.copy(
                isSyncing = false,
                bannerFeedback = result.message
            )
        }
    }

    fun onWebViewSessionCaptured(
        bearerToken: String?,
        cookieHeader: String?,
        candidateName: String? = null
    ) {
        viewModelScope.launch {
            sessionPrefs.saveCapturedSession(
                bearerToken = bearerToken,
                cookieHeader = cookieHeader,
                candidateName = candidateName
            )
        }
    }

    fun onWebViewPayloadCaptured(
        rawJson: String,
        sourceLabel: String = "Background Browser Sync"
    ) {
        viewModelScope.launch {
            controlState.value = controlState.value.copy(isSyncing = true)
            val result = repository.ingestRawApiPayload(
                rawJson = rawJson,
                sourceLabel = sourceLabel
            )
            controlState.value = controlState.value.copy(
                isSyncing = false,
                isLoggingIn = false,
                loginProgressMessage = null,
                bannerFeedback = result.message
            )
        }
    }

    fun onWebViewOpenJobsCaptured(rawJson: String) {
        viewModelScope.launch {
            val count = repository.ingestOpenJobsPayload(rawJson)
            if (count > 0) {
                controlState.value = controlState.value.copy(
                    bannerFeedback = "Synced $count open SJP job vacancies for eligibility matching"
                )
            }
        }
    }

    fun onWebViewCandidateProfileCaptured(rawJson: String) {
        viewModelScope.launch {
            repository.ingestCandidateProfilePayload(rawJson)
        }
    }

    fun onWebViewFetchError(errorMsg: String) {
        controlState.value = controlState.value.copy(
            isSyncing = false,
            isLoggingIn = false,
            loginProgressMessage = null,
            bannerFeedback = errorMsg
        )
    }

    fun saveJobNotesAndInterview(
        applicationId: String,
        notes: String,
        interviewTimestamp: Long?
    ) {
        viewModelScope.launch {
            repository.updateJobNotesAndInterview(applicationId, notes, interviewTimestamp)
            controlState.value = controlState.value.copy(
                bannerFeedback = "Saved notes & interview reminder for Application #$applicationId"
            )
        }
    }

    fun savePollingAndTokenConfig(
        bearerToken: String,
        intervalMinutes: Int,
        autoPollEnabled: Boolean,
        customEndpointUrl: String
    ) {
        viewModelScope.launch {
            sessionPrefs.saveCapturedSession(
                bearerToken = bearerToken.ifBlank { null },
                cookieHeader = null
            )
            sessionPrefs.updatePollingConfig(
                intervalMinutes = intervalMinutes,
                autoPollEnabled = autoPollEnabled,
                customEndpointUrl = customEndpointUrl
            )
            controlState.value = controlState.value.copy(
                bannerFeedback = "Updated polling configuration (Every $intervalMinutes mins)"
            )
        }
    }

    fun markAllHistoryRead() {
        viewModelScope.launch {
            repository.markAllHistoryRead()
            controlState.value = controlState.value.copy(
                bannerFeedback = "Marked all status updates as read"
            )
        }
    }

    fun clearSessionAndData() {
        viewModelScope.launch {
            sessionPrefs.clearSession()
            repository.clearAllTrackedData()
            controlState.value = controlState.value.copy(
                bannerFeedback = "Signed out and cleared local SJP session cache"
            )
        }
    }

    fun buildCsvReport(
        jobs: List<AppliedJobEntity>,
        includeRemarks: Boolean,
        includeNotes: Boolean
    ): String {
        val sb = StringBuilder()
        val headers = mutableListOf(
            "Application ID",
            "Job Title",
            "Department",
            "Current Status",
            "Job Offered",
            "Interview Status",
            "Offer Record",
            "Applied Date"
        )
        if (includeRemarks) headers.add("Rejection Reason")
        if (includeNotes) headers.add("Personal Notes")
        sb.appendLine(headers.joinToString(","))

        for (job in jobs) {
            val row = mutableListOf(
                escapeCsv(job.applicationId),
                escapeCsv(job.jobTitle),
                escapeCsv(job.departmentName),
                escapeCsv(job.rawStatusString),
                escapeCsv(if (job.jobOffered) "YES" else "NO"),
                escapeCsv(job.jobInterviewStatus),
                escapeCsv(if (job.hasOfferRecord) "PRESENT" else "NONE"),
                escapeCsv(job.appliedDate)
            )
            if (includeRemarks) row.add(escapeCsv(job.reasonForRejected))
            if (includeNotes) row.add(escapeCsv(job.userNotes))
            sb.appendLine(row.joinToString(","))
        }
        return sb.toString()
    }

    fun buildExecutiveTextReport(
        jobs: List<AppliedJobEntity>,
        includeRemarks: Boolean,
        includeNotes: Boolean
    ): String {
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("   SJP PULSE — CANDIDATE APPLICATION STATUS REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Generated: ${fmt.format(Date())}")
        sb.appendLine("Endpoint: usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate")
        sb.appendLine("Total Applications: ${jobs.size}")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine()

        val grouped = jobs.groupBy { it.statusEnum }
        for (status in ApplicationStatus.entries.sortedByDescending { it.funnelOrder }) {
            val list = grouped[status].orEmpty()
            if (list.isEmpty()) continue
            sb.appendLine("▶ ${status.displayLabel.uppercase()} (${list.size})")
            for (job in list) {
                sb.appendLine("  • ${job.jobTitle}")
                sb.appendLine("    Department: ${job.departmentName}")
                sb.appendLine("    Application ID: ${job.applicationId} | Current Status: ${job.rawStatusString}")
                sb.appendLine("    Job Offered: ${if (job.jobOffered) "YES" else "NO"} | Interview: ${job.jobInterviewStatus} | Offer Record: ${if (job.hasOfferRecord) "PRESENT" else "NONE"}")
                if (includeRemarks && job.reasonForRejected.isNotBlank() && job.reasonForRejected != "—") {
                    sb.appendLine("    Rejection Reason: ${job.reasonForRejected}")
                }
                if (includeNotes && job.userNotes.isNotBlank()) {
                    sb.appendLine("    Personal Notes: ${job.userNotes}")
                }
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val sanitized = value.replace("\"", "\"\"").replace("\n", " ")
        return "\"$sanitized\""
    }
}
