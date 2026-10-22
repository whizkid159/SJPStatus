package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SjpPayloadParser {

    data class ParseOutcome(
        val jobs: List<AppliedJobEntity>,
        val backendHistoryMap: Map<String, List<SjpBackendStatusEntry>>,
        val totalApplications: Int,
        val totalPages: Int,
        val currentPage: Int
    )

    fun parseAppliedJobsJson(
        rawJsonText: String,
        existingJobsMap: Map<String, AppliedJobEntity> = emptyMap()
    ): ParseOutcome {
        val trimmed = rawJsonText.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Empty response received from SJP API")
        }

        var totalPages = 1
        var currentPage = 1
        var totalFromPagination: Int? = null

        val appsArray: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val rootObj = JSONObject(trimmed)
                val dataObj = rootObj.optJSONObject("data") ?: rootObj
                val paginationObj = dataObj.optJSONObject("pagination")
                    ?: rootObj.optJSONObject("pagination")

                if (paginationObj != null) {
                    totalPages = paginationObj.optInt("totalPages", 1).coerceAtLeast(1)
                    currentPage = paginationObj.optInt("page", paginationObj.optInt("currentPage", 1)).coerceAtLeast(1)
                    if (paginationObj.has("total") && !paginationObj.isNull("total")) {
                        totalFromPagination = paginationObj.optInt("total")
                    }
                }

                dataObj.optJSONArray("applications")
                    ?: rootObj.optJSONArray("applications")
                    ?: rootObj.optJSONArray("data")
                    ?: throw IllegalArgumentException(
                        "Response JSON does not contain data.applications (keys: ${
                            rootObj.keys().asSequence().joinToString()
                        })"
                    )
            }
            else -> throw IllegalArgumentException("Response is not valid JSON: ${trimmed.take(80)}")
        }

        val parsedJobs = mutableListOf<AppliedJobEntity>()
        val backendHistoryMap = mutableMapOf<String, List<SjpBackendStatusEntry>>()
        val now = System.currentTimeMillis()

        for (i in 0 until appsArray.length()) {
            val z = appsArray.optJSONObject(i) ?: continue
            val j = z.optJSONObject("Job")
                ?: z.optJSONObject("job")
                ?: JSONObject()
            val deptObj = j.optJSONObject("Department")
                ?: j.optJSONObject("department")
                ?: z.optJSONObject("Department")
                ?: z.optJSONObject("department")

            val appId = extractString(z, "applicationId", "id", "appId")
                ?: "SJP-${1000 + i}"

            val jobTitle = extractString(j, "jobTitle", "title", "postTitle", "name")
                ?: extractString(z, "jobTitle", "title")
                ?: "Unknown job"

            val departmentName = extractString(deptObj, "departmentName", "name", "title")
                ?: extractString(j, "departmentName")
                ?: extractString(z, "departmentName")
                ?: "Unknown department"

            val rawBps = extractString(j, "bps", "bpsScale", "grade", "scale", "payScale")
                ?: extractString(z, "bps", "bpsScale", "grade")
                ?: ""
            val bpsFormatted = when {
                rawBps.isBlank() -> "SJP Post"
                rawBps.uppercase().startsWith("BPS") -> rawBps.uppercase()
                else -> "BPS-$rawBps"
            }

            val advertisementNo = extractString(j, "advertisementNo", "advNo", "jobCode", "code")
                ?: extractString(z, "advertisementNo", "advNo")
                ?: ""

            val rawApplicationStatus = extractString(
                z,
                "applicationStatus", "status", "currentStatus"
            ) ?: "APPLIED"

            val jobOffered = z.optBoolean("jobOffered", false)
            val jobInterviewStatus = extractString(j, "jobInterviewStatus", "interviewStatus")
                ?: extractString(z, "jobInterviewStatus", "interviewStatus")
                ?: "—"

            val hasOfferRecord = z.has("Offer") && !z.isNull("Offer") &&
                z.opt("Offer").toString().isNotBlank() &&
                z.opt("Offer").toString() != "null" &&
                z.opt("Offer").toString() != "false"

            val reasonForRejected = extractString(
                z,
                "reasonForRejected", "rejectionReason", "remarks", "scrutinyRemarks"
            ) ?: "—"

            val histArray = z.optJSONArray("ApplicationStatuses")
                ?: z.optJSONArray("applicationStatuses")
                ?: JSONArray()

            val historyEntries = mutableListOf<SjpBackendStatusEntry>()
            for (hIdx in 0 until histArray.length()) {
                val x = histArray.optJSONObject(hIdx) ?: continue
                val st = extractString(x, "status", "applicationStatus") ?: continue
                val changedAtRaw = extractString(x, "changedAt", "createdAt", "updatedAt") ?: ""
                val changedMillis = parseIsoTimestamp(changedAtRaw) ?: (now - (histArray.length() - hIdx) * 1000L)
                historyEntries.add(
                    SjpBackendStatusEntry(
                        status = st,
                        changedAt = formatDisplayTimestamp(changedAtRaw, changedMillis),
                        changedAtMillis = changedMillis
                    )
                )
            }
            backendHistoryMap[appId] = historyEntries

            val appliedDate = historyEntries.minByOrNull { it.changedAtMillis }?.changedAt
                ?: extractString(z, "createdAt", "appliedDate", "appliedAt")?.let {
                    formatDisplayTimestamp(it, parseIsoTimestamp(it) ?: now)
                }
                ?: "—"

            val normalizedStatus = ApplicationStatus.fromRawStatus(rawApplicationStatus)
            val existing = existingJobsMap[appId]
            val statusChanged = existing != null &&
                (!existing.rawStatusString.equals(rawApplicationStatus, ignoreCase = true) ||
                    existing.jobOffered != jobOffered ||
                    existing.jobInterviewStatus != jobInterviewStatus ||
                    existing.hasOfferRecord != hasOfferRecord ||
                    existing.reasonForRejected != reasonForRejected)

            parsedJobs.add(
                AppliedJobEntity(
                    applicationId = appId,
                    jobTitle = jobTitle,
                    departmentName = departmentName,
                    bpsScale = bpsFormatted,
                    advertisementNo = advertisementNo,
                    appliedDate = appliedDate,
                    rawStatusString = rawApplicationStatus,
                    normalizedStatusCode = normalizedStatus.code,
                    jobOffered = jobOffered,
                    jobInterviewStatus = jobInterviewStatus,
                    hasOfferRecord = hasOfferRecord,
                    reasonForRejected = reasonForRejected,
                    statusHistoryJson = histArray.toString(),
                    rawJsonPayload = z.toString(2),
                    userNotes = existing?.userNotes ?: "",
                    interviewTimestamp = existing?.interviewTimestamp,
                    lastUpdatedTimestamp = now,
                    hasUnreadChange = statusChanged || (existing?.hasUnreadChange == true)
                )
            )
        }

        return ParseOutcome(
            jobs = parsedJobs,
            backendHistoryMap = backendHistoryMap,
            totalApplications = totalFromPagination ?: parsedJobs.size,
            totalPages = totalPages,
            currentPage = currentPage
        )
    }

    /**
     * Parses available jobs discovered by the Headless Browser component from sjp.gos.pk / usr-svc.sjp.gos.pk
     * and ranks them with a personalized Match Score based on the candidate's applied job history.
     */
    fun parseAvailableJobsJson(
        rawJsonText: String,
        appliedJobs: List<AppliedJobEntity>
    ): List<SuggestedSjpJob> {
        val trimmed = rawJsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val jobsArray: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                val dataObj = root.optJSONObject("data") ?: root
                dataObj.optJSONArray("jobs")
                    ?: dataObj.optJSONArray("rows")
                    ?: dataObj.optJSONArray("vacancies")
                    ?: dataObj.optJSONArray("items")
                    ?: dataObj.optJSONArray("applications")
                    ?: root.optJSONArray("jobs")
                    ?: root.optJSONArray("data")
                    ?: JSONArray()
            }
            else -> return emptyList()
        }

        val appliedDepartments = appliedJobs.map { it.departmentName.lowercase() }.toSet()
        val appliedTitleTokens = appliedJobs
            .flatMap { it.jobTitle.lowercase().split(" ", "-", "/", "(", ")") }
            .filter { it.length >= 4 }
            .toSet()
        val appliedIds = appliedJobs.map { it.applicationId }.toSet()

        val result = mutableListOf<SuggestedSjpJob>()
        for (i in 0 until jobsArray.length()) {
            val item = jobsArray.optJSONObject(i) ?: continue
            val jobObj = item.optJSONObject("Job") ?: item.optJSONObject("job") ?: item
            val deptObj = jobObj.optJSONObject("Department")
                ?: jobObj.optJSONObject("department")
                ?: item.optJSONObject("Department")

            val jobId = extractString(jobObj, "jobId", "id", "vacancyId", "postId")
                ?: extractString(item, "jobId", "id")
                ?: continue

            val title = extractString(jobObj, "jobTitle", "title", "postTitle", "designation", "name")
                ?: "Sindh Government Vacancy #$jobId"

            val dept = extractString(deptObj, "departmentName", "name", "title")
                ?: extractString(jobObj, "departmentName", "department")
                ?: "Government of Sindh"

            val rawBps = extractString(jobObj, "bps", "bpsScale", "grade", "scale") ?: "15"
            val bps = if (rawBps.uppercase().startsWith("BPS")) rawBps.uppercase() else "BPS-$rawBps"

            val closingDate = extractString(
                jobObj,
                "lastDate", "closingDate", "expiryDate", "deadline", "endDate", "applyBefore"
            )?.take(10) ?: "Open"

            val quota = extractString(
                jobObj,
                "domicile", "districtQuota", "quota", "location", "city", "district"
            ) ?: "Sindh Province"

            val qualification = extractString(
                jobObj,
                "qualification", "minQualification", "education", "degree"
            ) ?: "As per SJP portal criteria"

            val positions = jobObj.optInt("totalPositions", jobObj.optInt("noOfPosts", jobObj.optInt("vacancies", 1)))
                .coerceAtLeast(1)

            val applyUrl = extractString(jobObj, "applyUrl", "url")
                ?: "https://sjp.gos.pk/jobs/$jobId"

            val alreadyApplied = appliedIds.contains(jobId) ||
                appliedJobs.any {
                    it.jobTitle.equals(title, ignoreCase = true) &&
                        it.departmentName.equals(dept, ignoreCase = true)
                }

            // Compute personalized recommendation score & reason
            var score = 70
            val reasons = mutableListOf<String>()

            if (appliedDepartments.contains(dept.lowercase())) {
                score += 18
                reasons.add("Matches your applied department ($dept)")
            }
            val matchingKeywords = title.lowercase()
                .split(" ", "-", "/", "(", ")")
                .filter { it.length >= 4 && appliedTitleTokens.contains(it) }
            if (matchingKeywords.isNotEmpty()) {
                score += 10
                reasons.add("Matches your role profile (${matchingKeywords.first().replaceFirstChar { c -> c.uppercase() }})")
            }
            if (reasons.isEmpty()) {
                reasons.add("Active Government of Sindh ($bps) portal vacancy")
            }

            result.add(
                SuggestedSjpJob(
                    jobId = jobId,
                    jobTitle = title,
                    departmentName = dept,
                    bpsScale = bps,
                    lastDateToApply = closingDate,
                    districtOrQuota = quota,
                    minQualification = qualification,
                    totalPositions = positions,
                    matchScorePercent = score.coerceIn(65, 99),
                    matchReason = reasons.joinToString(" • "),
                    applyUrl = applyUrl,
                    isAlreadyApplied = alreadyApplied
                )
            )
        }

        return result.sortedWith(
            compareBy<SuggestedSjpJob> { it.isAlreadyApplied }
                .thenByDescending { it.matchScorePercent }
        )
    }

    fun parseBackendHistoryJson(statusHistoryJson: String): List<SjpBackendStatusEntry> {
        if (statusHistoryJson.isBlank()) return emptyList()
        val result = mutableListOf<SjpBackendStatusEntry>()
        return try {
            val arr = JSONArray(statusHistoryJson)
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                val st = extractString(x, "status", "applicationStatus") ?: continue
                val changedAtRaw = extractString(x, "changedAt", "createdAt", "updatedAt") ?: ""
                val millis = parseIsoTimestamp(changedAtRaw) ?: 0L
                result.add(
                    SjpBackendStatusEntry(
                        status = st,
                        changedAt = formatDisplayTimestamp(changedAtRaw, millis),
                        changedAtMillis = millis
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractString(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val v = obj.opt(k)?.toString()?.trim()
            if (!v.isNullOrEmpty() && v != "null") return v
        }
        return null
    }

    private fun parseIsoTimestamp(raw: String): Long? {
        if (raw.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun formatDisplayTimestamp(raw: String, millis: Long): String {
        if (millis > 0L) {
            return try {
                val outFmt = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.US)
                outFmt.format(Date(millis))
            } catch (_: Exception) {
                raw
            }
        }
        return raw.ifBlank { "—" }
    }
}
