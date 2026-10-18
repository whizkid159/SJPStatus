package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SjpSessionState
import kotlinx.coroutines.flow.SharedFlow

/**
 * Shared JavaScript snippet modeled directly on the user's Firefox bookmarklet:
 * Fetches https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate?page=p&limit=10&keyword=
 * across all pages (1..totalPages) with credentials: 'include' and any captured Authorization token,
 * combines all applications into one JSON payload, and sends it to AndroidSjpBridge.
 */
fun buildSjpMultiPageFetchJs(savedBearerToken: String = ""): String {
    val escapedToken = savedBearerToken.replace("'", "\\'")
    return """
    (async () => {
      try {
        var headers = { 'Accept': 'application/json' };
        var foundToken = '${escapedToken}';
        try {
          var tokenKeys = ['token', 'access_token', 'auth_token', 'jwt', 'id_token', 'candidateToken'];
          for (var i = 0; i < tokenKeys.length; i++) {
            var v = window.localStorage.getItem(tokenKeys[i]) || window.sessionStorage.getItem(tokenKeys[i]);
            if (v && v.length > 10 && v !== 'null' && v !== 'undefined') {
              foundToken = v.replace(/^"|"$/g, '');
              break;
            }
          }
          if (!foundToken) {
            for (var j = 0; j < window.localStorage.length; j++) {
              var k = window.localStorage.key(j);
              var raw = window.localStorage.getItem(k);
              if (raw && raw.indexOf('eyJ') !== -1) {
                var match = raw.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
                if (match && match[0]) {
                  foundToken = match[0];
                  break;
                }
              }
            }
          }
        } catch(e) {}

        if (foundToken && foundToken.length > 10) {
          AndroidSjpBridge.onAuthTokenFound(foundToken);
          headers['Authorization'] = foundToken.indexOf('Bearer ') === 0 ? foundToken : ('Bearer ' + foundToken);
        }

        var page = 1;
        var totalPages = 1;
        var totalCount = 0;
        var allApps = [];

        while (page <= totalPages && page <= 50) {
          var u = 'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate?page=' + page + '&limit=10&keyword=';
          var r = await fetch(u, { credentials: 'include', headers: headers });
          var t = await r.text();
          if (!r.ok) {
            if (page === 1) {
              AndroidSjpBridge.onFetchError('HTTP ' + r.status + ': Please make sure you are logged into sjp.gos.pk');
              return;
            }
            break;
          }
          var parsed = JSON.parse(t);
          var d = parsed.data || {};
          var apps = d.applications || [];
          totalPages = (d.pagination && d.pagination.totalPages) ? Number(d.pagination.totalPages) : 1;
          totalCount = (d.pagination && d.pagination.total) ? Number(d.pagination.total) : apps.length;
          for (var idx = 0; idx < apps.length; idx++) {
            allApps.push(apps[idx]);
          }
          page++;
        }

        var combinedPayload = JSON.stringify({
          data: {
            applications: allApps,
            pagination: {
              total: totalCount || allApps.length,
              totalPages: totalPages,
              page: 1
            }
          }
        });
        AndroidSjpBridge.onAppliedJobsPayload(combinedPayload);
      } catch (err) {
        AndroidSjpBridge.onFetchError('Could not fetch SJP data: ' + String(err));
      }
    })();
    """.trimIndent()
}

class SjpWebJsBridge(
    private val onTokenCaptured: (String) -> Unit,
    private val onPayloadCaptured: (String) -> Unit,
    private val onErrorCaptured: (String) -> Unit
) {
    @JavascriptInterface
    fun onAuthTokenFound(token: String?) {
        if (!token.isNullOrBlank() && token != "null" && token != "undefined") {
            onTokenCaptured(token)
        }
    }

    @JavascriptInterface
    fun onAppliedJobsPayload(jsonString: String?) {
        if (!jsonString.isNullOrBlank() && (jsonString.trim().startsWith("{") || jsonString.trim().startsWith("["))) {
            onPayloadCaptured(jsonString)
        }
    }

    @JavascriptInterface
    fun onFetchError(errorMsg: String?) {
        if (!errorMsg.isNullOrBlank()) {
            onErrorCaptured(errorMsg)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalWebViewScreen(
    sessionState: SjpSessionState,
    webViewFetchRequests: SharedFlow<Unit>,
    onSessionCaptured: (String?, String?) -> Unit,
    onPayloadCaptured: (String, String) -> Unit,
    onFetchError: (String) -> Unit,
    onSaveConfig: (String, Int, Boolean, String) -> Unit,
    onClearSession: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://sjp.gos.pk") }
    var isLoadingPage by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    val multiPageFetchScript = remember(sessionState.bearerToken) {
        buildSjpMultiPageFetchJs(sessionState.bearerToken)
    }

    // Listen for fetch triggers from ViewModel
    LaunchedEffect(webViewFetchRequests, webViewRef) {
        webViewFetchRequests.collect {
            webViewRef?.evaluateJavascript(multiPageFetchScript, null)
        }
    }

    // Injected hook that watches for any authenticated request to usr-svc.sjp.gos.pk
    // and automatically triggers the multi-page getAllAppliedJobsByCandidate fetch upon login!
    val autoInterceptorJs = remember(multiPageFetchScript) {
        """
        (function() {
          try {
            for (var j = 0; j < window.localStorage.length; j++) {
              var k = window.localStorage.key(j);
              var raw = window.localStorage.getItem(k);
              if (raw && raw.indexOf('eyJ') !== -1) {
                var m = raw.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
                if (m && m[0]) {
                  AndroidSjpBridge.onAuthTokenFound(m[0]);
                }
              }
            }
          } catch(e) {}

          if (!window.__sjpPulseHooked) {
            window.__sjpPulseHooked = true;

            var origOpen = XMLHttpRequest.prototype.open;
            var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
            XMLHttpRequest.prototype.open = function(method, url) {
              this.__sjpUrl = url || '';
              return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
              if (header && header.toLowerCase() === 'authorization' && value) {
                AndroidSjpBridge.onAuthTokenFound(value);
                if (!window.__sjpAutoFetchedOnAuth) {
                  window.__sjpAutoFetchedOnAuth = true;
                  setTimeout(function() {
                    $multiPageFetchScript
                  }, 800);
                }
              }
              return origSetHeader.apply(this, arguments);
            };

            var origFetch = window.fetch;
            if (origFetch) {
              window.fetch = function() {
                var args = arguments;
                try {
                  var opts = args[1] || {};
                  var hdrs = opts.headers || {};
                  var authVal = hdrs['Authorization'] || hdrs['authorization'];
                  if (authVal) {
                    AndroidSjpBridge.onAuthTokenFound(authVal);
                  }
                } catch(e) {}
                return origFetch.apply(this, args);
              };
            }
          }
        })();
        """.trimIndent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Control Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    if (sessionState.hasActiveSession) Color(0xFF10B981)
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (sessionState.hasActiveSession) {
                                    "SJP Session Active • Auto-Saved"
                                } else {
                                    "Log in to sjp.gos.pk below"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.testTag("webview_reload_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload SJP Portal"
                            )
                        }
                        IconButton(
                            onClick = { showImportJsonDialog = true },
                            modifier = Modifier.testTag("open_json_import_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Import JSON Payload"
                            )
                        }
                        IconButton(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier.testTag("open_polling_config_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Polling & Session Settings"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val cm = CookieManager.getInstance()
                        cm.flush()
                        val combinedCookies = listOf(
                            cm.getCookie("https://usr-svc.sjp.gos.pk").orEmpty(),
                            cm.getCookie("https://sjp.gos.pk").orEmpty()
                        ).filter { it.isNotBlank() }.joinToString("; ")
                        if (combinedCookies.isNotBlank()) {
                            onSessionCaptured(null, combinedCookies)
                        }
                        webViewRef?.evaluateJavascript(multiPageFetchScript, null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("run_browser_fetch_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Fetch My SJP Applications Now (All Pages)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (isLoadingPage) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Embedded SJP Portal WebView
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("sjp_portal_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

                    addJavascriptInterface(
                        SjpWebJsBridge(
                            onTokenCaptured = { token ->
                                val combinedCookies = listOf(
                                    cookieManager.getCookie("https://usr-svc.sjp.gos.pk").orEmpty(),
                                    cookieManager.getCookie("https://sjp.gos.pk").orEmpty()
                                ).filter { it.isNotBlank() }.joinToString("; ")
                                onSessionCaptured(token, combinedCookies)
                            },
                            onPayloadCaptured = { json ->
                                onPayloadCaptured(json, "SJP Live Fetch")
                                onNavigateToDashboard()
                            },
                            onErrorCaptured = { err ->
                                onFetchError(err)
                            }
                        ),
                        "AndroidSjpBridge"
                    )

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoadingPage = true
                            if (!url.isNullOrBlank()) currentUrl = url
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoadingPage = false
                            if (!url.isNullOrBlank()) currentUrl = url

                            val cm = CookieManager.getInstance()
                            cm.flush()
                            val combinedCookies = listOf(
                                cm.getCookie("https://usr-svc.sjp.gos.pk").orEmpty(),
                                cm.getCookie("https://sjp.gos.pk").orEmpty()
                            ).filter { it.isNotBlank() }.joinToString("; ")

                            if (combinedCookies.isNotBlank()) {
                                onSessionCaptured(null, combinedCookies)
                            }

                            view?.evaluateJavascript(autoInterceptorJs, null)
                            // Automatically attempt to fetch applications after page load if logged in
                            view?.evaluateJavascript(multiPageFetchScript, null)
                        }
                    }

                    loadUrl("https://sjp.gos.pk")
                    webViewRef = this
                }
            }
        )
    }

    if (showImportJsonDialog) {
        ManualJsonImportDialog(
            onDismiss = { showImportJsonDialog = false },
            onImportJson = { rawJson ->
                onPayloadCaptured(rawJson, "Manual JSON Import")
                showImportJsonDialog = false
                onNavigateToDashboard()
            }
        )
    }

    if (showConfigDialog) {
        SessionAndPollingConfigDialog(
            sessionState = sessionState,
            onDismiss = { showConfigDialog = false },
            onSave = { token, interval, autoPoll, endpoint ->
                onSaveConfig(token, interval, autoPoll, endpoint)
                showConfigDialog = false
            },
            onClearSession = {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                onClearSession()
                showConfigDialog = false
            }
        )
    }
}

@Composable
private fun ManualJsonImportDialog(
    onDismiss: () -> Unit,
    onImportJson: (String) -> Unit
) {
    var jsonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import SJP JSON Response") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste a raw JSON response from usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    placeholder = { Text("{\"data\": {\"applications\": [...]}}") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_json_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (jsonInput.isNotBlank()) onImportJson(jsonInput) },
                enabled = jsonInput.isNotBlank()
            ) {
                Text("Parse & Load")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SessionAndPollingConfigDialog(
    sessionState: SjpSessionState,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean, String) -> Unit,
    onClearSession: () -> Unit
) {
    var bearerInput by remember { mutableStateOf(sessionState.bearerToken) }
    var endpointInput by remember { mutableStateOf(sessionState.customEndpointUrl) }
    var intervalMinutes by remember { mutableIntStateOf(sessionState.pollingIntervalMinutes) }
    var autoPoll by remember { mutableStateOf(sessionState.autoPollEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Polling & Session Persistence") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Background Polling Interval:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (mins in listOf(15, 30, 60, 120)) {
                        FilterChip(
                            selected = intervalMinutes == mins,
                            onClick = { intervalMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = autoPoll,
                        onCheckedChange = { autoPoll = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Auto-poll in background",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { endpointInput = it },
                    label = { Text("SJP API Endpoint URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bearerInput,
                    onValueChange = { bearerInput = it },
                    label = { Text("Captured / Custom Bearer Token (Optional)") },
                    placeholder = { Text("Auto-captured from WebView or paste JWT…") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (sessionState.cookieHeader.isNotBlank()) {
                    Text(
                        text = "Persisted Cookies: ${sessionState.cookieHeader.take(60)}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                TextButton(
                    onClick = onClearSession,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Saved Portal Session & Cache")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(bearerInput, intervalMinutes, autoPoll, endpointInput)
                }
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
