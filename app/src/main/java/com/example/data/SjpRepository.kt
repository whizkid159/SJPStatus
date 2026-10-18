package com.example.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SyncResult(
    val success: Boolean,
    val totalApplications: Int,
    val changedCount: Int,
    val message: String
)

class SjpRepository(
    private val appContext: Context,
    private val dao: SjpDao,
    val sessionPrefs: SessionPreferencesRepository
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val jobsFlow: Flow<List<AppliedJobEntity>> = dao.observeAllJobs()
    val historyFlow: Flow<List<StatusHistoryEntity>> = dao.observeStatusHistory()
    val sessionFlow: Flow<SjpSessionState> = sessionPrefs.sessionFlow

    /**
     * Polls https://sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate
     * using captured WebView cookies and/or Bearer token.
     */
    suspend fun pollEndpointFromNetwork(): SyncResult = withContext(Dispatchers.IO) {
        val currentSession = sessionPrefs.sessionFlow.first()
        val endpoint = currentSession.customEndpointUrl.ifBlank {
            "https://sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate"
        }

        // Sync fresh cookies from Android WebView CookieManager if available
        val webViewCookies = try {
            CookieManager.getInstance().getCookie("https://sjp.gos.pk").orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val activeCookies = webViewCookies.ifBlank { currentSession.cookieHeader }
        if (webViewCookies.isNotBlank() && webViewCookies != currentSession.cookieHeader) {
            sessionPrefs.saveCapturedSession(
                bearerToken = null,
                cookieHeader = webViewCookies
            )
        }

        if (activeCookies.isBlank() && currentSession.bearerToken.isBlank()) {
            val msg = "No active SJP session token/cookie yet. Sign in via the SJP Portal tab or run a WebView fetch."
            sessionPrefs.recordPollResult(
                success = false,
                message = msg,
                intervalMinutes = currentSession.pollingIntervalMinutes
            )
            return@withContext SyncResult(
                success = false,
                totalApplications = dao.getAllJobsSnapshot().size,
                changedCount = 0,
                message = msg
            )
        }

        return@withContext try {
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json, text/plain, */*")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                .header("Referer", "https://sjp.gos.pk/")
                .header("X-Requested-With", "XMLHttpRequest")

            if (activeCookies.isNotBlank()) {
                requestBuilder.header("Cookie", activeCookies)
            }
            if (currentSession.bearerToken.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${currentSession.bearerToken}")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = "HTTP ${response.code}: Portal returned ${response.message.ifBlank { "error" }}. Try 'Fetch via Browser Session' in the SJP Portal tab."
                    sessionPrefs.recordPollResult(
                        success = false,
                        message = errMsg,
                        intervalMinutes = currentSession.pollingIntervalMinutes
                    )
                    return@use SyncResult(
                        success = false,
                        totalApplications = dao.getAllJobsSnapshot().size,
                        changedCount = 0,
                        message = errMsg
                    )
                }

                ingestRawApiPayload(
                    rawJson = bodyText,
                    sourceLabel = "Live SJP API",
                    isSandbox = false
                )
            }
        } catch (e: Exception) {
            val errMsg = "Network sync error: ${e.localizedMessage ?: "Unable to reach sjp.gos.pk"}"
            sessionPrefs.recordPollResult(
                success = false,
                message = errMsg,
                intervalMinutes = currentSession.pollingIntervalMinutes
            )
            SyncResult(
                success = false,
                totalApplications = dao.getAllJobsSnapshot().size,
                changedCount = 0,
                message = errMsg
            )
        }
    }

    /**
     * Ingests a raw JSON string from getAllAppliedJobsByCandidate (whether from OkHttp,
     * the embedded WebView JavaScript fetch() interceptor, or manual JSON import),
     * calculates status diffs, logs history, and dispatches push notifications.
     */
    suspend fun ingestRawApiPayload(
        rawJson: String,
        sourceLabel: String,
        isSandbox: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        val currentSession = sessionPrefs.sessionFlow.first()
        return@withContext try {
            val existingList = dao.getAllJobsSnapshot()
            val existingMap = existingList.associateBy { it.applicationId }

            val outcome = SjpPayloadParser.parseAppliedJobsJson(rawJson, existingMap)
            val newHistoryEntries = mutableListOf<StatusHistoryEntity>()
            val now = System.currentTimeMillis()

            for (newJob in outcome.jobs) {
                val previous = existingMap[newJob.applicationId]
                if (previous == null) {
                    // Record initial snapshot in timeline so user has a baseline audit trail
                    newHistoryEntries.add(
                        StatusHistoryEntity(
                            applicationId = newJob.applicationId,
                            jobTitle = newJob.jobTitle,
                            departmentName = newJob.departmentName,
                            bpsScale = newJob.bpsScale,
                            previousStatusCode = ApplicationStatus.APPLIED.code,
                            newStatusCode = newJob.normalizedStatusCode,
                            previousRawStatus = newJob.frontendMaskedStatus,
                            newRawStatus = newJob.rawStatusString,
                            remarksSnapshot = newJob.backendRemarks,
                            detectedAtTimestamp = now,
                            isUnread = false
                        )
                    )
                } else {
                    val statusDiff = previous.normalizedStatusCode != newJob.normalizedStatusCode ||
                        !previous.rawStatusString.equals(newJob.rawStatusString, ignoreCase = true)
                    val remarksDiff = previous.backendRemarks != newJob.backendRemarks

                    if (statusDiff || remarksDiff) {
                        newHistoryEntries.add(
                            StatusHistoryEntity(
                                applicationId = newJob.applicationId,
                                jobTitle = newJob.jobTitle,
                                departmentName = newJob.departmentName,
                                bpsScale = newJob.bpsScale,
                                previousStatusCode = previous.normalizedStatusCode,
                                newStatusCode = newJob.normalizedStatusCode,
                                previousRawStatus = previous.rawStatusString,
                                newRawStatus = newJob.rawStatusString,
                                remarksSnapshot = newJob.backendRemarks,
                                detectedAtTimestamp = now,
                                isUnread = true
                            )
                        )

                        SjpNotificationHelper.sendStatusChangeNotification(
                            context = appContext,
                            jobTitle = newJob.jobTitle,
                            departmentName = newJob.departmentName,
                            previousStatus = previous.statusEnum.displayLabel,
                            newStatus = newJob.statusEnum.displayLabel,
                            remarks = newJob.backendRemarks
                        )
                    }
                }
            }

            dao.upsertJobs(outcome.jobs)
            if (newHistoryEntries.isNotEmpty()) {
                dao.insertStatusHistoryList(newHistoryEntries)
            }

            if (!outcome.candidateName.isNullOrBlank() || !outcome.candidateCnic.isNullOrBlank()) {
                sessionPrefs.saveCapturedSession(
                    bearerToken = null,
                    cookieHeader = null,
                    candidateName = outcome.candidateName,
                    candidateCnic = outcome.candidateCnic
                )
            }

            val changedChangesCount = newHistoryEntries.count { it.isUnread }
            val summaryMsg = if (changedChangesCount > 0) {
                "$sourceLabel synced ${outcome.jobs.size} jobs • $changedChangesCount status update(s) detected!"
            } else {
                "$sourceLabel synced ${outcome.jobs.size} jobs • All statuses up to date"
            }

            sessionPrefs.recordPollResult(
                success = true,
                message = summaryMsg,
                intervalMinutes = currentSession.pollingIntervalMinutes,
                isSandbox = isSandbox
            )

            SyncResult(
                success = true,
                totalApplications = outcome.jobs.size,
                changedCount = changedChangesCount,
                message = summaryMsg
            )
        } catch (e: Exception) {
            val errMsg = "Failed to parse API payload: ${e.localizedMessage ?: "Invalid JSON format"}"
            sessionPrefs.recordPollResult(
                success = false,
                message = errMsg,
                intervalMinutes = currentSession.pollingIntervalMinutes
            )
            SyncResult(
                success = false,
                totalApplications = dao.getAllJobsSnapshot().size,
                changedCount = 0,
                message = errMsg
            )
        }
    }

    /**
     * Simulates a real-time backend status transition on one of the tracked jobs
     * so the user can verify push notifications, diff badges, and timeline entries.
     */
    suspend fun simulateBackendStatusTransition(): SyncResult = withContext(Dispatchers.IO) {
        var currentJobs = dao.getAllJobsSnapshot()
        if (currentJobs.isEmpty()) {
            ingestRawApiPayload(
                rawJson = SjpPayloadParser.createDiagnosticSampleJson(),
                sourceLabel = "Diagnostic Dataset",
                isSandbox = true
            )
            currentJobs = dao.getAllJobsSnapshot()
        }

        val target = currentJobs.firstOrNull {
            it.statusEnum == ApplicationStatus.TEMP_SHORTLISTED ||
                it.statusEnum == ApplicationStatus.UNDER_SCRUTINY ||
                it.statusEnum == ApplicationStatus.SHORTLISTED
        } ?: currentJobs.firstOrNull() ?: return@withContext SyncResult(false, 0, 0, "No jobs available")

        val nextStatus = when (target.statusEnum) {
            ApplicationStatus.UNDER_SCRUTINY -> ApplicationStatus.TEMP_SHORTLISTED
            ApplicationStatus.TEMP_SHORTLISTED -> ApplicationStatus.SHORTLISTED
            ApplicationStatus.SHORTLISTED -> ApplicationStatus.TEMP_OFFERED
            ApplicationStatus.TEMP_OFFERED -> ApplicationStatus.OFFERED
            else -> ApplicationStatus.SHORTLISTED
        }

        val nextRawString = nextStatus.displayLabel.lowercase()
        val updatedRemarks = when (nextStatus) {
            ApplicationStatus.TEMP_SHORTLISTED -> "Backend Update: Provisionally shortlisted by Scrutiny Committee; documents verified."
            ApplicationStatus.SHORTLISTED -> "Backend Update: Cleared for Departmental Interview panel; call letter enabled in backend."
            ApplicationStatus.TEMP_OFFERED -> "Backend Update: Selected on merit list; provisional offer generated pending verification."
            ApplicationStatus.OFFERED -> "Backend Update: Final Offer Letter approved by Appointing Authority."
            else -> target.backendRemarks
        }

        val updatedJsonObj = try {
            JSONObject(target.rawJsonPayload).apply {
                put("status", nextRawString)
                put("scrutinyRemarks", updatedRemarks)
                put("lastBackendSync", System.currentTimeMillis())
            }.toString(2)
        } catch (_: Exception) {
            target.rawJsonPayload
        }

        val now = System.currentTimeMillis()
        val updatedJob = target.copy(
            rawStatusString = nextRawString,
            normalizedStatusCode = nextStatus.code,
            backendRemarks = updatedRemarks,
            rawJsonPayload = updatedJsonObj,
            lastUpdatedTimestamp = now,
            hasUnreadChange = true
        )

        val historyEntry = StatusHistoryEntity(
            applicationId = target.applicationId,
            jobTitle = target.jobTitle,
            departmentName = target.departmentName,
            bpsScale = target.bpsScale,
            previousStatusCode = target.normalizedStatusCode,
            newStatusCode = nextStatus.code,
            previousRawStatus = target.rawStatusString,
            newRawStatus = nextRawString,
            remarksSnapshot = updatedRemarks,
            detectedAtTimestamp = now,
            isUnread = true
        )

        dao.upsertJob(updatedJob)
        dao.insertStatusHistory(historyEntry)

        SjpNotificationHelper.sendStatusChangeNotification(
            context = appContext,
            jobTitle = target.jobTitle,
            departmentName = target.departmentName,
            previousStatus = target.statusEnum.displayLabel,
            newStatus = nextStatus.displayLabel,
            remarks = updatedRemarks
        )

        val msg = "Status Changed: ${target.jobTitle} moved from ${target.statusEnum.displayLabel} → ${nextStatus.displayLabel}"
        val session = sessionPrefs.sessionFlow.first()
        sessionPrefs.recordPollResult(
            success = true,
            message = msg,
            intervalMinutes = session.pollingIntervalMinutes
        )

        SyncResult(
            success = true,
            totalApplications = currentJobs.size,
            changedCount = 1,
            message = msg
        )
    }

    suspend fun updateJobNotesAndInterview(
        applicationId: String,
        notes: String,
        interviewTimestamp: Long?
    ) {
        dao.updateNotesAndReminder(applicationId, notes, interviewTimestamp)
    }

    suspend fun markJobRead(applicationId: String) {
        dao.markJobChangeRead(applicationId)
    }

    suspend fun markAllHistoryRead() {
        dao.markAllHistoryRead()
        dao.markAllJobChangesRead()
    }

    suspend fun clearAllTrackedData() {
        dao.clearAllJobs()
        dao.clearAllHistory()
    }
}
