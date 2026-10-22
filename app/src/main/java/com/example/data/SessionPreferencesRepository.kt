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

private val Context.sjpDataStore by preferencesDataStore(name = "sjp_session_preferences_v2")

const val DEFAULT_SJP_API_URL =
    "https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate"

data class SjpSessionState(
    val bearerToken: String = "",
    val cookieHeader: String = "",
    val encryptedTokenCipherPreview: String = "",
    val isEncryptedAtRest: Boolean = false,
    val candidateName: String = "",
    val candidateCnic: String = "",
    val customEndpointUrl: String = DEFAULT_SJP_API_URL,
    val pollingIntervalMinutes: Int = 30,
    val autoPollEnabled: Boolean = true,
    val lastPollTimestamp: Long = 0L,
    val nextPollTimestamp: Long = 0L,
    val lastPollMessage: String = "Sign in to sjp.gos.pk to fetch your applications",
    val lastPollSuccess: Boolean = true,
    val totalPollCount: Int = 0,
    val totalApplicationsOnServer: Int = 0,
    val totalPagesOnServer: Int = 1
) {
    val hasActiveSession: Boolean
        get() = bearerToken.isNotBlank() || cookieHeader.isNotBlank()
}

class SessionPreferencesRepository(private val context: Context) {

    private object Keys {
        val ENCRYPTED_BEARER_TOKEN = stringPreferencesKey("encrypted_bearer_token_v1")
        val ENCRYPTED_COOKIE_HEADER = stringPreferencesKey("encrypted_cookie_header_v1")
        // Legacy keys for automatic migration
        val LEGACY_BEARER_TOKEN = stringPreferencesKey("bearer_token")
        val LEGACY_COOKIE_HEADER = stringPreferencesKey("cookie_header")

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
        val TOTAL_APPS_ON_SERVER = intPreferencesKey("total_apps_on_server")
        val TOTAL_PAGES_ON_SERVER = intPreferencesKey("total_pages_on_server")
    }

    val sessionFlow: Flow<SjpSessionState> = context.sjpDataStore.data.map { prefs ->
        val rawStoredToken = prefs[Keys.ENCRYPTED_BEARER_TOKEN]
            ?: prefs[Keys.LEGACY_BEARER_TOKEN]
            ?: ""
        val rawStoredCookie = prefs[Keys.ENCRYPTED_COOKIE_HEADER]
            ?: prefs[Keys.LEGACY_COOKIE_HEADER]
            ?: ""

        val decryptedToken = TokenCryptoManager.decrypt(rawStoredToken)
        val decryptedCookie = TokenCryptoManager.decrypt(rawStoredCookie)
        val isEncrypted = TokenCryptoManager.isEncryptedPayload(rawStoredToken) ||
            TokenCryptoManager.isEncryptedPayload(rawStoredCookie)

        SjpSessionState(
            bearerToken = decryptedToken,
            cookieHeader = decryptedCookie,
            encryptedTokenCipherPreview = rawStoredToken.ifBlank { rawStoredCookie }.take(44),
            isEncryptedAtRest = isEncrypted,
            candidateName = prefs[Keys.CANDIDATE_NAME] ?: "",
            candidateCnic = prefs[Keys.CANDIDATE_CNIC] ?: "",
            customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT] ?: DEFAULT_SJP_API_URL,
            pollingIntervalMinutes = prefs[Keys.POLL_INTERVAL_MINS] ?: 30,
            autoPollEnabled = prefs[Keys.AUTO_POLL_ENABLED] ?: true,
            lastPollTimestamp = prefs[Keys.LAST_POLL_TIME] ?: 0L,
            nextPollTimestamp = prefs[Keys.NEXT_POLL_TIME] ?: 0L,
            lastPollMessage = prefs[Keys.LAST_POLL_MSG]
                ?: "Sign in to sjp.gos.pk to fetch your applications",
            lastPollSuccess = prefs[Keys.LAST_POLL_SUCCESS] ?: true,
            totalPollCount = prefs[Keys.TOTAL_POLL_COUNT] ?: 0,
            totalApplicationsOnServer = prefs[Keys.TOTAL_APPS_ON_SERVER] ?: 0,
            totalPagesOnServer = prefs[Keys.TOTAL_PAGES_ON_SERVER] ?: 1
        )
    }

    /**
     * Encrypts the session token and cookies using AndroidKeyStore AES-256-GCM
     * before persisting them to disk, and removes any legacy plaintext keys.
     */
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
                    prefs[Keys.ENCRYPTED_BEARER_TOKEN] = TokenCryptoManager.encrypt(cleaned)
                    prefs.remove(Keys.LEGACY_BEARER_TOKEN)
                }
            }
            if (!cookieHeader.isNullOrBlank()) {
                prefs[Keys.ENCRYPTED_COOKIE_HEADER] = TokenCryptoManager.encrypt(cookieHeader.trim())
                prefs.remove(Keys.LEGACY_COOKIE_HEADER)
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
            prefs.remove(Keys.ENCRYPTED_BEARER_TOKEN)
            prefs.remove(Keys.ENCRYPTED_COOKIE_HEADER)
            prefs.remove(Keys.LEGACY_BEARER_TOKEN)
            prefs.remove(Keys.LEGACY_COOKIE_HEADER)
            prefs[Keys.CANDIDATE_NAME] = ""
            prefs[Keys.CANDIDATE_CNIC] = ""
            prefs[Keys.LAST_POLL_MSG] = "Encrypted session cleared. Please log in on the SJP Login tab."
            prefs[Keys.TOTAL_APPS_ON_SERVER] = 0
            prefs[Keys.TOTAL_PAGES_ON_SERVER] = 1
        }
    }
}
