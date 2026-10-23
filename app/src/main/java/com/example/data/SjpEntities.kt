package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class SjpBackendStatusEntry(
    val status: String,
    val changedAt: String,
    val changedAtMillis: Long
)

@Entity(tableName = "applied_jobs")
data class AppliedJobEntity(
    @PrimaryKey
    val applicationId: String,
    val jobTitle: String,
    val departmentName: String,
    val bpsScale: String,
    val advertisementNo: String,
    val appliedDate: String,
    val rawStatusString: String,
    val normalizedStatusCode: String,
    val jobOffered: Boolean,
    val jobInterviewStatus: String,
    val hasOfferRecord: Boolean,
    val reasonForRejected: String,
    val statusHistoryJson: String,
    val rawJsonPayload: String,
    val userNotes: String = "",
    val interviewTimestamp: Long? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val hasUnreadChange: Boolean = false
) {
    /**
     * Resolves the dedicated status category:
     * - If a job is marked SHORTLISTED (or TEMP_SHORTLISTED) on SJP, but its `jobInterviewStatus`
     *   shows that an interview is SCHEDULED or CONDUCTED, it belongs in the dedicated INTERVIEW tab
     *   so the Shortlisted tab only shows purely shortlisted jobs.
     */
    val statusEnum: ApplicationStatus
        get() {
            val base = if (rawStatusString.isNotBlank()) {
                ApplicationStatus.fromRawStatus(rawStatusString)
            } else {
                ApplicationStatus.fromCode(normalizedStatusCode)
            }
            if (base == ApplicationStatus.SHORTLISTED || base == ApplicationStatus.TEMP_SHORTLISTED) {
                val interviewUpper = jobInterviewStatus.trim().uppercase()
                val hasActiveInterview = interviewUpper.contains("SCHEDULE") ||
                    interviewUpper.contains("CONDUCT") ||
                    (interviewUpper.isNotBlank() &&
                        interviewUpper != "—" &&
                        interviewUpper != "-" &&
                        interviewUpper != "NONE" &&
                        interviewUpper != "NO" &&
                        interviewUpper != "N/A" &&
                        interviewUpper != "NULL" &&
                        !interviewUpper.contains("NOT") &&
                        !interviewUpper.contains("PENDING"))
                if (hasActiveInterview) {
                    return ApplicationStatus.INTERVIEW
                }
            }
            return base
        }
}

@Entity(tableName = "status_history")
data class StatusHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val applicationId: String,
    val jobTitle: String,
    val departmentName: String,
    val bpsScale: String,
    val previousStatusCode: String,
    val newStatusCode: String,
    val previousRawStatus: String,
    val newRawStatus: String,
    val remarksSnapshot: String,
    val changedAtFormatted: String = "",
    val detectedAtTimestamp: Long = System.currentTimeMillis(),
    val isUnread: Boolean = true
) {
    val previousStatusEnum: ApplicationStatus
        get() = if (previousRawStatus.isNotBlank()) {
            ApplicationStatus.fromRawStatus(previousRawStatus)
        } else {
            ApplicationStatus.fromCode(previousStatusCode)
        }

    val newStatusEnum: ApplicationStatus
        get() = if (newRawStatus.isNotBlank()) {
            ApplicationStatus.fromRawStatus(newRawStatus)
        } else {
            ApplicationStatus.fromCode(newStatusCode)
        }
}
