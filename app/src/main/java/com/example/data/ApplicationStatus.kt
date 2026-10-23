package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class ApplicationStatus(
    val code: String,
    val displayLabel: String,
    val shortLabel: String,
    val description: String,
    val funnelOrder: Int
) {
    OFFERED(
        code = "OFFERED",
        displayLabel = "Offered / Joined",
        shortLabel = "Offered",
        description = "Job offered or joined on Sindh Job Portal",
        funnelOrder = 10
    ),
    TEMP_OFFERED(
        code = "TEMP_OFFERED",
        displayLabel = "Temp Offered",
        shortLabel = "Temp Offered",
        description = "Temporarily or provisionally offered in SJP backend",
        funnelOrder = 9
    ),
    INTERVIEW(
        code = "INTERVIEW",
        displayLabel = "Interview",
        shortLabel = "Interview",
        description = "Interview scheduled or conducted",
        funnelOrder = 8
    ),
    SHORTLISTED(
        code = "SHORTLISTED",
        displayLabel = "Shortlisted",
        shortLabel = "Shortlisted",
        description = "Shortlisted (awaiting interview schedule)",
        funnelOrder = 7
    ),
    TEMP_SHORTLISTED(
        code = "TEMP_SHORTLISTED",
        displayLabel = "Temp Shortlisted",
        shortLabel = "Temp Shortlisted",
        description = "Temporarily shortlisted in SJP backend",
        funnelOrder = 6
    ),
    WAITING(
        code = "WAITING",
        displayLabel = "Waiting",
        shortLabel = "Waiting",
        description = "On merit waiting list",
        funnelOrder = 5
    ),
    TEMP_WAITING(
        code = "TEMP_WAITING",
        displayLabel = "Temp Waiting",
        shortLabel = "Temp Waiting",
        description = "Temporarily placed on waiting list",
        funnelOrder = 4
    ),
    UNDER_SCRUTINY(
        code = "UNDER_SCRUTINY",
        displayLabel = "Under Scrutiny",
        shortLabel = "Under Scrutiny",
        description = "Application under departmental review",
        funnelOrder = 3
    ),
    APPLIED(
        code = "APPLIED",
        displayLabel = "Applied / Submitted",
        shortLabel = "Applied",
        description = "Application submitted to portal",
        funnelOrder = 2
    ),
    TEMP_REJECTED(
        code = "TEMP_REJECTED",
        displayLabel = "Temp Rejected",
        shortLabel = "Temp Rejected",
        description = "Temporarily or provisionally rejected (subject to review/objection)",
        funnelOrder = 1
    ),
    REJECTED(
        code = "REJECTED",
        displayLabel = "Rejected",
        shortLabel = "Rejected",
        description = "Marked rejected in backend status",
        funnelOrder = 0
    );

    fun badgeColors(isDark: Boolean): Pair<Color, Color> {
        return when (this) {
            OFFERED -> if (isDark) StatusOfferedDarkBg to StatusOfferedDarkFg else StatusOfferedBg to StatusOfferedFg
            TEMP_OFFERED -> if (isDark) StatusTempOfferedDarkBg to StatusTempOfferedDarkFg else StatusTempOfferedBg to StatusTempOfferedFg
            INTERVIEW -> if (isDark) StatusInterviewDarkBg to StatusInterviewDarkFg else StatusInterviewBg to StatusInterviewFg
            SHORTLISTED -> if (isDark) StatusShortlistedDarkBg to StatusShortlistedDarkFg else StatusShortlistedBg to StatusShortlistedFg
            TEMP_SHORTLISTED -> if (isDark) StatusTempShortlistedDarkBg to StatusTempShortlistedDarkFg else StatusTempShortlistedBg to StatusTempShortlistedFg
            WAITING -> if (isDark) StatusWaitingDarkBg to StatusWaitingDarkFg else StatusWaitingBg to StatusWaitingFg
            TEMP_WAITING -> if (isDark) StatusTempWaitingDarkBg to StatusTempWaitingDarkFg else StatusTempWaitingBg to StatusTempWaitingFg
            UNDER_SCRUTINY -> if (isDark) StatusScrutinyDarkBg to StatusScrutinyDarkFg else StatusScrutinyBg to StatusScrutinyFg
            APPLIED -> if (isDark) StatusAppliedDarkBg to StatusAppliedDarkFg else StatusAppliedBg to StatusAppliedFg
            TEMP_REJECTED -> if (isDark) StatusTempRejectedDarkBg to StatusTempRejectedDarkFg else StatusTempRejectedBg to StatusTempRejectedFg
            REJECTED -> if (isDark) StatusRejectedDarkBg to StatusRejectedDarkFg else StatusRejectedBg to StatusRejectedFg
        }
    }

    companion object {
        fun fromRawStatus(raw: String?): ApplicationStatus {
            if (raw.isNullOrBlank()) return APPLIED
            val upper = raw.trim().uppercase()
            val normalized = upper.replace("_", " ").replace("-", " ")
            val isTemp = normalized.contains("TEMP") || normalized.contains("PROVISIONAL")

            return when {
                isTemp && (normalized.contains("REJECT") || normalized.contains("DISQUALIF") || normalized.contains("INELIGIBLE")) -> TEMP_REJECTED
                normalized.contains("REJECT") || normalized.contains("DISQUALIF") || normalized.contains("INELIGIBLE") -> REJECTED
                isTemp && normalized.contains("OFFER") -> TEMP_OFFERED
                normalized.contains("OFFER") || normalized.contains("JOINED") || normalized.contains("SELECTED") || normalized.contains("APPOINTED") -> OFFERED
                isTemp && normalized.contains("WAIT") -> TEMP_WAITING
                normalized.contains("WAIT") -> WAITING
                normalized.contains("INTERVIEW") -> INTERVIEW
                isTemp && (normalized.contains("SHORTLIST") || normalized.contains("QUALIF")) -> TEMP_SHORTLISTED
                normalized.contains("SHORTLIST") || normalized.contains("QUALIF") -> SHORTLISTED
                normalized.contains("SCRUTIN") || normalized.contains("REVIEW") || normalized.contains("VERIF") -> UNDER_SCRUTINY
                else -> APPLIED
            }
        }

        fun fromCode(code: String?): ApplicationStatus {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: fromRawStatus(code)
        }
    }
}
