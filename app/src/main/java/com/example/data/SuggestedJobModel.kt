package com.example.data

data class SuggestedSjpJob(
    val jobId: String,
    val jobTitle: String,
    val departmentName: String,
    val bpsScale: String,
    val lastDateToApply: String,
    val districtOrQuota: String,
    val minQualification: String,
    val totalPositions: Int,
    val matchScorePercent: Int,
    val matchReason: String,
    val applyUrl: String,
    val isAlreadyApplied: Boolean = false
)

sealed class HeadlessApplyStatus {
    data object Idle : HeadlessApplyStatus()
    data class Running(
        val jobId: String,
        val jobTitle: String,
        val currentStep: String,
        val logs: List<String>
    ) : HeadlessApplyStatus()

    data class Completed(
        val jobId: String,
        val jobTitle: String,
        val success: Boolean,
        val summaryMessage: String,
        val logs: List<String>
    ) : HeadlessApplyStatus()
}
