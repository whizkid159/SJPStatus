package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SjpDocumentRef(
    val fieldPath: String,
    val label: String,
    val rawValue: String,
    val isAppointmentOrder: Boolean,
    val isBase64: Boolean,
    val offerId: String? = null
) {
    fun uniqueKey(applicationId: String): String = "$applicationId|$fieldPath|$rawValue"

    val displayFileName: String
        get() {
            if (isBase64) {
                return if (isAppointmentOrder) "appointment_order.pdf" else "sjp_document.pdf"
            }
            val withoutQuery = rawValue.substringBefore("?").substringBefore("#")
            val lastSeg = withoutQuery.substringAfterLast('/').substringAfterLast('\\').trim()
            return if (lastSeg.isNotBlank() && lastSeg.length <= 120) {
                if (lastSeg.lowercase(Locale.US).endsWith(".pdf")) lastSeg else "$lastSeg.pdf"
            } else {
                if (isAppointmentOrder) "appointment_order.pdf" else "sjp_document.pdf"
            }
        }
}

data class DocumentProbeAttempt(
    val url: String,
    val statusCode: Int?,
    val contentType: String?,
    val note: String,
    val succeeded: Boolean = false
)

sealed class DocumentDownloadState {
    data object Idle : DocumentDownloadState()

    data class Probing(
        val currentUrl: String,
        val currentStep: Int,
        val totalSteps: Int,
        val attempts: List<DocumentProbeAttempt>
    ) : DocumentDownloadState()

    data class Success(
        val fileName: String,
        val savedLocationLabel: String,
        val resolvedUrl: String,
        val fileUri: Uri,
        val sizeBytes: Long,
        val attempts: List<DocumentProbeAttempt>
    ) : DocumentDownloadState()

    data class Error(
        val message: String,
        val rawFieldValue: String,
        val attempts: List<DocumentProbeAttempt>
    ) : DocumentDownloadState()
}

object SjpDocumentExtractor {

    private val NON_DOCUMENT_VALUES = setOf(
        "", "null", "undefined", "false", "true", "none", "n/a", "na", "-", "—", "no", "yes", "pending"
    )

    fun extractDocuments(rawJsonPayload: String): List<SjpDocumentRef> {
        if (rawJsonPayload.isBlank()) return emptyList()
        return try {
            val root = JSONObject(rawJsonPayload)
            val offerObj = root.optJSONObject("Offer") ?: root.optJSONObject("offer")
            val offerId = offerObj?.let {
                extractSimpleId(it, "offerId", "id", "jobOfferId")
            }

            val found = mutableListOf<SjpDocumentRef>()
            scanJsonObject(root, prefix = "", offerId = offerId, output = found)

            found
                .distinctBy { it.rawValue }
                .sortedWith(
                    compareByDescending<SjpDocumentRef> { it.isAppointmentOrder }
                        .thenByDescending { it.fieldPath.lowercase(Locale.US).contains("offer") }
                        .thenBy { it.fieldPath.length }
                )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractSimpleId(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val v = obj.opt(k)?.toString()?.trim()
            if (!v.isNullOrBlank() && v != "null") return v
        }
        return null
    }

    private fun scanJsonObject(
        obj: JSONObject,
        prefix: String,
        offerId: String?,
        output: MutableList<SjpDocumentRef>
    ) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (obj.isNull(key)) continue
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = obj.opt(key) ?: continue

            when (value) {
                is JSONObject -> scanJsonObject(value, path, offerId, output)
                is JSONArray -> scanJsonArray(value, path, offerId, output)
                is String -> inspectStringValue(key, path, value.trim(), offerId, output)
            }
        }
    }

    private fun scanJsonArray(
        arr: JSONArray,
        prefix: String,
        offerId: String?,
        output: MutableList<SjpDocumentRef>
    ) {
        for (i in 0 until arr.length()) {
            val item = arr.opt(i) ?: continue
            val itemPath = "$prefix[$i]"
            when (item) {
                is JSONObject -> scanJsonObject(item, itemPath, offerId, output)
                is JSONArray -> scanJsonArray(item, itemPath, offerId, output)
                is String -> inspectStringValue(prefix, itemPath, item.trim(), offerId, output)
            }
        }
    }

    private fun inspectStringValue(
        key: String,
        path: String,
        value: String,
        offerId: String?,
        output: MutableList<SjpDocumentRef>
    ) {
        val lowerVal = value.lowercase(Locale.US)
        if (lowerVal in NON_DOCUMENT_VALUES) return

        val normalizedKey = key.lowercase(Locale.US).replace("_", "").replace("-", "")
        val normalizedPath = path.lowercase(Locale.US).replace("_", "").replace("-", "")

        val isPdfBase64 = lowerVal.startsWith("data:application/pdf") || value.startsWith("JVBERi0")
        val looksLikePdfString = lowerVal.endsWith(".pdf") ||
            lowerVal.contains(".pdf?") ||
            lowerVal.contains(".pdf#") ||
            isPdfBase64

        val isAppointmentKey = normalizedPath.contains("appointmentorder") ||
            normalizedPath.contains("appointmentletter") ||
            normalizedPath.contains("appointmentpdf") ||
            (normalizedKey.contains("appointment") &&
                (normalizedKey.contains("upload") ||
                    normalizedKey.contains("file") ||
                    normalizedKey.contains("doc") ||
                    normalizedKey.contains("url") ||
                    normalizedKey.contains("path") ||
                    normalizedKey.contains("pdf") ||
                    normalizedKey.contains("order")))

        val isOfferLetterKey = normalizedPath.contains("offerletter") ||
            normalizedPath.contains("joiningletter") ||
            normalizedPath.contains("joiningorder") ||
            (normalizedPath.contains("offer") &&
                (normalizedKey.contains("file") ||
                    normalizedKey.contains("pdf") ||
                    normalizedKey.contains("upload") ||
                    normalizedKey.contains("document") ||
                    normalizedKey.contains("url")))

        val looksLikeFilePathOrUrl = looksLikePdfString ||
            lowerVal.startsWith("http://") ||
            lowerVal.startsWith("https://") ||
            lowerVal.startsWith("//") ||
            value.contains("/") ||
            value.contains("\\") ||
            Regex(".*\\.(pdf|doc|docx|png|jpg|jpeg)(\\?.*)?$", RegexOption.IGNORE_CASE).matches(value)

        if (looksLikePdfString || ((isAppointmentKey || isOfferLetterKey) && looksLikeFilePathOrUrl)) {
            val label = when {
                isAppointmentKey -> "Appointment Order PDF"
                isOfferLetterKey -> "Offer / Joining Letter PDF"
                normalizedPath.contains("advertisement") || normalizedPath.contains("adv") -> "Job Advertisement PDF"
                normalizedPath.contains("interview") -> "Interview Letter PDF"
                else -> "Attached PDF Document"
            }
            output.add(
                SjpDocumentRef(
                    fieldPath = path,
                    label = label,
                    rawValue = value,
                    isAppointmentOrder = isAppointmentKey || lowerVal.contains("appointment"),
                    isBase64 = isPdfBase64,
                    offerId = offerId
                )
            )
        }
    }
}

object SjpDocumentDownloader {

    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val CACHE_SUBDIR = "appointment_orders"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    suspend fun downloadWithProbe(
        context: Context,
        applicationId: String,
        jobTitle: String,
        doc: SjpDocumentRef,
        bearerToken: String,
        cookieHeader: String,
        apiBaseEndpoint: String,
        discoveredBaseUrls: List<String>,
        customBaseUrl: String,
        onProgress: (DocumentDownloadState.Probing) -> Unit
    ): Pair<DocumentDownloadState, String?> = withContext(Dispatchers.IO) {
        val safeFileName = buildSafeDownloadFileName(applicationId, jobTitle, doc)

        // 1. Handle inline Base64 PDFs immediately
        if (doc.isBase64) {
            return@withContext try {
                val base64Body = doc.rawValue.substringAfter("base64,", doc.rawValue).trim()
                val bytes = Base64.decode(base64Body, Base64.DEFAULT)
                if (!isValidPdfPayload(bytes, "application/pdf")) {
                    DocumentDownloadState.Error(
                        message = "Field contained Base64 data, but it did not decode to a valid PDF.",
                        rawFieldValue = doc.rawValue,
                        attempts = listOf(
                            DocumentProbeAttempt(
                                url = "inline:base64",
                                statusCode = 200,
                                contentType = "application/pdf",
                                note = "Decoded ${bytes.size} bytes (missing %PDF header)",
                                succeeded = false
                            )
                        )
                    ) to null
                } else {
                    val saved = persistPdfBytes(context, safeFileName, bytes)
                    DocumentDownloadState.Success(
                        fileName = saved.fileName,
                        savedLocationLabel = saved.locationLabel,
                        resolvedUrl = "inline:base64",
                        fileUri = saved.shareableUri,
                        sizeBytes = bytes.size.toLong(),
                        attempts = listOf(
                            DocumentProbeAttempt(
                                url = "inline:base64",
                                statusCode = 200,
                                contentType = "application/pdf",
                                note = "Decoded inline Base64 PDF (${formatBytes(bytes.size.toLong())})",
                                succeeded = true
                            )
                        )
                    ) to null
                }
            } catch (e: Exception) {
                DocumentDownloadState.Error(
                    message = "Failed to decode Base64 PDF: ${e.localizedMessage ?: "Invalid encoding"}",
                    rawFieldValue = doc.rawValue,
                    attempts = emptyList()
                ) to null
            }
        }

        // 2. Build prioritized probe URLs
        val candidateUrls = buildCandidateUrls(
            rawValue = doc.rawValue,
            applicationId = applicationId,
            offerId = doc.offerId,
            apiBaseEndpoint = apiBaseEndpoint,
            discoveredBaseUrls = discoveredBaseUrls,
            customBaseUrl = customBaseUrl
        )

        val attempts = mutableListOf<DocumentProbeAttempt>()
        val totalSteps = candidateUrls.size

        for ((index, candidateUrl) in candidateUrls.withIndex()) {
            onProgress(
                DocumentDownloadState.Probing(
                    currentUrl = candidateUrl,
                    currentStep = index + 1,
                    totalSteps = totalSteps,
                    attempts = attempts.toList()
                )
            )

            val outcome = executeSingleProbe(
                url = candidateUrl,
                bearerToken = bearerToken,
                cookieHeader = cookieHeader,
                allowJsonRedirect = true
            )

            attempts.addAll(outcome.attempts)

            if (outcome.pdfBytes != null && outcome.resolvedUrl != null) {
                val saved = persistPdfBytes(context, safeFileName, outcome.pdfBytes)
                val workingBase = inferBaseUrlForCaching(outcome.resolvedUrl, doc.rawValue)
                return@withContext DocumentDownloadState.Success(
                    fileName = saved.fileName,
                    savedLocationLabel = saved.locationLabel,
                    resolvedUrl = outcome.resolvedUrl,
                    fileUri = saved.shareableUri,
                    sizeBytes = outcome.pdfBytes.size.toLong(),
                    attempts = attempts.toList()
                ) to workingBase
            }
        }

        val hint = if (bearerToken.isBlank() && cookieHeader.isBlank()) {
            "Your SJP session appears logged out. Please sign in on the SJP Login tab and try again."
        } else {
            "Probed ${attempts.size} SJP API & upload routes for '${doc.rawValue}', but none returned a PDF. Expand Probe Diagnostics below to inspect responses or enter a Custom File Base URL."
        }

        DocumentDownloadState.Error(
            message = hint,
            rawFieldValue = doc.rawValue,
            attempts = attempts.toList()
        ) to null
    }

    suspend fun downloadDirectUrl(
        context: Context,
        url: String,
        suggestedFileName: String?,
        bearerToken: String,
        cookieHeader: String
    ): Pair<DocumentDownloadState, String?> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url) ?: return@withContext DocumentDownloadState.Error(
            message = "Invalid download URL: $url",
            rawFieldValue = url,
            attempts = emptyList()
        ) to null

        val outcome = executeSingleProbe(
            url = normalizedUrl,
            bearerToken = bearerToken,
            cookieHeader = cookieHeader,
            allowJsonRedirect = true
        )

        if (outcome.pdfBytes != null && outcome.resolvedUrl != null) {
            val cleanName = sanitizeFileName(
                suggestedFileName?.takeIf { it.isNotBlank() }
                    ?: normalizedUrl.substringBefore("?").substringAfterLast('/').ifBlank { "sjp_appointment_order.pdf" }
            )
            val saved = persistPdfBytes(context, cleanName, outcome.pdfBytes)
            val basePrefix = normalizedUrl.substringBefore("?").substringBeforeLast("/", "")
                .takeIf { it.startsWith("https://") }
            DocumentDownloadState.Success(
                fileName = saved.fileName,
                savedLocationLabel = saved.locationLabel,
                resolvedUrl = outcome.resolvedUrl,
                fileUri = saved.shareableUri,
                sizeBytes = outcome.pdfBytes.size.toLong(),
                attempts = outcome.attempts
            ) to basePrefix
        } else {
            DocumentDownloadState.Error(
                message = "Could not download PDF from $normalizedUrl",
                rawFieldValue = normalizedUrl,
                attempts = outcome.attempts
            ) to null
        }
    }

    private data class SingleProbeOutcome(
        val pdfBytes: ByteArray? = null,
        val resolvedUrl: String? = null,
        val attempts: List<DocumentProbeAttempt>
    )

    private fun executeSingleProbe(
        url: String,
        bearerToken: String,
        cookieHeader: String,
        allowJsonRedirect: Boolean
    ): SingleProbeOutcome {
        val localAttempts = mutableListOf<DocumentProbeAttempt>()
        val httpUrl = url.toHttpUrlOrNull()
        if (httpUrl == null) {
            localAttempts.add(DocumentProbeAttempt(url, null, null, "Invalid URL format"))
            return SingleProbeOutcome(attempts = localAttempts)
        }

        val isSjpHost = isTrustedSjpHost(httpUrl.host)

        // First try: with SJP auth headers ONLY if host is *.sjp.gos.pk
        val firstTry = runHttpGet(
            url = url,
            attachSjpAuth = isSjpHost,
            bearerToken = bearerToken,
            cookieHeader = cookieHeader
        )
        localAttempts.add(firstTry.attempt)

        if (firstTry.pdfBytes != null) {
            return SingleProbeOutcome(
                pdfBytes = firstTry.pdfBytes,
                resolvedUrl = firstTry.finalUrl ?: url,
                attempts = localAttempts
            )
        }

        // If SJP API returned JSON containing a pre-signed / direct file URL, follow it immediately!
        if (allowJsonRedirect && !firstTry.jsonSignedUrl.isNullOrBlank()) {
            val signedUrl = normalizeUrl(firstTry.jsonSignedUrl)
            if (signedUrl != null && signedUrl != url) {
                val followUp = executeSingleProbe(
                    url = signedUrl,
                    bearerToken = bearerToken,
                    cookieHeader = cookieHeader,
                    allowJsonRedirect = false
                )
                localAttempts.addAll(followUp.attempts)
                if (followUp.pdfBytes != null) {
                    return SingleProbeOutcome(
                        pdfBytes = followUp.pdfBytes,
                        resolvedUrl = followUp.resolvedUrl,
                        attempts = localAttempts
                    )
                }
            }
        }

        // If an external pre-signed URL or SJP static CDN returned 400/403 with auth headers, retry cleanly without auth
        if (isSjpHost && (firstTry.attempt.statusCode == 400 || firstTry.attempt.statusCode == 403)) {
            val noAuthTry = runHttpGet(
                url = url,
                attachSjpAuth = false,
                bearerToken = "",
                cookieHeader = ""
            )
            localAttempts.add(noAuthTry.attempt)
            if (noAuthTry.pdfBytes != null) {
                return SingleProbeOutcome(
                    pdfBytes = noAuthTry.pdfBytes,
                    resolvedUrl = noAuthTry.finalUrl ?: url,
                    attempts = localAttempts
                )
            }
        }

        return SingleProbeOutcome(attempts = localAttempts)
    }

    private data class RawHttpResult(
        val attempt: DocumentProbeAttempt,
        val pdfBytes: ByteArray? = null,
        val finalUrl: String? = null,
        val jsonSignedUrl: String? = null
    )

    private fun runHttpGet(
        url: String,
        attachSjpAuth: Boolean,
        bearerToken: String,
        cookieHeader: String
    ): RawHttpResult {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/pdf, application/octet-stream, application/json;q=0.9, */*;q=0.5")
                .header("User-Agent", USER_AGENT)

            if (attachSjpAuth) {
                reqBuilder.header("Origin", "https://sjp.gos.pk")
                reqBuilder.header("Referer", "https://sjp.gos.pk/")
                if (cookieHeader.isNotBlank()) {
                    reqBuilder.header("Cookie", cookieHeader)
                }
                if (bearerToken.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $bearerToken")
                }
            }

            probeClient.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                val contentType = response.header("Content-Type")?.substringBefore(";")?.trim()
                val finalUrl = response.request.url.toString()

                if (!response.isSuccessful) {
                    return RawHttpResult(
                        attempt = DocumentProbeAttempt(
                            url = url,
                            statusCode = code,
                            contentType = contentType,
                            note = "HTTP $code",
                            succeeded = false
                        )
                    )
                }

                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (isValidPdfPayload(bytes, contentType)) {
                    return RawHttpResult(
                        attempt = DocumentProbeAttempt(
                            url = url,
                            statusCode = code,
                            contentType = contentType ?: "application/pdf",
                            note = "PDF verified (${formatBytes(bytes.size.toLong())})",
                            succeeded = true
                        ),
                        pdfBytes = bytes,
                        finalUrl = finalUrl
                    )
                }

                // Check if endpoint returned JSON with a signed/download URL or base64 PDF
                val textSample = bytes.take(16_384).toByteArray().toString(Charsets.UTF_8).trim()
                if (textSample.startsWith("{")) {
                    val extractedUrl = extractUrlFromJsonResponse(textSample)
                    if (!extractedUrl.isNullOrBlank()) {
                        return RawHttpResult(
                            attempt = DocumentProbeAttempt(
                                url = url,
                                statusCode = code,
                                contentType = contentType,
                                note = "JSON returned signed file URL -> following",
                                succeeded = false
                            ),
                            jsonSignedUrl = extractedUrl
                        )
                    }
                    val extractedBase64 = extractBase64PdfFromJsonResponse(textSample)
                    if (extractedBase64 != null) {
                        return RawHttpResult(
                            attempt = DocumentProbeAttempt(
                                url = url,
                                statusCode = code,
                                contentType = "application/pdf",
                                note = "JSON returned inline PDF (${formatBytes(extractedBase64.size.toLong())})",
                                succeeded = true
                            ),
                            pdfBytes = extractedBase64,
                            finalUrl = finalUrl
                        )
                    }
                }

                val note = when {
                    textSample.startsWith("<!DOCTYPE", ignoreCase = true) ||
                        textSample.startsWith("<html", ignoreCase = true) ->
                        "HTTP $code returned HTML page (not a PDF)"
                    else ->
                        "HTTP $code returned ${contentType ?: "unknown"} (${bytes.size} B, not PDF)"
                }

                RawHttpResult(
                    attempt = DocumentProbeAttempt(
                        url = url,
                        statusCode = code,
                        contentType = contentType,
                        note = note,
                        succeeded = false
                    )
                )
            }
        } catch (e: Exception) {
            val shortErr = e.localizedMessage?.take(90) ?: e.javaClass.simpleName
            RawHttpResult(
                attempt = DocumentProbeAttempt(
                    url = url,
                    statusCode = null,
                    contentType = null,
                    note = shortErr,
                    succeeded = false
                )
            )
        }
    }

    internal fun buildCandidateUrls(
        rawValue: String,
        applicationId: String,
        offerId: String?,
        apiBaseEndpoint: String,
        discoveredBaseUrls: List<String>,
        customBaseUrl: String
    ): List<String> {
        val candidates = LinkedHashSet<String>()
        val trimmed = rawValue.trim()

        // 1. Direct absolute URL if already present in JSON
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("//")
        ) {
            normalizeUrl(trimmed)?.let { candidates.add(it) }
            // Also try URL-encoding spaces if present
            if (trimmed.contains(" ")) {
                normalizeUrl(trimmed.replace(" ", "%20"))?.let { candidates.add(it) }
            }
            return candidates.toList()
        }

        val cleanPath = trimmed.replace("\\", "/").trimStart('/')
        val fileName = cleanPath.substringAfterLast('/')
        val encodedPath = encodePathSegments(cleanPath)
        val encodedFile = encodePathSegments(fileName)
        val queryPath = URLEncoder.encode(cleanPath, "UTF-8")
        val queryFile = URLEncoder.encode(fileName, "UTF-8")

        fun addBaseCombos(base: String) {
            val normBase = normalizeUrl(base)?.trimEnd('/') ?: return
            candidates.add("$normBase/$encodedPath")
            if (encodedFile != encodedPath) {
                candidates.add("$normBase/$encodedFile")
            }
            if (!normBase.endsWith("/uploads") && !cleanPath.startsWith("uploads/", ignoreCase = true)) {
                candidates.add("$normBase/uploads/$encodedPath")
                if (encodedFile != encodedPath) {
                    candidates.add("$normBase/uploads/$encodedFile")
                }
            }
        }

        // 2. User-provided or previously working custom base URL (highest priority!)
        if (customBaseUrl.isNotBlank()) {
            addBaseCombos(customBaseUrl)
        }

        // 3. Auto-discovered base URLs captured from live sjp.gos.pk WebView
        for (discovered in discoveredBaseUrls) {
            if (discovered.isNotBlank()) {
                addBaseCombos(discovered)
            }
        }

        // 4. Custom API host if user configured one different from default
        val configuredOrigin = extractOrigin(apiBaseEndpoint)
        if (!configuredOrigin.isNullOrBlank() && configuredOrigin != "https://usr-svc.sjp.gos.pk") {
            addBaseCombos(configuredOrigin)
        }

        // 5. Primary SJP Backend (usr-svc.sjp.gos.pk) static upload & folder routes
        val usrSvc = "https://usr-svc.sjp.gos.pk"
        candidates.add("$usrSvc/$encodedPath")
        candidates.add("$usrSvc/uploads/$encodedPath")
        if (encodedFile != encodedPath) {
            candidates.add("$usrSvc/uploads/$encodedFile")
        }
        candidates.add("$usrSvc/public/$encodedPath")
        candidates.add("$usrSvc/public/uploads/$encodedPath")
        candidates.add("$usrSvc/files/$encodedPath")
        candidates.add("$usrSvc/static/$encodedPath")
        candidates.add("$usrSvc/uploads/appointment-orders/$encodedFile")
        candidates.add("$usrSvc/uploads/appointmentOrders/$encodedFile")
        candidates.add("$usrSvc/uploads/appointment_orders/$encodedFile")
        candidates.add("$usrSvc/uploads/offers/$encodedFile")
        candidates.add("$usrSvc/uploads/documents/$encodedFile")
        candidates.add("$usrSvc/uploads/candidates/$encodedFile")

        // 6. Authenticated SJP API v2 download & signed-URL endpoints
        candidates.add("$usrSvc/api/v2/uploads/$encodedPath")
        candidates.add("$usrSvc/api/v2/files/$encodedPath")
        candidates.add("$usrSvc/api/v2/download/$encodedPath")
        candidates.add("$usrSvc/api/v2/download?file=$queryPath")
        candidates.add("$usrSvc/api/v2/download?key=$queryPath")
        candidates.add("$usrSvc/api/v2/files/download?key=$queryPath")
        candidates.add("$usrSvc/api/v2/files/download?file=$queryFile")
        candidates.add("$usrSvc/api/v2/s3/signed-url?key=$queryPath")
        candidates.add("$usrSvc/api/v2/candidates/application/download/$encodedFile")
        candidates.add("$usrSvc/api/v2/candidates/application/download?file=$queryPath")
        candidates.add("$usrSvc/api/v2/candidates/application/appointmentOrder/$applicationId")
        candidates.add("$usrSvc/api/v2/candidates/application/appointment-order/$applicationId")
        candidates.add("$usrSvc/api/v2/candidates/application/getAppointmentOrder/$applicationId")
        candidates.add("$usrSvc/api/v2/candidates/offer/appointmentOrder/$applicationId")
        if (!offerId.isNullOrBlank()) {
            candidates.add("$usrSvc/api/v2/candidates/offer/download/$offerId")
            candidates.add("$usrSvc/api/v2/offers/$offerId/appointment-order")
        }
        candidates.add("$usrSvc/api/v1/uploads/$encodedPath")
        candidates.add("$usrSvc/api/uploads/$encodedPath")
        candidates.add("$usrSvc/api/files/$encodedPath")

        // 7. Main Portal (sjp.gos.pk) & sibling SJP microservice hosts
        val siblingHosts = listOf(
            "https://sjp.gos.pk",
            "https://job-svc.sjp.gos.pk",
            "https://admin-svc.sjp.gos.pk",
            "https://adm-svc.sjp.gos.pk",
            "https://file-svc.sjp.gos.pk",
            "https://api.sjp.gos.pk",
            "https://cdn.sjp.gos.pk",
            "https://storage.sjp.gos.pk",
            "https://media.sjp.gos.pk"
        )
        for (host in siblingHosts) {
            candidates.add("$host/$encodedPath")
            if (!cleanPath.startsWith("uploads/", ignoreCase = true)) {
                candidates.add("$host/uploads/$encodedPath")
            }
            if (encodedFile != encodedPath) {
                candidates.add("$host/uploads/$encodedFile")
            }
        }

        return candidates.toList()
    }

    private fun isTrustedSjpHost(host: String): Boolean {
        val h = host.lowercase(Locale.US)
        return h == "sjp.gos.pk" || h.endsWith(".sjp.gos.pk")
    }

    private fun isValidPdfPayload(bytes: ByteArray, contentType: String?): Boolean {
        if (bytes.size < 64) return false
        // Check standard PDF magic bytes: "%PDF-" (0x25 0x50 0x44 0x46 0x2D)
        val header = String(bytes, 0, minOf(bytes.size, 32), Charsets.ISO_8859_1)
        if (header.contains("%PDF-")) return true

        val ct = contentType?.lowercase(Locale.US).orEmpty()
        val trimmedStart = header.trimStart().lowercase(Locale.US)
        val looksLikeHtmlOrJson = trimmedStart.startsWith("<!doctype") ||
            trimmedStart.startsWith("<html") ||
            trimmedStart.startsWith("{") ||
            trimmedStart.startsWith("[")
        return ct.contains("application/pdf") && !looksLikeHtmlOrJson
    }

    private fun extractUrlFromJsonResponse(jsonText: String): String? {
        return try {
            val obj = JSONObject(jsonText)
            findUrlInJson(obj)
        } catch (_: Exception) {
            null
        }
    }

    private fun findUrlInJson(obj: JSONObject): String? {
        val priorityKeys = listOf("url", "signedUrl", "signed_url", "downloadUrl", "download_url", "fileUrl", "file_url", "link", "pdfUrl", "data")
        for (k in priorityKeys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val v = obj.opt(k)
            when (v) {
                is String -> {
                    val trimmed = v.trim()
                    if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
                        return trimmed
                    }
                }
                is JSONObject -> {
                    val nested = findUrlInJson(v)
                    if (nested != null) return nested
                }
            }
        }
        return null
    }

    private fun extractBase64PdfFromJsonResponse(jsonText: String): ByteArray? {
        return try {
            val obj = JSONObject(jsonText)
            val candidateKeys = listOf("pdf", "file", "content", "base64", "data")
            for (k in candidateKeys) {
                val str = obj.optString(k, "").trim()
                if (str.startsWith("data:application/pdf") || str.startsWith("JVBERi0")) {
                    val decoded = Base64.decode(str.substringAfter("base64,", str), Base64.DEFAULT)
                    if (isValidPdfPayload(decoded, "application/pdf")) return decoded
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val withScheme = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        return withScheme.toHttpUrlOrNull()?.toString()
    }

    private fun extractOrigin(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        return "${parsed.scheme}://${parsed.host}"
    }

    private fun encodePathSegments(path: String): String {
        return path.split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
    }

    private fun inferBaseUrlForCaching(resolvedUrl: String, rawFieldValue: String): String? {
        val cleanField = rawFieldValue.trim().replace("\\", "/").trimStart('/')
        if (cleanField.startsWith("http://") || cleanField.startsWith("https://")) return null
        val cleanResolved = resolvedUrl.substringBefore("?").substringBefore("#")
        val encodedField = encodePathSegments(cleanField)
        val fileName = encodePathSegments(cleanField.substringAfterLast('/'))
        return when {
            cleanResolved.endsWith("/$encodedField") -> cleanResolved.removeSuffix("/$encodedField")
            cleanResolved.endsWith("/$fileName") -> cleanResolved.removeSuffix("/$fileName")
            else -> null
        }
    }

    private data class SavedPdfInfo(
        val fileName: String,
        val locationLabel: String,
        val shareableUri: Uri
    )

    private fun persistPdfBytes(
        context: Context,
        desiredFileName: String,
        bytes: ByteArray
    ): SavedPdfInfo {
        val safeName = sanitizeFileName(desiredFileName)

        // 1. Write shareable FileProvider copy in cacheDir/appointment_orders/ (wiped on SJP logout)
        val cacheFolder = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
        val cacheFile = File(cacheFolder, safeName)
        cacheFile.writeBytes(bytes)
        val providerUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        // 2. Also save to user Downloads via zero-permission Scoped Storage (Android 10..17)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SJP_Pulse")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val insertedUri = runCatching {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }.getOrNull()

            if (insertedUri != null) {
                runCatching {
                    resolver.openOutputStream(insertedUri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    val finalizeValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, finalizeValues, null, null)
                }
                return SavedPdfInfo(
                    fileName = safeName,
                    locationLabel = "Downloads/SJP_Pulse/$safeName",
                    shareableUri = providerUri
                )
            }
        } else {
            val extDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (extDir != null) {
                runCatching {
                    val outFile = File(extDir, safeName)
                    outFile.writeBytes(bytes)
                    return SavedPdfInfo(
                        fileName = safeName,
                        locationLabel = "App Downloads/$safeName",
                        shareableUri = providerUri
                    )
                }
            }
        }

        return SavedPdfInfo(
            fileName = safeName,
            locationLabel = "Saved ($safeName)",
            shareableUri = providerUri
        )
    }

    private fun buildSafeDownloadFileName(
        applicationId: String,
        jobTitle: String,
        doc: SjpDocumentRef
    ): String {
        val rawName = doc.displayFileName
        val slugTitle = jobTitle
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(28)
        val prefix = if (doc.isAppointmentOrder) "AppointmentOrder" else "SJP_Doc"
        val candidate = if (rawName.length > 6 && rawName != "appointment_order.pdf" && rawName != "sjp_document.pdf") {
            "${prefix}_${applicationId}_$rawName"
        } else {
            "${prefix}_${applicationId}_${slugTitle}.pdf"
        }
        return sanitizeFileName(candidate)
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(96)
            .ifBlank { "appointment_order.pdf" }
        return if (cleaned.lowercase(Locale.US).endsWith(".pdf")) cleaned else "$cleaned.pdf"
    }

    fun buildOpenPdfIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildSharePdfIntent(uri: Uri, title: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Appointment Order PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun clearCachedDocuments(context: Context) {
        runCatching {
            val cacheFolder = File(context.cacheDir, CACHE_SUBDIR)
            if (cacheFolder.exists()) {
                cacheFolder.deleteRecursively()
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_048_576L -> String.format(Locale.US, "%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
