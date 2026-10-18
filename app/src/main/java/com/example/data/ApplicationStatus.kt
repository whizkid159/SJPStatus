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
        displayLabel = "Offered",
        shortLabel = "Offered",
        description = "Final offer issued in backend recruitment pipeline",
        funnelOrder = 6
    ),
    TEMP_OFFERED(
        code = "TEMP_OFFERED",
        displayLabel = "Temporary Offered",
        shortLabel = "Temp Offered",
        description = "Provisional offer pending final document verification",
        funnelOrder = 5
    ),
    SHORTLISTED(
        code = "SHORTLISTED",
        displayLabel = "Shortlisted",
        shortLabel = "Shortlisted",
        description = "Verified and shortlisted for interview or next phase",
        funnelOrder = 4
    ),
    TEMP_SHORTLISTED(
        code = "TEMP_SHORTLISTED",
        displayLabel = "Temporary Shortlisted",
        shortLabel = "Temp Shortlisted",
        description = "Provisionally shortlisted in hidden backend scrutiny",
        funnelOrder = 3
    ),
    UNDER_SCRUTINY(
        code = "UNDER_SCRUTINY",
        displayLabel = "Under Scrutiny",
        shortLabel = "Scrutiny",
        description = "Application is undergoing departmental verification",
        funnelOrder = 2
    ),
    APPLIED(
        code = "APPLIED",
        displayLabel = "Applied / Submitted",
        shortLabel = "Applied",
        description = "Submitted on portal; awaiting backend status transition",
        funnelOrder = 1
    ),
    REJECTED(
        code = "REJECTED",
        displayLabel = "Rejected",
        shortLabel = "Rejected",
        description = "Marked ineligible or rejected in backend scrutiny",
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
            val normalized = raw.trim().lowercase()
                .replace("_", " ")
                .replace("-", " ")

            return when {
                normalized.contains("temp") && normalized.contains("offer") -> TEMP_OFFERED
                normalized.contains("provisional") && normalized.contains("offer") -> TEMP_OFFERED
                normalized.contains("offer") || normalized.contains("selected") || normalized.contains("appointed") -> OFFERED
                normalized.contains("temp") && normalized.contains("shortlist") -> TEMP_SHORTLISTED
                normalized.contains("provisional") && normalized.contains("shortlist") -> TEMP_SHORTLISTED
                normalized.contains("shortlist") || normalized.contains("eligible") || normalized.contains("qualified") -> SHORTLISTED
                normalized.contains("reject") || normalized.contains("ineligible") || normalized.contains("disqualif") || normalized.contains("not eligible") -> REJECTED
                normalized.contains("scrutin") || normalized.contains("review") || normalized.contains("verif") || normalized.contains("process") -> UNDER_SCRUTINY
                else -> APPLIED
            }
        }

        fun fromCode(code: String?): ApplicationStatus {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: fromRawStatus(code)
        }
    }
}
