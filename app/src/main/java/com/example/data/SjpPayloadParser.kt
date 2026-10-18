package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object SjpPayloadParser {

    data class ParseOutcome(
        val jobs: List<AppliedJobEntity>,
        val candidateName: String?,
        val candidateCnic: String?
    )

    /**
     * Parses any JSON payload returned by sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate
     * Supports root JSONArray or wrapped JSONObject ({ data: [...], result: [...], applications: [...] }).
     */
    fun parseAppliedJobsJson(
        rawJsonText: String,
        existingJobsMap: Map<String, AppliedJobEntity> = emptyMap()
    ): ParseOutcome {
        val trimmed = rawJsonText.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Empty response received from endpoint")
        }

        var candidateName: String? = null
        var candidateCnic: String? = null
        val jsonArray: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val rootObj = JSONObject(trimmed)
                candidateName = extractFirstNonBlank(
                    rootObj,
                    "candidateName", "fullName", "name", "applicantName"
                )
                candidateCnic = extractFirstNonBlank(
                    rootObj,
                    "cnic", "candidateCnic", "nic"
                )
                findApplicationsArray(rootObj)
                    ?: throw IllegalArgumentException(
                        "JSON object does not contain an applications array (keys: ${
                            rootObj.keys().asSequence().joinToString()
                        })"
                    )
            }
            else -> throw IllegalArgumentException("Response is not valid JSON (starts with '${trimmed.take(24)}')")
        }

        val parsedList = mutableListOf<AppliedJobEntity>()
        val now = System.currentTimeMillis()

        for (i in 0 until jsonArray.length()) {
            val itemObj = jsonArray.optJSONObject(i) ?: continue
            val jobNested = itemObj.optJSONObject("job")
                ?: itemObj.optJSONObject("post")
                ?: itemObj.optJSONObject("jobDetail")
                ?: itemObj.optJSONObject("vacancy")

            val deptNested = itemObj.optJSONObject("department")
                ?: jobNested?.optJSONObject("department")
                ?: itemObj.optJSONObject("organization")

            val candidateNested = itemObj.optJSONObject("candidate")
                ?: itemObj.optJSONObject("applicant")

            if (candidateName.isNullOrBlank() && candidateNested != null) {
                candidateName = extractFirstNonBlank(candidateNested, "fullName", "name", "candidateName")
            }
            if (candidateCnic.isNullOrBlank() && candidateNested != null) {
                candidateCnic = extractFirstNonBlank(candidateNested, "cnic", "nic")
            }

            val appId = extractFirstNonBlank(
                itemObj,
                "applicationId", "application_id", "id", "appId", "trackingId", "trackingNo", "applicationNo"
            ) ?: extractFirstNonBlank(jobNested, "id", "jobId")
            ?: "SJP-APP-${1000 + i}"

            val jobTitle = extractFirstNonBlank(
                itemObj,
                "jobTitle", "postTitle", "title", "designation", "postName", "positionName", "job_name"
            ) ?: extractFirstNonBlank(
                jobNested,
                "title", "jobTitle", "postTitle", "designation", "name"
            ) ?: "Sindh Government Post #$appId"

            val departmentName = extractFirstNonBlank(
                itemObj,
                "departmentName", "department_name", "department", "ministry", "wing", "organizationName"
            ) ?: extractFirstNonBlank(
                deptNested,
                "name", "departmentName", "title"
            ) ?: extractFirstNonBlank(
                jobNested,
                "departmentName", "department"
            ) ?: "Government of Sindh Department"

            val rawBps = extractFirstNonBlank(
                itemObj,
                "bps", "bpsScale", "grade", "scale", "payScale", "bps_scale"
            ) ?: extractFirstNonBlank(
                jobNested,
                "bps", "bpsScale", "grade", "scale"
            ) ?: "BPS-15"

            val bpsFormatted = if (rawBps.uppercase().startsWith("BPS")) {
                rawBps.uppercase()
            } else {
                "BPS-$rawBps"
            }

            val advertisementNo = extractFirstNonBlank(
                itemObj,
                "advertisementNo", "advNo", "advertisementNumber", "jobCode", "refNo"
            ) ?: extractFirstNonBlank(
                jobNested,
                "advertisementNo", "advNo", "jobCode", "code"
            ) ?: "INF-KRY / SJP"

            val appliedDate = extractFirstNonBlank(
                itemObj,
                "appliedDate", "createdAt", "appliedAt", "submissionDate", "dateApplied", "created_at"
            )?.take(10) ?: "2026-09-15"

            // Extract the hidden backend status that sjp.gos.pk doesn't show on frontend
            val rawBackendStatus = extractFirstNonBlank(
                itemObj,
                "status", "applicationStatus", "candidateStatus", "scrutinyStatus",
                "subStatus", "internalStatus", "currentStatus", "stage", "selectionStatus"
            ) ?: "Applied"

            val frontendMaskedStatus = extractFirstNonBlank(
                itemObj,
                "publicStatus", "portalStatus", "frontendStatus", "displayStatus"
            ) ?: "Submitted"

            val remarks = extractFirstNonBlank(
                itemObj,
                "remarks", "scrutinyRemarks", "comments", "reason", "rejectionReason",
                "adminNotes", "verificationNote", "statusRemarks", "notes"
            ) ?: "Backend status retrieved via getAllAppliedJobsByCandidate"

            val meritScore = extractFirstNonBlank(
                itemObj,
                "meritScore", "testScore", "stsScore", "AggregateScore", "marks", "score", "percentage"
            ) ?: "N/A"

            val normalizedStatus = ApplicationStatus.fromRawStatus(rawBackendStatus)
            val existing = existingJobsMap[appId]
            val statusChanged = existing != null &&
                (existing.normalizedStatusCode != normalizedStatus.code ||
                    !existing.rawStatusString.equals(rawBackendStatus, ignoreCase = true))

            parsedList.add(
                AppliedJobEntity(
                    applicationId = appId,
                    jobTitle = jobTitle,
                    departmentName = departmentName,
                    bpsScale = bpsFormatted,
                    advertisementNo = advertisementNo,
                    appliedDate = appliedDate,
                    rawStatusString = rawBackendStatus,
                    normalizedStatusCode = normalizedStatus.code,
                    frontendMaskedStatus = frontendMaskedStatus,
                    backendRemarks = remarks,
                    meritScore = meritScore,
                    rawJsonPayload = itemObj.toString(2),
                    userNotes = existing?.userNotes ?: "",
                    interviewTimestamp = existing?.interviewTimestamp,
                    lastUpdatedTimestamp = now,
                    hasUnreadChange = statusChanged || (existing?.hasUnreadChange == true)
                )
            )
        }

        return ParseOutcome(
            jobs = parsedList,
            candidateName = candidateName,
            candidateCnic = candidateCnic
        )
    }

    private fun findApplicationsArray(root: JSONObject): JSONArray? {
        val candidateArrayKeys = listOf(
            "data", "applications", "appliedJobs", "result", "items",
            "records", "content", "payload", "response", "jobs"
        )
        for (key in candidateArrayKeys) {
            val arr = root.optJSONArray(key)
            if (arr != null) return arr
        }
        // Check one level deeper (e.g., { data: { applications: [...] } })
        for (key in candidateArrayKeys) {
            val nestedObj = root.optJSONObject(key) ?: continue
            for (subKey in candidateArrayKeys) {
                val subArr = nestedObj.optJSONArray(subKey)
                if (subArr != null) return subArr
            }
        }
        // Fallback: scan any key that holds a JSONArray
        val keys = root.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = root.optJSONArray(k)
            if (arr != null) return arr
        }
        return null
    }

    private fun extractFirstNonBlank(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key)
            if (value is JSONObject) {
                val nestedName = extractFirstNonBlank(value, "name", "title", "label", "status", "value")
                if (!nestedName.isNullOrBlank()) return nestedName
            } else if (value != null) {
                val str = value.toString().trim()
                if (str.isNotEmpty() && str != "null") return str
            }
        }
        return null
    }

    /**
     * Generates a realistic SJP `api/v2/candidates/application/getAllAppliedJobsByCandidate`
     * diagnostic JSON payload so users can test Raw JSON inspection, Status Diffs,
     * and Push Notifications right away or compare against their live portal account.
     */
    fun createDiagnosticSampleJson(): String {
        return """
        {
          "status": 200,
          "message": "Candidate applied jobs retrieved successfully",
          "endpoint": "api/v2/candidates/application/getAllAppliedJobsByCandidate",
          "candidateName": "Sindh Portal Candidate",
          "candidateCnic": "41304-*******-3",
          "data": [
            {
              "applicationId": "SJP-2026-88412",
              "jobTitle": "Assistant Director (IT / E-Governance)",
              "departmentName": "Information, Science & Technology Department",
              "bpsScale": "BPS-17",
              "advertisementNo": "INF-KRY-4418/26",
              "appliedDate": "2026-08-14",
              "frontendStatus": "Submitted",
              "status": "shortlisted",
              "scrutinyRemarks": "Domicile Form-D, PEC/HEC degree & STS score (78.5%) verified by Departmental Scrutiny Committee.",
              "meritScore": "78.5 / 100",
              "districtQuota": "Hyderabad / Urban",
              "interviewCenter": "Karachi Secretariat Committee Room #2"
            },
            {
              "applicationId": "SJP-2026-87905",
              "jobTitle": "Data Processing Officer",
              "departmentName": "Finance Department (Treasury & Accounts)",
              "bpsScale": "BPS-16",
              "advertisementNo": "FD-SO(B&E)-119/26",
              "appliedDate": "2026-08-02",
              "frontendStatus": "Submitted",
              "status": "temporary shortlisted",
              "scrutinyRemarks": "Provisionally shortlisted subject to original PRC & experience certificate attestation before interview.",
              "meritScore": "74.0 / 100",
              "districtQuota": "Sindh Merit",
              "scrutinyOfficerCode": "SCR-FIN-09"
            },
            {
              "applicationId": "SJP-2026-84210",
              "jobTitle": "Inspector Revenue / Mukhtiarkar",
              "departmentName": "Board of Revenue Sindh",
              "bpsScale": "BPS-16",
              "advertisementNo": "BOR-ESTT-2026/08",
              "appliedDate": "2026-07-19",
              "frontendStatus": "Submitted",
              "status": "temporary offered",
              "scrutinyRemarks": "Interview cleared (Merit #14). Provisional offer letter queued pending police character verification.",
              "meritScore": "83.2 / 100",
              "districtQuota": "Hyderabad Division"
            },
            {
              "applicationId": "SJP-2026-81034",
              "jobTitle": "Junior Clerk (IT Cadre)",
              "departmentName": "School Education & Literacy Department",
              "bpsScale": "BPS-11",
              "advertisementNo": "SELD-REC-2026/31",
              "appliedDate": "2026-06-28",
              "frontendStatus": "Submitted",
              "status": "offered",
              "scrutinyRemarks": "Final Appointment Offer approved by DRC. Typing speed 42 WPM verified.",
              "meritScore": "86.0 / 100",
              "districtQuota": "District Merit"
            },
            {
              "applicationId": "SJP-2026-89102",
              "jobTitle": "Monitoring & Evaluation Assistant",
              "departmentName": "Planning & Development Department",
              "bpsScale": "BPS-14",
              "advertisementNo": "P&D-MEC-772/26",
              "appliedDate": "2026-09-05",
              "frontendStatus": "Submitted",
              "status": "under scrutiny",
              "scrutinyRemarks": "Initial STS threshold met; academic transcripts queued for HEC verification.",
              "meritScore": "69.5 / 100",
              "districtQuota": "Sindh Rural"
            },
            {
              "applicationId": "SJP-2026-79511",
              "jobTitle": "Statistical Officer",
              "departmentName": "Bureau of Statistics (P&D Sindh)",
              "bpsScale": "BPS-16",
              "advertisementNo": "BOS-SINDH-104/26",
              "appliedDate": "2026-06-10",
              "frontendStatus": "Submitted",
              "status": "rejected",
              "scrutinyRemarks": "Post-qualification 2-year field experience in Economics/Statistics not attached in portal profile.",
              "meritScore": "62.0 / 100",
              "districtQuota": "Open Merit"
            }
          ]
        }
        """.trimIndent()
    }
}
