package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
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
     * Parses open job listings from usr-svc.sjp.gos.pk (e.g., /api/v2/jobs, /api/v2/candidates/jobs)
     */
    fun parseOpenJobsJson(rawJsonText: String): List<OpenJobEntity> {
        val trimmed = rawJsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val jobsArray: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                val dataObj = root.optJSONObject("data") ?: root
                dataObj.optJSONArray("jobs")
                    ?: dataObj.optJSONArray("vacancies")
                    ?: dataObj.optJSONArray("records")
                    ?: dataObj.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("jobs")
                    ?: return emptyList()
            }
            else -> return emptyList()
        }

        val list = mutableListOf<OpenJobEntity>()
        val now = System.currentTimeMillis()

        for (i in 0 until jobsArray.length()) {
            val item = jobsArray.optJSONObject(i) ?: continue
            val deptObj = item.optJSONObject("Department")
                ?: item.optJSONObject("department")

            val jobId = extractString(item, "jobId", "id", "vacancyId", "postId")
                ?: continue
            val jobTitle = extractString(item, "jobTitle", "title", "postTitle", "designation", "name")
                ?: "Government of Sindh Vacancy #$jobId"
            val departmentName = extractString(deptObj, "departmentName", "name", "title")
                ?: extractString(item, "departmentName", "department")
                ?: "Government of Sindh Department"

            val rawBps = extractString(item, "bps", "bpsScale", "grade", "scale") ?: "15"
            val bpsScale = if (rawBps.uppercase().startsWith("BPS")) rawBps.uppercase() else "BPS-$rawBps"

            val reqDegree = extractString(
                item,
                "qualification", "minQualification", "education", "degreeLevel", "requiredQualification", "requiredEducation"
            ) ?: inferEducationFromBpsAndTitle(bpsScale, jobTitle)

            val reqMajor = extractString(
                item,
                "major", "subject", "discipline", "fieldOfStudy", "specialization", "qualificationMajor"
            ) ?: inferMajorFromTitle(jobTitle, departmentName)

            val domicileQuota = extractString(
                item,
                "domicile", "quota", "districtQuota", "region", "domicileQuota"
            ) ?: "Sindh Merit (Urban & Rural)"

            val minAge = item.optInt("minAge", 18).coerceAtLeast(18)
            val maxAge = item.optInt("maxAge", 35).coerceAtLeast(28)
            val expYears = item.optInt("experience", item.optInt("minExperience", 0)).coerceAtLeast(0)
            val totalPositions = item.optInt("noOfPosts", item.optInt("totalPositions", item.optInt("vacancies", 1))).coerceAtLeast(1)

            val closingDate = extractString(
                item,
                "lastDate", "closingDate", "expiryDate", "deadline", "applyBefore", "endDate"
            )?.take(10) ?: "Open"

            val advNo = extractString(item, "advertisementNo", "advNo", "jobCode") ?: "SJP-ADV"
            val description = extractString(item, "jobDescription", "description", "duties", "Summary")
                ?: "Official recruitment post under $departmentName ($bpsScale)."

            list.add(
                OpenJobEntity(
                    jobId = jobId,
                    jobTitle = jobTitle,
                    departmentName = departmentName,
                    bpsScale = bpsScale,
                    requiredDegreeLevel = reqDegree,
                    requiredMajor = reqMajor,
                    domicileQuota = domicileQuota,
                    minAge = minAge,
                    maxAge = maxAge,
                    experienceRequiredYears = expYears,
                    totalPositions = totalPositions,
                    closingDate = closingDate,
                    advertisementNo = advNo,
                    jobDescription = description,
                    rawJsonPayload = item.toString(2),
                    fetchedAtTimestamp = now
                )
            )
        }
        return list
    }

    /**
     * Parses candidate profile & academic qualifications from usr-svc.sjp.gos.pk
     */
    fun parseCandidateProfileJson(
        rawJsonText: String,
        existingProfile: CandidateProfile
    ): CandidateProfile {
        return try {
            val root = JSONObject(rawJsonText)
            val data = root.optJSONObject("data")
                ?: root.optJSONObject("candidate")
                ?: root.optJSONObject("user")
                ?: root

            val name = extractString(data, "fullName", "name", "candidateName", "firstName")
                ?: existingProfile.fullName
            val cnic = extractString(data, "cnic", "nic", "candidateCnic")
                ?: existingProfile.cnic

            val district = extractString(
                data.optJSONObject("District") ?: data.optJSONObject("district") ?: data,
                "districtName", "domicileDistrict", "district", "city"
            ) ?: existingProfile.domicileDistrict

            val quota = extractString(data, "domicileType", "domicileQuota", "urbanRural", "quota")
                ?: existingProfile.domicileQuota

            var age = existingProfile.ageYears
            val dobRaw = extractString(data, "dateOfBirth", "dob", "birthDate")
            if (!dobRaw.isNullOrBlank()) {
                val dobMillis = parseIsoTimestamp(dobRaw)
                if (dobMillis != null && dobMillis > 0L) {
                    val dobCal = Calendar.getInstance().apply { timeInMillis = dobMillis }
                    val nowCal = Calendar.getInstance()
                    val computedAge = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
                    if (computedAge in 18..65) age = computedAge
                }
            }

            // Inspect educations / qualifications array if present
            val eduArray = data.optJSONArray("CandidateEducations")
                ?: data.optJSONArray("educations")
                ?: data.optJSONArray("qualifications")
                ?: data.optJSONArray("AcademicRecords")

            var highestLevel = existingProfile.educationEnum
            var detectedMajor = existingProfile.majorField

            if (eduArray != null && eduArray.length() > 0) {
                val majors = mutableListOf<String>()
                for (i in 0 until eduArray.length()) {
                    val eduObj = eduArray.optJSONObject(i) ?: continue
                    val degStr = extractString(
                        eduObj,
                        "degreeTitle", "degreeLevel", "degreeName", "qualification", "level", "title"
                    )
                    if (!degStr.isNullOrBlank()) {
                        val level = EducationLevel.fromString(degStr)
                        if (level.rank >= highestLevel.rank) {
                            highestLevel = level
                        }
                    }
                    val majStr = extractString(
                        eduObj,
                        "majorSubject", "major", "subject", "discipline", "fieldOfStudy", "specialization"
                    )
                    if (!majStr.isNullOrBlank()) {
                        majors.add(majStr)
                    }
                }
                if (majors.isNotEmpty()) {
                    detectedMajor = majors.distinct().joinToString(" / ")
                }
            }

            existingProfile.copy(
                fullName = name,
                cnic = cnic,
                highestDegreeLevel = highestLevel.label,
                majorField = detectedMajor,
                domicileDistrict = district,
                domicileQuota = quota,
                ageYears = age,
                isSyncedFromPortal = true
            )
        } catch (_: Exception) {
            existingProfile
        }
    }

    /**
     * Extracts open/eligible job opportunities from applied jobs' department & job metadata
     * or live SJP job feed when browsing on sjp.gos.pk.
     */
    fun inferEducationFromBpsAndTitle(bpsScale: String, title: String): String {
        val t = title.uppercase()
        val bpsNum = bpsScale.filter { it.isDigit() }.toIntOrNull() ?: 15
        return when {
            t.contains("DIRECTOR") || bpsNum >= 17 -> EducationLevel.BACHELOR_16.label
            bpsNum in 14..16 -> EducationLevel.BACHELOR_14.label
            bpsNum in 11..13 || t.contains("ASSISTANT") || t.contains("CLERK") -> EducationLevel.INTERMEDIATE.label
            else -> EducationLevel.MATRIC.label
        }
    }

    fun inferMajorFromTitle(title: String, department: String): String {
        val combined = "$title $department".uppercase()
        return when {
            combined.contains("IT") || combined.contains("COMPUTER") || combined.contains("SOFTWARE") || combined.contains("DATA") -> "Computer Science / IT / Software Engineering"
            combined.contains("ACCOUNT") || combined.contains("FINANCE") || combined.contains("AUDIT") || combined.contains("TREASURY") -> "Commerce / Finance / BBA / Economics"
            combined.contains("ENGINEER") -> "Engineering (PEC Registered)"
            combined.contains("STATISTIC") || combined.contains("ECONOM") || combined.contains("PLANNING") -> "Economics / Statistics / Mathematics"
            combined.contains("TEACHER") || combined.contains("EDUCATION") -> "Education / B.Ed / Relevant Subject"
            else -> "Any Recognized Discipline (General / Arts / Science / IT)"
        }
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
