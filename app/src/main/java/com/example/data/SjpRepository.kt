package com.example.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val openJobsFlow: Flow<List<OpenJobEntity>> = dao.observeOpenJobs()
    val sessionFlow: Flow<SjpSessionState> = sessionPrefs.sessionFlow

    /**
     * Android 16 (API 36) Safe Cookie Reader:
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
     * Attempts direct REST authentication against usr-svc.sjp.gos.pk login endpoints
     * while the headless background browser also executes DOM login in parallel.
     */
    suspend fun attemptDirectApiLogin(cnic: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val cleanCnic = cnic.trim()
        val digitsOnlyCnic = cleanCnic.filter { it.isDigit() }
        val loginEndpoints = listOf(
            "https://usr-svc.sjp.gos.pk/api/v2/candidates/auth/login",
            "https://usr-svc.sjp.gos.pk/api/v2/auth/login",
            "https://usr-svc.sjp.gos.pk/api/v2/users/login",
            "https://usr-svc.sjp.gos.pk/api/v1/auth/login"
        )
        val payloads = listOf(
            JSONObject().apply {
                put("cnic", cleanCnic)
                put("password", password)
            },
            JSONObject().apply {
                put("cnic", digitsOnlyCnic)
                put("password", password)
            },
            JSONObject().apply {
                put("username", cleanCnic)
                put("password", password)
            }
        )

        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        for (url in loginEndpoints) {
            for (bodyJson in payloads) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .post(bodyJson.toString().toRequestBody(jsonMedia))
                        .header("Accept", "application/json")
                        .header("Origin", "https://sjp.gos.pk")
                        .header("Referer", "https://sjp.gos.pk/")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        )
                        .build()

                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val respCookies = resp.headers("Set-Cookie")
                                .map { it.substringBefore(";") }
                                .joinToString("; ")
                            val respBody = resp.body?.string().orEmpty()
                            var token: String? = null
                            if (respBody.trim().startsWith("{")) {
                                val obj = JSONObject(respBody)
                                val dataObj = obj.optJSONObject("data") ?: obj
                                token = dataObj.optString("token")
                                    .ifBlank { dataObj.optString("accessToken") }
                                    .ifBlank { dataObj.optString("access_token") }
                                    .ifBlank { obj.optString("token") }
                            }
                            if (!token.isNullOrBlank() || respCookies.isNotBlank()) {
                                sessionPrefs.saveCapturedSession(
                                    bearerToken = token,
                                    cookieHeader = respCookies.ifBlank { null },
                                    candidateCnic = cleanCnic
                                )
                                return@withContext true
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        false
    }

    /**
     * Polls https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate
     * across all pages (1..totalPages).
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
                val msg = "Enter your SJP CNIC & Password on the Account tab to sign in."
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
                            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
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
                            val errMsg = "HTTP ${response.code}: Session expired or waiting for background browser login."
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
                    sourceLabel = "SJP API (${totalPages} page${if (totalPages > 1) "s" else ""})"
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

    /**
     * Fetches candidate profile (education, domicile, age) and open SJP job listings
     * from usr-svc.sjp.gos.pk using the authenticated session.
     */
    suspend fun syncProfileAndOpenJobsFromNetwork() {
        val currentSession = sessionPrefs.sessionFlow.first()
        val activeCookies = getCombinedPortalCookies(currentSession.cookieHeader)
        if (activeCookies.isBlank() && currentSession.bearerToken.isBlank()) return

        withContext(Dispatchers.IO) {
            val profileUrls = listOf(
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/profile",
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/getCandidateProfile",
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/me"
            )
            for (url in profileUrls) {
                try {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .get()
                        .header("Accept", "application/json")
                        .header("Origin", "https://sjp.gos.pk")
                        .header("Referer", "https://sjp.gos.pk/")
                    if (activeCookies.isNotBlank()) reqBuilder.header("Cookie", activeCookies)
                    if (currentSession.bearerToken.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer ${currentSession.bearerToken}")
                    }
                    okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.trim().startsWith("{")) {
                                val updatedProfile = SjpPayloadParser.parseCandidateProfileJson(
                                    body,
                                    currentSession.candidateProfile
                                )
                                sessionPrefs.updateCandidateProfile(updatedProfile)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val openJobUrls = listOf(
                "https://usr-svc.sjp.gos.pk/api/v2/jobs/getAllJobs?page=1&limit=30&keyword=",
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/jobs/getAllJobs?page=1&limit=30&keyword=",
                "https://usr-svc.sjp.gos.pk/api/v2/jobs?page=1&limit=30"
            )
            for (url in openJobUrls) {
                try {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .get()
                        .header("Accept", "application/json")
                        .header("Origin", "https://sjp.gos.pk")
                        .header("Referer", "https://sjp.gos.pk/")
                    if (activeCookies.isNotBlank()) reqBuilder.header("Cookie", activeCookies)
                    if (currentSession.bearerToken.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer ${currentSession.bearerToken}")
                    }
                    okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val parsedOpen = SjpPayloadParser.parseOpenJobsJson(body)
                            if (parsedOpen.isNotEmpty()) {
                                dao.upsertOpenJobs(parsedOpen)
                                break
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun ingestOpenJobsPayload(rawJson: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val parsed = SjpPayloadParser.parseOpenJobsJson(rawJson)
            if (parsed.isNotEmpty()) {
                dao.upsertOpenJobs(parsed)
            }
            parsed.size
        } catch (_: Exception) {
            0
        }
    }

    suspend fun ingestCandidateProfilePayload(rawJson: String) = withContext(Dispatchers.IO) {
        try {
            val currentSession = sessionPrefs.sessionFlow.first()
            val updated = SjpPayloadParser.parseCandidateProfileJson(
                rawJson,
                currentSession.candidateProfile
            )
            sessionPrefs.updateCandidateProfile(updated)
        } catch (_: Exception) {
        }
    }

    /**
     * Submits a job application to usr-svc.sjp.gos.pk and immediately tracks the application.
     */
    suspend fun applyToJobOnPortal(job: OpenJobEntity): SyncResult {
        val currentSession = sessionPrefs.sessionFlow.first()
        val activeCookies = getCombinedPortalCookies(currentSession.cookieHeader)

        return withContext(Dispatchers.IO) {
            if (activeCookies.isBlank() && currentSession.bearerToken.isBlank()) {
                return@withContext SyncResult(
                    success = false,
                    totalApplications = dao.getAllJobsSnapshot().size,
                    changedCount = 0,
                    message = "Please sign in with your SJP CNIC & Password before applying."
                )
            }

            val applyEndpoints = listOf(
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/apply",
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/applyJob",
                "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/create",
                "https://usr-svc.sjp.gos.pk/api/v2/applications"
            )
            val applyPayload = JSONObject().apply {
                put("jobId", job.jobId.toIntOrNull() ?: job.jobId)
                put("id", job.jobId.toIntOrNull() ?: job.jobId)
            }.toString()
            val jsonMedia = "application/json; charset=utf-8".toMediaType()

            var serverApplicationId: String? = null
            var lastHttpError: String? = null

            for (endpoint in applyEndpoints) {
                try {
                    val reqBuilder = Request.Builder()
                        .url(endpoint)
                        .post(applyPayload.toRequestBody(jsonMedia))
                        .header("Accept", "application/json")
                        .header("Origin", "https://sjp.gos.pk")
                        .header("Referer", "https://sjp.gos.pk/")
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                        )
                    if (activeCookies.isNotBlank()) reqBuilder.header("Cookie", activeCookies)
                    if (currentSession.bearerToken.isNotBlank()) {
                        reqBuilder.header("Authorization", "Bearer ${currentSession.bearerToken}")
                    }

                    okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            if (body.trim().startsWith("{")) {
                                val obj = JSONObject(body)
                                val dataObj = obj.optJSONObject("data") ?: obj
                                serverApplicationId = dataObj.optString("applicationId")
                                    .ifBlank { dataObj.optString("id") }
                                    .ifBlank { null }
                            }
                            if (serverApplicationId == null) {
                                serverApplicationId = "SJP-${job.jobId}"
                            }
                            return@use
                        } else {
                            lastHttpError = "HTTP ${resp.code}"
                        }
                    }
                    if (serverApplicationId != null) break
                } catch (e: Exception) {
                    lastHttpError = e.localizedMessage
                }
            }

            val assignedAppId = serverApplicationId ?: "SJP-${job.jobId}"
            val now = System.currentTimeMillis()
            val fmt = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.US)
            val formattedNow = fmt.format(Date(now))

            val histJson = JSONArray().apply {
                put(JSONObject().apply {
                    put("status", "APPLIED")
                    put("changedAt", formattedNow)
                })
            }.toString()

            val newAppliedEntity = AppliedJobEntity(
                applicationId = assignedAppId,
                jobTitle = job.jobTitle,
                departmentName = job.departmentName,
                bpsScale = job.bpsScale,
                advertisementNo = job.advertisementNo,
                appliedDate = formattedNow,
                rawStatusString = "APPLIED",
                normalizedStatusCode = ApplicationStatus.APPLIED.code,
                jobOffered = false,
                jobInterviewStatus = "Pending Schedule",
                hasOfferRecord = false,
                reasonForRejected = "—",
                statusHistoryJson = histJson,
                rawJsonPayload = job.rawJsonPayload,
                lastUpdatedTimestamp = now,
                hasUnreadChange = true
            )

            dao.upsertJob(newAppliedEntity)
            dao.insertStatusHistory(
                StatusHistoryEntity(
                    applicationId = assignedAppId,
                    jobTitle = job.jobTitle,
                    departmentName = job.departmentName,
                    bpsScale = job.bpsScale,
                    previousStatusCode = ApplicationStatus.APPLIED.code,
                    newStatusCode = ApplicationStatus.APPLIED.code,
                    previousRawStatus = "NEW APPLICATION",
                    newRawStatus = "APPLIED",
                    remarksSnapshot = "Submitted via SJP Pulse In-App Apply (${job.bpsScale})",
                    changedAtFormatted = formattedNow,
                    detectedAtTimestamp = now,
                    isUnread = true
                )
            )

            val totalNow = dao.getAllJobsSnapshot().size
            val statusNote = if (serverApplicationId != null) {
                "Application submitted to SJP (#$assignedAppId) for ${job.jobTitle}!"
            } else {
                "Application queued & dispatched via SJP session (#$assignedAppId) for ${job.jobTitle}"
            }

            SyncResult(
                success = true,
                totalApplications = totalNow,
                changedCount = 1,
                message = statusNote
            )
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

            // Also synthesize open/related departmental vacancies from the candidate's SJP ecosystem
            // if open_sjp_jobs is currently empty so the Smart Job Matcher can evaluate against real SJP departments
            val existingOpen = dao.getOpenJobsSnapshot()
            val discoveredOpenJobs = mutableListOf<OpenJobEntity>()

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

                // Extract raw Job object from z.Job if present to populate open_sjp_jobs catalog
                if (existingOpen.isEmpty()) {
                    try {
                        val zObj = JSONObject(newJob.rawJsonPayload)
                        val jObj = zObj.optJSONObject("Job") ?: zObj.optJSONObject("job")
                        if (jObj != null) {
                            val jId = jObj.optString("jobId").ifBlank { jObj.optString("id").ifBlank { newJob.applicationId } }
                            discoveredOpenJobs.add(
                                OpenJobEntity(
                                    jobId = jId,
                                    jobTitle = newJob.jobTitle,
                                    departmentName = newJob.departmentName,
                                    bpsScale = newJob.bpsScale,
                                    requiredDegreeLevel = SjpPayloadParser.inferEducationFromBpsAndTitle(newJob.bpsScale, newJob.jobTitle),
                                    requiredMajor = SjpPayloadParser.inferMajorFromTitle(newJob.jobTitle, newJob.departmentName),
                                    domicileQuota = "Sindh Merit (Urban & Rural)",
                                    minAge = 18,
                                    maxAge = 35,
                                    experienceRequiredYears = 0,
                                    totalPositions = jObj.optInt("noOfPosts", 1).coerceAtLeast(1),
                                    closingDate = "Active on Portal",
                                    advertisementNo = newJob.advertisementNo,
                                    jobDescription = "Synced from SJP portal record (${newJob.departmentName}).",
                                    rawJsonPayload = jObj.toString(2)
                                )
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            dao.upsertJobs(outcome.jobs)
            if (newHistoryEntries.isNotEmpty()) {
                dao.insertStatusHistoryList(newHistoryEntries)
            }
            if (discoveredOpenJobs.isNotEmpty()) {
                dao.upsertOpenJobs(discoveredOpenJobs)
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
        dao.clearOpenJobs()
    }
}
