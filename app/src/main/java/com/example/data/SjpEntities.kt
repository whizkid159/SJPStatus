package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val frontendMaskedStatus: String,
    val backendRemarks: String,
    val meritScore: String,
    val rawJsonPayload: String,
    val userNotes: String = "",
    val interviewTimestamp: Long? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val hasUnreadChange: Boolean = false
) {
    val statusEnum: ApplicationStatus
        get() = ApplicationStatus.fromCode(normalizedStatusCode)
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
    val detectedAtTimestamp: Long = System.currentTimeMillis(),
    val isUnread: Boolean = true
) {
    val previousStatusEnum: ApplicationStatus
        get() = ApplicationStatus.fromCode(previousStatusCode)

    val newStatusEnum: ApplicationStatus
        get() = ApplicationStatus.fromCode(newStatusCode)
}
