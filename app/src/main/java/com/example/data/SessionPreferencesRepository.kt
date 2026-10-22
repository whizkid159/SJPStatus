package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sjpDataStore by preferencesDataStore(name = "sjp_session_preferences_v3")

const val DEFAULT_SJP_API_URL =
    "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate"

data class SjpSessionState(
    val bearerToken: String = "",
    val cookieHeader: String = "",
    val savedCnic: String = "",
    val savedPassword: String = "",
    val rememberCredentials: Boolean = true,
    val candidateProfile: CandidateProfile = CandidateProfile(),
    val customEndpointUrl: String = DEFAULT_SJP_API_URL,
    val pollingIntervalMinutes: Int = 30,
    val autoPollEnabled: Boolean = true,
    val lastPollTimestamp: Long = 0L,
    val nextPollTimestamp: Long = 0L,
    val lastPollMessage: String = "Enter your SJP CNIC & Password to sign in",
    val lastPollSuccess: Boolean = true,
    val totalPollCount: Int = 0,
    val totalApplicationsOnServer: Int = 0,
    val totalPagesOnServer: Int = 1
) {
    val hasActiveSession: Boolean
        get() = bearerToken.isNotBlank() || cookieHeader.isNotBlank()

    val hasSavedCredentials: Boolean
        get() = savedCnic.isNotBlank() && savedPassword.isNotBlank()
}

class SessionPreferencesRepository(private val context: Context) {

    private object Keys {
        val BEARER_TOKEN = stringPreferencesKey("bearer_token")
        val COOKIE_HEADER = stringPreferencesKey("cookie_header")
        val SAVED_CNIC = stringPreferencesKey("saved_cnic")
        val SAVED_PASSWORD = stringPreferencesKey("saved_password")
        val REMEMBER_CREDENTIALS = booleanPreferencesKey("remember_credentials")

        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_DEGREE = stringPreferencesKey("profile_degree")
        val PROFILE_MAJOR = stringPreferencesKey("profile_major")
        val PROFILE_DISTRICT = stringPreferencesKey("profile_district")
        val PROFILE_QUOTA = stringPreferencesKey("profile_quota")
        val PROFILE_AGE = intPreferencesKey("profile_age")
        val PROFILE_EXP = intPreferencesKey("profile_exp")
        val PROFILE_SYNCED = booleanPreferencesKey("profile_synced")

        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val POLL_INTERVAL_MINS = intPreferencesKey("poll_interval_mins")
        val AUTO_POLL_ENABLED = booleanPreferencesKey("auto_poll_enabled")
        val LAST_POLL_TIME = longPreferencesKey("last_poll_time")
        val NEXT_POLL_TIME = longPreferencesKey("next_poll_time")
        val LAST_POLL_MSG = stringPreferencesKey("last_poll_msg")
        val LAST_POLL_SUCCESS = booleanPreferencesKey("last_poll_success")
        val TOTAL_POLL_COUNT = intPreferencesKey("total_poll_count")
        val TOTAL_APPS_ON_SERVER = intPreferencesKey("total_apps_on_server")
        val TOTAL_PAGES_ON_SERVER = intPreferencesKey("total_pages_on_server")
    }

    val sessionFlow: Flow<SjpSessionState> = context.sjpDataStore.data.map { prefs ->
        val cnic = prefs[Keys.SAVED_CNIC] ?: ""
        val profile = CandidateProfile(
            fullName = prefs[Keys.PROFILE_NAME] ?: "",
            cnic = cnic,
            highestDegreeLevel = prefs[Keys.PROFILE_DEGREE] ?: EducationLevel.BACHELOR_16.label,
            majorField = prefs[Keys.PROFILE_MAJOR] ?: "Computer Science / IT / General",
            domicileDistrict = prefs[Keys.PROFILE_DISTRICT] ?: "Sindh",
            domicileQuota = prefs[Keys.PROFILE_QUOTA] ?: "All Sindh (Urban & Rural)",
            ageYears = prefs[Keys.PROFILE_AGE] ?: 27,
            experienceYears = prefs[Keys.PROFILE_EXP] ?: 2,
            isSyncedFromPortal = prefs[Keys.PROFILE_SYNCED] ?: false
        )

        SjpSessionState(
            bearerToken = prefs[Keys.BEARER_TOKEN] ?: "",
            cookieHeader = prefs[Keys.COOKIE_HEADER] ?: "",
            savedCnic = cnic,
            savedPassword = prefs[Keys.SAVED_PASSWORD] ?: "",
            rememberCredentials = prefs[Keys.REMEMBER_CREDENTIALS] ?: true,
            candidateProfile = profile,
            customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT] ?: DEFAULT_SJP_API_URL,
            pollingIntervalMinutes = prefs[Keys.POLL_INTERVAL_MINS] ?: 30,
            autoPollEnabled = prefs[Keys.AUTO_POLL_ENABLED] ?: true,
            lastPollTimestamp = prefs[Keys.LAST_POLL_TIME] ?: 0L,
            nextPollTimestamp = prefs[Keys.NEXT_POLL_TIME] ?: 0L,
            lastPollMessage = prefs[Keys.LAST_POLL_MSG]
                ?: "Enter your SJP CNIC & Password to sign in",
            lastPollSuccess = prefs[Keys.LAST_POLL_SUCCESS] ?: true,
            totalPollCount = prefs[Keys.TOTAL_POLL_COUNT] ?: 0,
            totalApplicationsOnServer = prefs[Keys.TOTAL_APPS_ON_SERVER] ?: 0,
            totalPagesOnServer = prefs[Keys.TOTAL_PAGES_ON_SERVER] ?: 1
        )
    }

    suspend fun saveCredentials(
        cnic: String,
        password: String,
        remember: Boolean
    ) {
        context.sjpDataStore.edit { prefs ->
            prefs[Keys.SAVED_CNIC] = cnic.trim()
            prefs[Keys.REMEMBER_CREDENTIALS] = remember
            if (remember) {
                prefs[Keys.SAVED_PASSWORD] = password
            } else {
                prefs[Keys.SAVED_PASSWORD] = ""
            }
        }
    }

    suspend fun saveCapturedSession(
        bearerToken: String?,
        cookieHeader: String?,
        candidateName: String? = null,
        candidateCnic: String? = null
    ) {
        context.sjpDataStore.edit { prefs ->
            if (!bearerToken.isNullOrBlank()) {
                val cleaned = bearerToken
                    .removePrefix("Bearer ")
                    .removePrefix("bearer ")
                    .trim()
                    .removeSurrounding("\"")
                if (cleaned.isNotBlank() && cleaned != "null" && cleaned != "undefined") {
                    prefs[Keys.BEARER_TOKEN] = cleaned
                }
            }
            if (!cookieHeader.isNullOrBlank()) {
                prefs[Keys.COOKIE_HEADER] = cookieHeader.trim()
            }
            if (!candidateName.isNullOrBlank()) {
                prefs[Keys.PROFILE_NAME] = candidateName.trim()
            }
            if (!candidateCnic.isNullOrBlank()) {
                prefs[Keys.SAVED_CNIC] = candidateCnic.trim()
            }
        }
    }

    suspend fun updateCandidateProfile(profile: CandidateProfile) {
        context.sjpDataStore.edit { prefs ->
            if (profile.fullName.isNotBlank()) prefs[Keys.PROFILE_NAME] = profile.fullName.trim()
            if (profile.cnic.isNotBlank()) prefs[Keys.SAVED_CNIC] = profile.cnic.trim()
            prefs[Keys.PROFILE_DEGREE] = profile.highestDegreeLevel
            prefs[Keys.PROFILE_MAJOR] = profile.majorField.trim()
            prefs[Keys.PROFILE_DISTRICT] = profile.domicileDistrict.trim()
            prefs[Keys.PROFILE_QUOTA] = profile.domicileQuota.trim()
            prefs[Keys.PROFILE_AGE] = profile.ageYears.coerceIn(18, 60)
            prefs[Keys.PROFILE_EXP] = profile.experienceYears.coerceIn(0, 40)
            prefs[Keys.PROFILE_SYNCED] = profile.isSyncedFromPortal
        }
    }

    suspend fun updatePollingConfig(
        intervalMinutes: Int,
        autoPollEnabled: Boolean,
        customEndpointUrl: String
    ) {
        context.sjpDataStore.edit { prefs ->
            prefs[Keys.POLL_INTERVAL_MINS] = intervalMinutes.coerceIn(5, 360)
            prefs[Keys.AUTO_POLL_ENABLED] = autoPollEnabled
            if (customEndpointUrl.isNotBlank()) {
                prefs[Keys.CUSTOM_ENDPOINT] = customEndpointUrl.trim()
            }
        }
    }

    suspend fun recordPollResult(
        success: Boolean,
        message: String,
        intervalMinutes: Int,
        totalAppsOnServer: Int? = null,
        totalPagesOnServer: Int? = null
    ) {
        val now = System.currentTimeMillis()
        val next = now + intervalMinutes * 60_000L
        context.sjpDataStore.edit { prefs ->
            prefs[Keys.LAST_POLL_TIME] = now
            prefs[Keys.NEXT_POLL_TIME] = next
            prefs[Keys.LAST_POLL_MSG] = message
            prefs[Keys.LAST_POLL_SUCCESS] = success
            val currentCount = prefs[Keys.TOTAL_POLL_COUNT] ?: 0
            prefs[Keys.TOTAL_POLL_COUNT] = currentCount + 1
            if (totalAppsOnServer != null) {
                prefs[Keys.TOTAL_APPS_ON_SERVER] = totalAppsOnServer
            }
            if (totalPagesOnServer != null) {
                prefs[Keys.TOTAL_PAGES_ON_SERVER] = totalPagesOnServer
            }
        }
    }

    suspend fun clearSession() {
        context.sjpDataStore.edit { prefs ->
            prefs[Keys.BEARER_TOKEN] = ""
            prefs[Keys.COOKIE_HEADER] = ""
            prefs[Keys.SAVED_PASSWORD] = ""
            prefs[Keys.LAST_POLL_MSG] = "Signed out. Enter your CNIC & Password to log in."
            prefs[Keys.TOTAL_APPS_ON_SERVER] = 0
            prefs[Keys.TOTAL_PAGES_ON_SERVER] = 1
        }
    }
}
