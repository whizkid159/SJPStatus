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
    val changedAtFormatted: String = "",
    val detectedAtTimestamp: Long = System.currentTimeMillis(),
    val isUnread: Boolean = true
) {
    val previousStatusEnum: ApplicationStatus
        get() = ApplicationStatus.fromCode(previousStatusCode)

    val newStatusEnum: ApplicationStatus
        get() = ApplicationStatus.fromCode(newStatusCode)
}

@Entity(tableName = "open_sjp_jobs")
data class OpenJobEntity(
    @PrimaryKey
    val jobId: String,
    val jobTitle: String,
    val departmentName: String,
    val bpsScale: String,
    val requiredDegreeLevel: String,
    val requiredMajor: String,
    val domicileQuota: String,
    val minAge: Int,
    val maxAge: Int,
    val experienceRequiredYears: Int,
    val totalPositions: Int,
    val closingDate: String,
    val advertisementNo: String,
    val jobDescription: String,
    val rawJsonPayload: String,
    val fetchedAtTimestamp: Long = System.currentTimeMillis()
)

enum class EducationLevel(val rank: Int, val label: String) {
    MATRIC(1, "Matriculation (10 Years)"),
    INTERMEDIATE(2, "Intermediate (12 Years)"),
    BACHELOR_14(3, "Bachelor's (14 Years / B.A / B.Sc / B.Com)"),
    BACHELOR_16(4, "Bachelor's Honors (16 Years / BS / BE / BBA)"),
    MASTER_16_PLUS(5, "Master's / MS / M.Phil (16–18 Years)");

    companion object {
        fun fromString(raw: String?): EducationLevel {
            if (raw.isNullOrBlank()) return BACHELOR_16
            val u = raw.uppercase()
            return when {
                u.contains("PHIL") || u.contains("PHD") || u.contains("MS") || u.contains("MASTER") || u.contains("M.A") || u.contains("M.SC") || u.contains("MBA") || u.contains("18") -> MASTER_16_PLUS
                u.contains("16") || u.contains("BS") || u.contains("B.E") || u.contains("BBA") || u.contains("B.S") || u.contains("HON") -> BACHELOR_16
                u.contains("14") || u.contains("B.A") || u.contains("B.SC") || u.contains("B.COM") || u.contains("GRADUATE") || u.contains("BACHELOR") -> BACHELOR_14
                u.contains("INTER") || u.contains("F.A") || u.contains("F.SC") || u.contains("HSSC") || u.contains("12") || u.contains("DAE") || u.contains("DIPLOMA") -> INTERMEDIATE
                else -> MATRIC
            }
        }
    }
}

data class CandidateProfile(
    val fullName: String = "",
    val cnic: String = "",
    val highestDegreeLevel: String = EducationLevel.BACHELOR_16.label,
    val majorField: String = "Computer Science / IT / General",
    val domicileDistrict: String = "Sindh",
    val domicileQuota: String = "All Sindh (Urban & Rural)",
    val ageYears: Int = 27,
    val experienceYears: Int = 2,
    val isSyncedFromPortal: Boolean = false
) {
    val educationEnum: EducationLevel
        get() = EducationLevel.fromString(highestDegreeLevel)
}

data class RequirementCheck(
    val criterion: String,
    val jobRequirement: String,
    val candidateValue: String,
    val passed: Boolean
)

data class EligibilityEvaluation(
    val job: OpenJobEntity,
    val matchScorePercent: Int,
    val isEligible: Boolean,
    val isAlreadyApplied: Boolean,
    val appliedApplicationId: String? = null,
    val checks: List<RequirementCheck>
)

object EligibilityMatcher {

    fun evaluate(
        job: OpenJobEntity,
        profile: CandidateProfile,
        appliedJobs: List<AppliedJobEntity>
    ): EligibilityEvaluation {
        val alreadyAppliedJob = appliedJobs.firstOrNull { applied ->
            applied.applicationId == job.jobId ||
                (applied.jobTitle.equals(job.jobTitle, ignoreCase = true) &&
                    applied.departmentName.equals(job.departmentName, ignoreCase = true))
        }

        val reqEdu = EducationLevel.fromString(job.requiredDegreeLevel)
        val candEdu = profile.educationEnum
        val eduPassed = candEdu.rank >= reqEdu.rank

        val majorPassed = checkMajorMatch(job.requiredMajor, profile.majorField)
        val domicilePassed = checkDomicileMatch(job.domicileQuota, profile.domicileQuota, profile.domicileDistrict)
        val agePassed = profile.ageYears in job.minAge..job.maxAge
        val expPassed = profile.experienceYears >= job.experienceRequiredYears

        val checks = listOf(
            RequirementCheck(
                criterion = "Education Level",
                jobRequirement = job.requiredDegreeLevel,
                candidateValue = profile.highestDegreeLevel,
                passed = eduPassed
            ),
            RequirementCheck(
                criterion = "Field / Major",
                jobRequirement = job.requiredMajor,
                candidateValue = profile.majorField,
                passed = majorPassed
            ),
            RequirementCheck(
                criterion = "Domicile & Quota",
                jobRequirement = job.domicileQuota,
                candidateValue = "${profile.domicileDistrict} (${profile.domicileQuota})",
                passed = domicilePassed
            ),
            RequirementCheck(
                criterion = "Age Limit",
                jobRequirement = "${job.minAge}–${job.maxAge} years (incl. relaxation)",
                candidateValue = "${profile.ageYears} years",
                passed = agePassed
            ),
            RequirementCheck(
                criterion = "Experience",
                jobRequirement = if (job.experienceRequiredYears <= 0) "Fresh / No prior experience required" else "${job.experienceRequiredYears}+ years",
                candidateValue = "${profile.experienceYears} year(s)",
                passed = expPassed
            )
        )

        var score = 0
        if (eduPassed) score += 35
        if (majorPassed) score += 30
        if (domicilePassed) score += 15
        if (agePassed) score += 10
        if (expPassed) score += 10

        val isEligible = eduPassed && majorPassed && domicilePassed && agePassed && expPassed

        return EligibilityEvaluation(
            job = job,
            matchScorePercent = score.coerceIn(0, 100),
            isEligible = isEligible,
            isAlreadyApplied = alreadyAppliedJob != null,
            appliedApplicationId = alreadyAppliedJob?.applicationId,
            checks = checks
        )
    }

    private fun checkMajorMatch(requiredMajor: String, candidateMajor: String): Boolean {
        val req = requiredMajor.lowercase()
        val cand = candidateMajor.lowercase()
        if (req.contains("any") || req.contains("all") || req.contains("general") || req.contains("arts") || req.contains("science")) {
            return true
        }
        val candTokens = cand.split("/", ",", " ", "&").map { it.trim() }.filter { it.length >= 2 }
        for (token in candTokens) {
            if (req.contains(token)) return true
        }
        return false
    }

    private fun checkDomicileMatch(jobQuota: String, candQuota: String, candDistrict: String): Boolean {
        val jq = jobQuota.lowercase()
        val cq = candQuota.lowercase()
        val cd = candDistrict.lowercase()
        if (jq.contains("merit") || jq.contains("sindh") || jq.contains("both") || jq.contains("all") || cq.contains("all") || cq.contains("both")) {
            return true
        }
        if (cd.isNotBlank() && jq.contains(cd)) return true
        if (jq.contains("urban") && cq.contains("urban")) return true
        if (jq.contains("rural") && cq.contains("rural")) return true
        return true
    }
}
