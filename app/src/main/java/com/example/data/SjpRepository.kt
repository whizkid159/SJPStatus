package com.example.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SyncResult(
    val success: Boolean,
    val totalApplications: Int,
    val changedCount: Int,
    val message: String,
    val loggedOut: Boolean = false
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
     * Android 16 (API 36) & Android 17 (API 36.1) Safe Cookie Reader:
     * Always accesses CookieManager.getInstance() on Dispatchers.Main inside runCatching
     * so Chromium WebViewFactory is never initialized on a background worker thread.
     */
    suspend fun getCombinedPortalCookies(fallbackCookieHeader: String = ""): String {
        return withContext(Dispatchers.Main) {
            runCatching {
                val cm = CookieManager.getInstance()
                val apiCookies = cm.getCookie("https://usr-svc.sjp.gos.pk").orEmpty().trim()
                val mainCookies = cm.getCookie("https://sjp.gos.pk").orEmpty().trim()
                val combined = listOf(apiCookies, mainCookies)
                    .filter { it.isNotBlank() }
                    .flatMap { it.split(";") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("; ")
                combined.ifBlank { fallbackCookieHeader }
            }.getOrDefault(fallbackCookieHeader)
        }
    }

    /**
     * Polls https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate
     * across all pages (1..totalPages) using the decrypted session token and cookies.
     */
    suspend fun pollEndpointFromNetwork(): SyncResult {
        val currentSession = sessionPrefs.sessionFlow.first()
        val activeCookies = getCombinedPortalCookies(currentSession.cookieHeader)
        if (activeCookies.isNotBlank() && activeCookies != currentSession.cookieHeader) {
            sessionPrefs.saveCapturedSession(
                bearerToken = null,
                cookieHeader = activeCookies
            )
        }

        return withContext(Dispatchers.IO) {
            val baseEndpoint = currentSession.customEndpointUrl
                .ifBlank { DEFAULT_SJP_API_URL }
                .substringBefore("?")

            if (activeCookies.isBlank() && currentSession.bearerToken.isBlank()) {
                clearAllTrackedData()
                val msg = "No active SJP session. Please log in on the SJP Login tab first."
                sessionPrefs.recordPollResult(
                    success = false,
                    message = msg,
                    intervalMinutes = currentSession.pollingIntervalMinutes
                )
                return@withContext SyncResult(
                    success = false,
                    totalApplications = 0,
                    changedCount = 0,
                    message = msg
                )
            }

            try {
                val combinedApplications = JSONArray()
                var page = 1
                var totalPages = 1
                var totalReported = 0

                while (page <= totalPages && page <= 50) {
                    val pageUrl = "$baseEndpoint?page=$page&limit=10&keyword="
                    val requestBuilder = Request.Builder()
                        .url(pageUrl)
                        .get()
                        .header("Accept", "application/json")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        )
                        .header("Origin", "https://sjp.gos.pk")
                        .header("Referer", "https://sjp.gos.pk/")

                    if (activeCookies.isNotBlank()) {
                        requestBuilder.header("Cookie", activeCookies)
                    }
                    if (currentSession.bearerToken.isNotBlank()) {
                        requestBuilder.header("Authorization", "Bearer ${currentSession.bearerToken}")
                    }

                    okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                        val bodyText = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            if (response.code == 401 || response.code == 403) {
                                // User logged out or session invalidated: do not retain any application data
                                clearAllTrackedData()
                                sessionPrefs.clearSession()
                                val logoutMsg = "Logged out from SJP. All local application data has been cleared."
                                return@withContext SyncResult(
                                    success = false,
                                    totalApplications = 0,
                                    changedCount = 0,
                                    message = logoutMsg,
                                    loggedOut = true
                                )
                            }

                            val errMsg = "HTTP ${response.code}: ${bodyText.take(160).ifBlank { "Session expired — please open SJP Login tab" }}"
                            sessionPrefs.recordPollResult(
                                success = false,
                                message = errMsg,
                                intervalMinutes = currentSession.pollingIntervalMinutes
                            )
                            return@withContext SyncResult(
                                success = false,
                                totalApplications = dao.getAllJobsSnapshot().size,
                                changedCount = 0,
                                message = errMsg
                            )
                        }

                        val rootObj = JSONObject(bodyText)
                        val dataObj = rootObj.optJSONObject("data") ?: rootObj
                        val paginationObj = dataObj.optJSONObject("pagination")
                        if (paginationObj != null) {
                            totalPages = paginationObj.optInt("totalPages", 1).coerceAtLeast(1)
                            totalReported = paginationObj.optInt("total", totalReported)
                        }
                        val apps = dataObj.optJSONArray("applications") ?: JSONArray()
                        for (i in 0 until apps.length()) {
                            combinedApplications.put(apps.get(i))
                        }
                    }
                    page++
                }

                val mergedPayload = JSONObject().apply {
                    put("data", JSONObject().apply {
                        put("applications", combinedApplications)
                        put("pagination", JSONObject().apply {
                            put("total", if (totalReported > 0) totalReported else combinedApplications.length())
                            put("totalPages", totalPages)
                            put("page", 1)
                        })
                    })
                }.toString()

                ingestRawApiPayload(
                    rawJson = mergedPayload,
                    sourceLabel = "SJP API ($totalPages page${if (totalPages > 1) "s" else ""})"
                )
            } catch (e: Exception) {
                val errMsg = "Could not fetch SJP data: ${e.localizedMessage ?: "Network error"}"
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
    }

    suspend fun ingestRawApiPayload(
        rawJson: String,
        sourceLabel: String
    ): SyncResult = withContext(Dispatchers.IO) {
        val currentSession = sessionPrefs.sessionFlow.first()
        return@withContext try {
            val existingList = dao.getAllJobsSnapshot()
            val existingMap = existingList.associateBy { it.applicationId }

            val outcome = SjpPayloadParser.parseAppliedJobsJson(rawJson, existingMap)
            val newHistoryEntries = mutableListOf<StatusHistoryEntity>()
            val now = System.currentTimeMillis()
            var detectedLiveChanges = 0

            for (newJob in outcome.jobs) {
                val previous = existingMap[newJob.applicationId]

                val backendEntries = outcome.backendHistoryMap[newJob.applicationId].orEmpty()
                    .sortedBy { it.changedAtMillis }

                var priorStatus = "APPLIED"
                for (entry in backendEntries) {
                    val existsCount = dao.countMatchingHistoryEntry(
                        appId = newJob.applicationId,
                        status = entry.status,
                        changedAt = entry.changedAt
                    )
                    if (existsCount == 0) {
                        newHistoryEntries.add(
                            StatusHistoryEntity(
                                applicationId = newJob.applicationId,
                                jobTitle = newJob.jobTitle,
                                departmentName = newJob.departmentName,
                                bpsScale = newJob.bpsScale,
                                previousStatusCode = ApplicationStatus.fromRawStatus(priorStatus).code,
                                newStatusCode = ApplicationStatus.fromRawStatus(entry.status).code,
                                previousRawStatus = priorStatus,
                                newRawStatus = entry.status,
                                remarksSnapshot = buildJobMetadataSummary(newJob),
                                changedAtFormatted = entry.changedAt,
                                detectedAtTimestamp = if (entry.changedAtMillis > 0L) entry.changedAtMillis else now,
                                isUnread = false
                            )
                        )
                    }
                    priorStatus = entry.status
                }

                if (previous != null) {
                    val statusDiff = !previous.rawStatusString.equals(newJob.rawStatusString, ignoreCase = true) ||
                        previous.jobOffered != newJob.jobOffered ||
                        previous.jobInterviewStatus != newJob.jobInterviewStatus ||
                        previous.hasOfferRecord != newJob.hasOfferRecord

                    if (statusDiff) {
                        detectedLiveChanges++
                        val changeSummary = buildJobMetadataSummary(newJob)
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
                                remarksSnapshot = changeSummary,
                                changedAtFormatted = "Live Poll Update",
                                detectedAtTimestamp = now,
                                isUnread = true
                            )
                        )

                        SjpNotificationHelper.sendStatusChangeNotification(
                            context = appContext,
                            jobTitle = newJob.jobTitle,
                            departmentName = newJob.departmentName,
                            previousStatus = previous.rawStatusString,
                            newStatus = newJob.rawStatusString,
                            remarks = changeSummary
                        )
                    }
                }
            }

            dao.clearAllJobs()
            dao.upsertJobs(outcome.jobs)
            if (newHistoryEntries.isNotEmpty()) {
                dao.insertStatusHistoryList(newHistoryEntries)
            }

            val summaryMsg = if (detectedLiveChanges > 0) {
                "$sourceLabel: Loaded ${outcome.jobs.size} applications ($detectedLiveChanges status change(s) detected!)"
            } else {
                "$sourceLabel: Loaded ${outcome.jobs.size} applications across ${outcome.totalPages} page(s)"
            }

            sessionPrefs.recordPollResult(
                success = true,
                message = summaryMsg,
                intervalMinutes = currentSession.pollingIntervalMinutes,
                totalAppsOnServer = outcome.totalApplications,
                totalPagesOnServer = outcome.totalPages
            )

            SyncResult(
                success = true,
                totalApplications = outcome.jobs.size,
                changedCount = detectedLiveChanges,
                message = summaryMsg
            )
        } catch (e: Exception) {
            val errMsg = "Could not parse SJP response: ${e.localizedMessage ?: "Invalid JSON"}"
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

    private fun buildJobMetadataSummary(job: AppliedJobEntity): String {
        val parts = mutableListOf<String>()
        parts.add("Job offered: ${if (job.jobOffered) "YES" else "NO"}")
        parts.add("Interview: ${job.jobInterviewStatus}")
        parts.add("Offer record: ${if (job.hasOfferRecord) "PRESENT" else "NONE"}")
        if (job.reasonForRejected.isNotBlank() && job.reasonForRejected != "—") {
            parts.add("Rejection reason: ${job.reasonForRejected}")
        }
        return parts.joinToString(" • ")
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
