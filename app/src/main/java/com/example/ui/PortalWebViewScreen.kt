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

private class SjpWebJsBridge(
    private val onTokenCaptured: (String) -> Unit,
    private val onPayloadCaptured: (String) -> Unit
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalWebViewScreen(
    sessionState: SjpSessionState,
    onSessionCaptured: (String?, String?) -> Unit,
    onPayloadCaptured: (String, String) -> Unit,
    onSaveConfig: (String, Int, Boolean, String) -> Unit,
    onLoadDiagnosticSample: () -> Unit,
    onClearSession: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://sjp.gos.pk") }
    var isLoadingPage by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    // JavaScript snippet injected on every sjp.gos.pk page load to auto-capture
    // localStorage/sessionStorage tokens AND hook fetch/XHR calls to getAllAppliedJobsByCandidate
    val autoInterceptorJs = remember {
        """
        (function() {
          try {
            var tokenKeys = ['token', 'access_token', 'auth_token', 'jwt', 'id_token', 'candidateToken', 'sjp_token'];
            for (var i = 0; i < tokenKeys.length; i++) {
              var k = tokenKeys[i];
              var v = window.localStorage.getItem(k) || window.sessionStorage.getItem(k);
              if (v && v.length > 10) {
                AndroidSjpBridge.onAuthTokenFound(v);
              }
            }
            for (var j = 0; j < window.localStorage.length; j++) {
              var keyName = window.localStorage.key(j);
              var rawVal = window.localStorage.getItem(keyName);
              if (rawVal && rawVal.indexOf('eyJ') !== -1) {
                AndroidSjpBridge.onAuthTokenFound(rawVal);
              }
            }
          } catch(e) {}

          if (!window.__sjpPulseHooked) {
            window.__sjpPulseHooked = true;
            var origFetch = window.fetch;
            if (origFetch) {
              window.fetch = function() {
                var args = arguments;
                var urlStr = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url ? args[0].url : '');
                return origFetch.apply(this, args).then(function(response) {
                  try {
                    if (urlStr.indexOf('getAllAppliedJobsByCandidate') !== -1) {
                      response.clone().text().then(function(txt) {
                        AndroidSjpBridge.onAppliedJobsPayload(txt);
                      });
                    }
                  } catch(e) {}
                  return response;
                });
              };
            }

            var origOpen = XMLHttpRequest.prototype.open;
            var origSend = XMLHttpRequest.prototype.send;
            var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
            XMLHttpRequest.prototype.open = function(method, url) {
              this.__sjpUrl = url || '';
              return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
              if (header && header.toLowerCase() === 'authorization' && value) {
                AndroidSjpBridge.onAuthTokenFound(value);
              }
              return origSetHeader.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
              this.addEventListener('load', function() {
                try {
                  if (this.__sjpUrl && this.__sjpUrl.indexOf('getAllAppliedJobsByCandidate') !== -1) {
                    AndroidSjpBridge.onAppliedJobsPayload(this.responseText);
                  }
                } catch(e) {}
              });
              return origSend.apply(this, arguments);
            };
          }
        })();
        """.trimIndent()
    }

    // Direct browser-context fetch script (identical to running JS inside Firefox console)
    val browserFetchJs = remember(sessionState.bearerToken) {
        """
        (function() {
          var headers = { 'Accept': 'application/json, text/plain, */*' };
          var foundToken = '${sessionState.bearerToken}';
          try {
            var tokenKeys = ['token', 'access_token', 'auth_token', 'jwt', 'candidateToken'];
            for (var i = 0; i < tokenKeys.length; i++) {
              var v = window.localStorage.getItem(tokenKeys[i]) || window.sessionStorage.getItem(tokenKeys[i]);
              if (v && v.length > 10) { foundToken = v.replace(/^"|"$/g, ''); break; }
            }
          } catch(e) {}
          if (foundToken && foundToken.length > 5) {
            headers['Authorization'] = foundToken.indexOf('Bearer ') === 0 ? foundToken : ('Bearer ' + foundToken);
          }
          fetch('/api/v2/candidates/application/getAllAppliedJobsByCandidate', {
            method: 'GET',
            credentials: 'include',
            headers: headers
          })
          .then(function(r) { return r.text(); })
          .then(function(txt) {
            AndroidSjpBridge.onAppliedJobsPayload(txt);
          })
          .catch(function(err) {});
        })();
        """.trimIndent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Session Persistence & Browser Fetch Control Bar
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
                                    "SJP Session Active & Auto-Persisted"
                                } else {
                                    "Sign in below to persist SJP session"
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
                                contentDescription = "Paste Firefox JSON Response"
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            webViewRef?.evaluateJavascript(browserFetchJs, null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("run_browser_fetch_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Run JS API Fetch Now",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onLoadDiagnosticSample,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("load_sample_data_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Load Demo JSON",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (isLoadingPage) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Embedded SJP Portal WebView with automatic CookieManager persistence
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
                                val cookies = cookieManager.getCookie("https://sjp.gos.pk")
                                onSessionCaptured(token, cookies)
                            },
                            onPayloadCaptured = { json ->
                                onPayloadCaptured(json, "Browser JS Fetch")
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

                            // Flush cookies to disk so session survives app and device restarts
                            val cookieManagerInstance = CookieManager.getInstance()
                            cookieManagerInstance.flush()
                            val cookies = cookieManagerInstance.getCookie("https://sjp.gos.pk")
                            if (!cookies.isNullOrBlank()) {
                                onSessionCaptured(null, cookies)
                            }

                            // Inject our XHR/Fetch/localStorage interceptor script
                            view?.evaluateJavascript(autoInterceptorJs, null)
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
        title = { Text("Import Firefox / SJP JSON Payload") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste the raw JSON output from api/v2/candidates/application/getAllAppliedJobsByCandidate to parse hidden statuses and trigger diff alerts:",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    placeholder = { Text("{\"data\": [{\"applicationId\": ...}]}") },
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
                Text("Parse & Track JSON")
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
                    Text("Clear Saved Portal Session & Cookies")
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
