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
     * Always derive statusEnum from rawStatusString first so dedicated statuses
     * (TEMP_SHORTLISTED, TEMP_REJECTED, TEMP_WAITING, WAITING, TEMP_OFFERED)
     * work immediately even on previously cached Room rows.
     */
    val statusEnum: ApplicationStatus
        get() = if (rawStatusString.isNotBlank()) {
            ApplicationStatus.fromRawStatus(rawStatusString)
        } else {
            ApplicationStatus.fromCode(normalizedStatusCode)
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
