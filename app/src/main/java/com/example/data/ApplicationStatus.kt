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
        funnelOrder = 6
    ),
    TEMP_OFFERED(
        code = "TEMP_OFFERED",
        displayLabel = "Temporary Offered",
        shortLabel = "Temp Offered",
        description = "Provisional offer in backend pipeline",
        funnelOrder = 5
    ),
    SHORTLISTED(
        code = "SHORTLISTED",
        displayLabel = "Shortlisted / Interview",
        shortLabel = "Shortlisted",
        description = "Shortlisted or scheduled for interview",
        funnelOrder = 4
    ),
    TEMP_SHORTLISTED(
        code = "TEMP_SHORTLISTED",
        displayLabel = "Temp Shortlisted / Waiting",
        shortLabel = "Temp / Waiting",
        description = "Temporarily shortlisted or on waiting list",
        funnelOrder = 3
    ),
    UNDER_SCRUTINY(
        code = "UNDER_SCRUTINY",
        displayLabel = "Under Scrutiny",
        shortLabel = "Scrutiny",
        description = "Application under departmental review",
        funnelOrder = 2
    ),
    APPLIED(
        code = "APPLIED",
        displayLabel = "Applied / Submitted",
        shortLabel = "Applied",
        description = "Application submitted",
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
            SHORTLISTED -> if (isDark) StatusShortlistedDarkBg to StatusShortlistedDarkFg else StatusShortlistedBg to StatusShortlistedFg
            TEMP_SHORTLISTED -> if (isDark) StatusTempShortlistedDarkBg to StatusTempShortlistedDarkFg else StatusTempShortlistedBg to StatusTempShortlistedFg
            UNDER_SCRUTINY -> if (isDark) StatusScrutinyDarkBg to StatusScrutinyDarkFg else StatusScrutinyBg to StatusScrutinyFg
            APPLIED -> if (isDark) StatusAppliedDarkBg to StatusAppliedDarkFg else StatusAppliedBg to StatusAppliedFg
            REJECTED -> if (isDark) StatusRejectedDarkBg to StatusRejectedDarkFg else StatusRejectedBg to StatusRejectedFg
        }
    }

    companion object {
        fun fromRawStatus(raw: String?): ApplicationStatus {
            if (raw.isNullOrBlank()) return APPLIED
            val upper = raw.trim().uppercase()
            val normalized = upper.replace("_", " ").replace("-", " ")

            return when {
                normalized.contains("REJECTED") || normalized.contains("DISQUALIF") || normalized.contains("INELIGIBLE") -> REJECTED
                normalized.contains("TEMP") && normalized.contains("OFFER") -> TEMP_OFFERED
                normalized.contains("OFFERED") || normalized.contains("JOINED") || normalized.contains("SELECTED") || normalized.contains("APPOINTED") -> OFFERED
                normalized.contains("WAITING") || (normalized.contains("TEMP") && normalized.contains("SHORTLIST")) -> TEMP_SHORTLISTED
                normalized.contains("SHORTLISTED") || normalized.contains("INTERVIEW") || normalized.contains("QUALIFIED") -> SHORTLISTED
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
