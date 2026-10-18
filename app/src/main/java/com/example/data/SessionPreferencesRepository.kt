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

private val Context.sjpDataStore by preferencesDataStore(name = "sjp_session_preferences")

data class SjpSessionState(
    val bearerToken: String = "",
    val cookieHeader: String = "",
    val candidateName: String = "",
    val candidateCnic: String = "",
    val customEndpointUrl: String = "https://sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate",
    val pollingIntervalMinutes: Int = 30,
    val autoPollEnabled: Boolean = true,
    val lastPollTimestamp: Long = 0L,
    val nextPollTimestamp: Long = 0L,
    val lastPollMessage: String = "Ready to sync with sjp.gos.pk",
    val lastPollSuccess: Boolean = true,
    val totalPollCount: Int = 0,
    val isSandboxLoaded: Boolean = false
) {
    val hasActiveSession: Boolean
        get() = bearerToken.isNotBlank() || cookieHeader.isNotBlank()
}

class SessionPreferencesRepository(private val context: Context) {

    private object Keys {
        val BEARER_TOKEN = stringPreferencesKey("bearer_token")
        val COOKIE_HEADER = stringPreferencesKey("cookie_header")
        val CANDIDATE_NAME = stringPreferencesKey("candidate_name")
        val CANDIDATE_CNIC = stringPreferencesKey("candidate_cnic")
        val CUSTOM_ENDPOINT = stringPreferencesKey("custom_endpoint")
        val POLL_INTERVAL_MINS = intPreferencesKey("poll_interval_mins")
        val AUTO_POLL_ENABLED = booleanPreferencesKey("auto_poll_enabled")
        val LAST_POLL_TIME = longPreferencesKey("last_poll_time")
        val NEXT_POLL_TIME = longPreferencesKey("next_poll_time")
        val LAST_POLL_MSG = stringPreferencesKey("last_poll_msg")
        val LAST_POLL_SUCCESS = booleanPreferencesKey("last_poll_success")
        val TOTAL_POLL_COUNT = intPreferencesKey("total_poll_count")
        val IS_SANDBOX_LOADED = booleanPreferencesKey("is_sandbox_loaded")
    }

    val sessionFlow: Flow<SjpSessionState> = context.sjpDataStore.data.map { prefs ->
        SjpSessionState(
            bearerToken = prefs[Keys.BEARER_TOKEN] ?: "",
            cookieHeader = prefs[Keys.COOKIE_HEADER] ?: "",
            candidateName = prefs[Keys.CANDIDATE_NAME] ?: "",
            candidateCnic = prefs[Keys.CANDIDATE_CNIC] ?: "",
            customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT]
                ?: "https://sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate",
            pollingIntervalMinutes = prefs[Keys.POLL_INTERVAL_MINS] ?: 30,
            autoPollEnabled = prefs[Keys.AUTO_POLL_ENABLED] ?: true,
            lastPollTimestamp = prefs[Keys.LAST_POLL_TIME] ?: 0L,
            nextPollTimestamp = prefs[Keys.NEXT_POLL_TIME] ?: 0L,
            lastPollMessage = prefs[Keys.LAST_POLL_MSG] ?: "Ready to sync with sjp.gos.pk",
            lastPollSuccess = prefs[Keys.LAST_POLL_SUCCESS] ?: true,
            totalPollCount = prefs[Keys.TOTAL_POLL_COUNT] ?: 0,
            isSandboxLoaded = prefs[Keys.IS_SANDBOX_LOADED] ?: false
        )
    }

    suspend fun saveCapturedSession(
        bearerToken: String?,
        cookieHeader: String?,
        candidateName: String? = null,
        candidateCnic: String? = null
    ) {
        context.sjpDataStore.edit { prefs ->
            if (!bearerToken.isNullOrBlank()) {
                val cleaned = bearerToken.removePrefix("Bearer ").removePrefix("bearer ").trim()
                if (cleaned.isNotBlank() && cleaned != "null" && cleaned != "undefined") {
                    prefs[Keys.BEARER_TOKEN] = cleaned
                }
            }
            if (!cookieHeader.isNullOrBlank()) {
                prefs[Keys.COOKIE_HEADER] = cookieHeader.trim()
            }
            if (!candidateName.isNullOrBlank()) {
                prefs[Keys.CANDIDATE_NAME] = candidateName.trim()
            }
            if (!candidateCnic.isNullOrBlank()) {
                prefs[Keys.CANDIDATE_CNIC] = candidateCnic.trim()
            }
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
        isSandbox: Boolean? = null
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
            if (isSandbox != null) {
                prefs[Keys.IS_SANDBOX_LOADED] = isSandbox
            }
        }
    }

    suspend fun clearSession() {
        context.sjpDataStore.edit { prefs ->
            prefs[Keys.BEARER_TOKEN] = ""
            prefs[Keys.COOKIE_HEADER] = ""
            prefs[Keys.CANDIDATE_NAME] = ""
            prefs[Keys.CANDIDATE_CNIC] = ""
            prefs[Keys.LAST_POLL_MSG] = "Session cleared"
        }
    }
}
