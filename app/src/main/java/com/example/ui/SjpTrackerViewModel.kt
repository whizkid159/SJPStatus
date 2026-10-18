package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApplicationStatus
import com.example.data.AppliedJobEntity
import com.example.data.SessionPreferencesRepository
import com.example.data.SjpDatabase
import com.example.data.SjpPayloadParser
import com.example.data.SjpRepository
import com.example.data.SjpSessionState
import com.example.data.StatusHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val sessionState: SjpSessionState = SjpSessionState(),
    val filterState: SjpFilterState = SjpFilterState(),
    val availableDepartments: List<String> = emptyList(),
    val isSyncing: Boolean = false,
    val bannerFeedback: String? = null,
    val selectedJobForDetail: AppliedJobEntity? = null,
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
                it.statusEnum == ApplicationStatus.TEMP_OFFERED
        }
    val rejectedTotalCount: Int
        get() = allJobs.count { it.statusEnum == ApplicationStatus.REJECTED }
    val unreadHistoryCount: Int
        get() = historyList.count { it.isUnread }
}

private data class InternalControlState(
    val filterState: SjpFilterState = SjpFilterState(),
    val isSyncing: Boolean = false,
    val bannerFeedback: String? = null,
    val selectedJobId: String? = null,
    val initialDetailTab: Int = 0
)

class SjpTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SjpDatabase.getInstance(application)
    private val sessionPrefs = SessionPreferencesRepository(application)
    val repository = SjpRepository(application, database.sjpDao(), sessionPrefs)

    private val controlState = MutableStateFlow(InternalControlState())

    val uiState: StateFlow<SjpUiState> = combine(
        repository.jobsFlow,
        repository.historyFlow,
        repository.sessionFlow,
        controlState
    ) { jobs, history, session, ctrl ->
        val f = ctrl.filterState
        val filtered = jobs.filter { job ->
            val matchesQuery = f.searchQuery.isBlank() ||
                job.jobTitle.contains(f.searchQuery, ignoreCase = true) ||
                job.departmentName.contains(f.searchQuery, ignoreCase = true) ||
                job.bpsScale.contains(f.searchQuery, ignoreCase = true) ||
                job.rawStatusString.contains(f.searchQuery, ignoreCase = true) ||
                job.applicationId.contains(f.searchQuery, ignoreCase = true)

            val matchesStatus = f.selectedStatus == null || job.statusEnum == f.selectedStatus
            val matchesDept = f.selectedDepartment == null || job.departmentName == f.selectedDepartment
            val matchesReminder = !f.onlyWithReminders || (job.interviewTimestamp != null && job.interviewTimestamp > 0L)
            val matchesUnread = !f.onlyUnreadChanges || job.hasUnreadChange

            matchesQuery && matchesStatus && matchesDept && matchesReminder && matchesUnread
        }

        val departments = jobs.map { it.departmentName }.distinct().sorted()
        val selectedJob = ctrl.selectedJobId?.let { id -> jobs.firstOrNull { it.applicationId == id } }

        SjpUiState(
            allJobs = jobs,
            filteredJobs = filtered,
            historyList = history,
            sessionState = session,
            filterState = f,
            availableDepartments = departments,
            isSyncing = ctrl.isSyncing,
            bannerFeedback = ctrl.bannerFeedback,
            selectedJobForDetail = selectedJob,
            initialDetailTab = ctrl.initialDetailTab
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SjpUiState()
    )

    init {
        // Seed initial diagnostic SJP dataset on first launch so dashboard, raw JSON inspector,
        // and analytics charts are immediately populated and interactive.
        viewModelScope.launch {
            val currentJobs = database.sjpDao().getAllJobsSnapshot()
            if (currentJobs.isEmpty()) {
                repository.ingestRawApiPayload(
                    rawJson = SjpPayloadParser.createDiagnosticSampleJson(),
                    sourceLabel = "SJP Sample Telemetry",
                    isSandbox = true
                )
            }
        }

        // Start 30-minute background polling loop while app process is alive
        viewModelScope.launch {
            while (isActive) {
                val session = sessionPrefs.sessionFlow.first()
                val intervalMs = (session.pollingIntervalMinutes.coerceAtLeast(5)) * 60_000L
                delay(intervalMs)
                val latestSession = sessionPrefs.sessionFlow.first()
                if (latestSession.autoPollEnabled && latestSession.hasActiveSession) {
                    controlState.value = controlState.value.copy(isSyncing = true)
                    val res = repository.pollEndpointFromNetwork()
                    controlState.value = controlState.value.copy(
                        isSyncing = false,
                        bannerFeedback = res.message
                    )
                }
            }
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
            controlState.value = controlState.value.copy(isSyncing = true)
            val result = repository.pollEndpointFromNetwork()
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

    fun onWebViewPayloadCaptured(rawJson: String, sourceLabel: String = "SJP Portal Browser Fetch") {
        viewModelScope.launch {
            controlState.value = controlState.value.copy(isSyncing = true)
            val result = repository.ingestRawApiPayload(
                rawJson = rawJson,
                sourceLabel = sourceLabel,
                isSandbox = false
            )
            controlState.value = controlState.value.copy(
                isSyncing = false,
                bannerFeedback = result.message
            )
        }
    }

    fun loadDiagnosticSamplePayload() {
        viewModelScope.launch {
            controlState.value = controlState.value.copy(isSyncing = true)
            val result = repository.ingestRawApiPayload(
                rawJson = SjpPayloadParser.createDiagnosticSampleJson(),
                sourceLabel = "Diagnostic SJP Dataset",
                isSandbox = true
            )
            controlState.value = controlState.value.copy(
                isSyncing = false,
                bannerFeedback = result.message
            )
        }
    }

    fun simulateStatusUpdateAlert() {
        viewModelScope.launch {
            controlState.value = controlState.value.copy(isSyncing = true)
            val result = repository.simulateBackendStatusTransition()
            controlState.value = controlState.value.copy(
                isSyncing = false,
                bannerFeedback = result.message
            )
        }
    }

    fun saveJobNotesAndInterview(
        applicationId: String,
        notes: String,
        interviewTimestamp: Long?
    ) {
        viewModelScope.launch {
            repository.updateJobNotesAndInterview(applicationId, notes, interviewTimestamp)
            controlState.value = controlState.value.copy(
                bannerFeedback = "Saved personal notes & reminder for $applicationId"
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
                bannerFeedback = "Marked all status diffs as read"
            )
        }
    }

    fun clearSessionCredentials() {
        viewModelScope.launch {
            sessionPrefs.clearSession()
            controlState.value = controlState.value.copy(
                bannerFeedback = "Cleared saved SJP session credentials"
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
            "BPS Scale",
            "Advertisement No",
            "Applied Date",
            "Portal UI Status",
            "Hidden Backend Status",
            "Merit Score"
        )
        if (includeRemarks) headers.add("Backend Scrutiny Remarks")
        if (includeNotes) headers.add("Candidate Notes")
        sb.appendLine(headers.joinToString(","))

        for (job in jobs) {
            val row = mutableListOf(
                escapeCsv(job.applicationId),
                escapeCsv(job.jobTitle),
                escapeCsv(job.departmentName),
                escapeCsv(job.bpsScale),
                escapeCsv(job.advertisementNo),
                escapeCsv(job.appliedDate),
                escapeCsv(job.frontendMaskedStatus),
                escapeCsv(job.statusEnum.displayLabel),
                escapeCsv(job.meritScore)
            )
            if (includeRemarks) row.add(escapeCsv(job.backendRemarks))
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
        sb.appendLine("Source: sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate")
        sb.appendLine("Total Filtered Applications: ${jobs.size}")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine()

        val grouped = jobs.groupBy { it.statusEnum }
        for (status in ApplicationStatus.entries.sortedByDescending { it.funnelOrder }) {
            val list = grouped[status].orEmpty()
            if (list.isEmpty()) continue
            sb.appendLine("▶ ${status.displayLabel.uppercase()} (${list.size})")
            for (job in list) {
                sb.appendLine("  • [${job.bpsScale}] ${job.jobTitle}")
                sb.appendLine("    Dept: ${job.departmentName} | ID: ${job.applicationId}")
                sb.appendLine("    Portal UI: ${job.frontendMaskedStatus} -> Backend API: ${job.rawStatusString} (Score: ${job.meritScore})")
                if (includeRemarks && job.backendRemarks.isNotBlank()) {
                    sb.appendLine("    Remarks: ${job.backendRemarks}")
                }
                if (includeNotes && job.userNotes.isNotBlank()) {
                    sb.appendLine("    My Notes: ${job.userNotes}")
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
