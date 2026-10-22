package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SjpSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow

/**
 * Multi-page fetch script that retrieves:
 * 1) All pages of https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate
 * 2) Candidate education/profile from usr-svc.sjp.gos.pk
 * 3) Open job vacancies from usr-svc.sjp.gos.pk
 */
fun buildSjpFullBackgroundSyncJs(savedBearerToken: String = ""): String {
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

        // 1. Fetch all pages of Applied Jobs
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
              AndroidSjpBridge.onFetchError('HTTP ' + r.status + ': Waiting for valid SJP login session');
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

        // 2. Fetch Open SJP Jobs for Recommendation Engine
        try {
          var jobEndpoints = [
            'https://usr-svc.sjp.gos.pk/api/v2/jobs/getAllJobs?page=1&limit=30&keyword=',
            'https://usr-svc.sjp.gos.pk/api/v2/candidates/jobs/getAllJobs?page=1&limit=30&keyword=',
            'https://usr-svc.sjp.gos.pk/api/v2/jobs?page=1&limit=30'
          ];
          for (var je = 0; je < jobEndpoints.length; je++) {
            var jr = await fetch(jobEndpoints[je], { credentials: 'include', headers: headers });
            if (jr.ok) {
              var jt = await jr.text();
              if (jt && jt.indexOf('{') === 0) {
                AndroidSjpBridge.onOpenJobsPayload(jt);
                break;
              }
            }
          }
        } catch(e) {}

        // 3. Fetch Candidate Profile / Education for Eligibility Matching
        try {
          var profEndpoints = [
            'https://usr-svc.sjp.gos.pk/api/v2/candidates/profile',
            'https://usr-svc.sjp.gos.pk/api/v2/candidates/getCandidateProfile'
          ];
          for (var pe = 0; pe < profEndpoints.length; pe++) {
            var pr = await fetch(profEndpoints[pe], { credentials: 'include', headers: headers });
            if (pr.ok) {
              var pt = await pr.text();
              if (pt && pt.indexOf('{') === 0) {
                AndroidSjpBridge.onCandidateProfilePayload(pt);
                break;
              }
            }
          }
        } catch(e) {}
      } catch (err) {
        AndroidSjpBridge.onFetchError('Could not fetch SJP data: ' + String(err));
      }
    })();
    """.trimIndent()
}

/**
 * Headless DOM Login automation script:
 * Fills CNIC & Password inputs in the SJP SPA (using native HTMLInputElement value setter
 * so React/Angular state updates properly) and clicks the login submit button.
 */
fun buildHeadlessDomLoginJs(cnic: String, password: String): String {
    val safeCnic = cnic.replace("\\", "\\\\").replace("'", "\\'")
    val safePass = password.replace("\\", "\\\\").replace("'", "\\'")
    return """
    (function() {
      function setNativeValue(element, value) {
        if (!element) return;
        var valueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        var prototype = Object.getPrototypeOf(element);
        var prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value') ? Object.getOwnPropertyDescriptor(prototype, 'value').set : null;
        if (valueSetter && valueSetter !== prototypeValueSetter && prototypeValueSetter) {
          prototypeValueSetter.call(element, value);
        } else if (valueSetter) {
          valueSetter.call(element, value);
        } else {
          element.value = value;
        }
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
      }

      var attempts = 0;
      var timer = setInterval(function() {
        attempts++;
        var passInput = document.querySelector('input[type="password"]');
        var textInputs = Array.from(document.querySelectorAll('input:not([type="password"]):not([type="hidden"]):not([type="checkbox"])'));
        var userField = textInputs.find(function(el) {
          var attr = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '')).toLowerCase();
          return attr.indexOf('cnic') !== -1 || attr.indexOf('user') !== -1 || attr.indexOf('email') !== -1 || attr.indexOf('nic') !== -1;
        }) || textInputs[0];

        if (userField && passInput) {
          clearInterval(timer);
          setNativeValue(userField, '${safeCnic}');
          setNativeValue(passInput, '${safePass}');
          setTimeout(function() {
            var submitBtn = document.querySelector('button[type="submit"]') ||
              Array.from(document.querySelectorAll('button')).find(function(b) {
                var t = (b.innerText || b.textContent || '').toLowerCase();
                return t.indexOf('login') !== -1 || t.indexOf('sign in') !== -1 || t.indexOf('submit') !== -1;
              });
            if (submitBtn) {
              submitBtn.click();
            }
          }, 300);
        } else if (attempts > 15) {
          clearInterval(timer);
        }
      }, 500);
    })();
    """.trimIndent()
}

fun buildBrowserApplyJobJs(jobId: String, savedBearerToken: String = ""): String {
    val safeJobId = jobId.replace("'", "\\'")
    val safeToken = savedBearerToken.replace("'", "\\'")
    return """
    (async () => {
      try {
        var headers = {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        };
        var foundToken = '${safeToken}';
        if (foundToken && foundToken.length > 10) {
          headers['Authorization'] = foundToken.indexOf('Bearer ') === 0 ? foundToken : ('Bearer ' + foundToken);
        }
        var endpoints = [
          'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/apply',
          'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/applyJob',
          'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/create'
        ];
        for (var i = 0; i < endpoints.length; i++) {
          var r = await fetch(endpoints[i], {
            method: 'POST',
            credentials: 'include',
            headers: headers,
            body: JSON.stringify({ jobId: '${safeJobId}', id: '${safeJobId}' })
          });
          if (r.ok) break;
        }
      } catch(e) {}
    })();
    """.trimIndent()
}

class SjpWebJsBridge(
    private val onTokenCaptured: (String) -> Unit,
    private val onPayloadCaptured: (String) -> Unit,
    private val onOpenJobsCaptured: (String) -> Unit,
    private val onProfileCaptured: (String) -> Unit,
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
    fun onOpenJobsPayload(jsonString: String?) {
        if (!jsonString.isNullOrBlank() && (jsonString.trim().startsWith("{") || jsonString.trim().startsWith("["))) {
            onOpenJobsCaptured(jsonString)
        }
    }

    @JavascriptInterface
    fun onCandidateProfilePayload(jsonString: String?) {
        if (!jsonString.isNullOrBlank() && jsonString.trim().startsWith("{")) {
            onProfileCaptured(jsonString)
        }
    }

    @JavascriptInterface
    fun onFetchError(errorMsg: String?) {
        if (!errorMsg.isNullOrBlank()) {
            onErrorCaptured(errorMsg)
        }
    }
}

/**
 * Invisible, Android 16-safe Headless Background Browser Host mounted at the root of the app.
 * Automatically executes silent DOM login, multi-page getAllAppliedJobsByCandidate fetches,
 * open job discovery, and in-app job applications without requiring a visible browser UI.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HeadlessSjpBrowserHost(
    sessionState: SjpSessionState,
    browserCommands: SharedFlow<BackgroundBrowserCommand>,
    onSessionCaptured: (String?, String?) -> Unit,
    onPayloadCaptured: (String, String) -> Unit,
    onOpenJobsCaptured: (String) -> Unit,
    onProfileCaptured: (String) -> Unit,
    onFetchError: (String) -> Unit
) {
    // Delay WebView creation until after the first UI frame renders (Android 16 cold-start safety)
    var readyToMount by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingLoginCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        delay(650)
        readyToMount = true
    }

    LaunchedEffect(browserCommands, webViewRef) {
        browserCommands.collect { cmd ->
            val wv = webViewRef ?: return@collect
            runCatching {
                when (cmd) {
                    is BackgroundBrowserCommand.PerformHeadlessLogin -> {
                        pendingLoginCredentials = cmd.cnic to cmd.password
                        wv.loadUrl("https://sjp.gos.pk/login")
                        wv.evaluateJavascript(
                            buildHeadlessDomLoginJs(cmd.cnic, cmd.password),
                            null
                        )
                    }
                    is BackgroundBrowserCommand.FetchAllPortalData -> {
                        wv.evaluateJavascript(
                            buildSjpFullBackgroundSyncJs(cmd.bearerToken),
                            null
                        )
                    }
                    is BackgroundBrowserCommand.SubmitJobApplicationInBrowser -> {
                        wv.evaluateJavascript(
                            buildBrowserApplyJobJs(cmd.jobId, cmd.bearerToken),
                            null
                        )
                    }
                }
            }
        }
    }

    if (readyToMount) {
        AndroidView(
            modifier = Modifier.size(0.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    runCatching {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = false
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

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
                                    onPayloadCaptured(json, "SJP Background Engine")
                                },
                                onOpenJobsCaptured = onOpenJobsCaptured,
                                onProfileCaptured = onProfileCaptured,
                                onErrorCaptured = onFetchError
                            ),
                            "AndroidSjpBridge"
                        )

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                runCatching {
                                    val cm = CookieManager.getInstance()
                                    cm.flush()
                                    val combinedCookies = listOf(
                                        cm.getCookie("https://usr-svc.sjp.gos.pk").orEmpty(),
                                        cm.getCookie("https://sjp.gos.pk").orEmpty()
                                    ).filter { it.isNotBlank() }.joinToString("; ")

                                    if (combinedCookies.isNotBlank()) {
                                        onSessionCaptured(null, combinedCookies)
                                    }

                                    // Hook XHR/fetch Authorization headers and auto-trigger sync after login
                                    val hookScript = """
                                    (function() {
                                      if (!window.__sjpHeadlessHooked) {
                                        window.__sjpHeadlessHooked = true;
                                        var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
                                        XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
                                          if (header && header.toLowerCase() === 'authorization' && value) {
                                            AndroidSjpBridge.onAuthTokenFound(value);
                                            if (!window.__sjpSyncedAfterAuth) {
                                              window.__sjpSyncedAfterAuth = true;
                                              setTimeout(function() {
                                                ${buildSjpFullBackgroundSyncJs(sessionState.bearerToken)}
                                              }, 900);
                                            }
                                          }
                                          return origSetHeader.apply(this, arguments);
                                        };
                                      }
                                    })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(hookScript, null)

                                    pendingLoginCredentials?.let { (cnic, pass) ->
                                        view?.evaluateJavascript(buildHeadlessDomLoginJs(cnic, pass), null)
                                        pendingLoginCredentials = null
                                    }

                                    if (sessionState.hasActiveSession) {
                                        view?.evaluateJavascript(
                                            buildSjpFullBackgroundSyncJs(sessionState.bearerToken),
                                            null
                                        )
                                    }
                                }
                            }
                        }

                        loadUrl("https://sjp.gos.pk")
                    }
                    webViewRef = this
                }
            }
        )
    }
}

/**
 * 100% Native SJP Account & Login Screen.
 * Users enter their CNIC and Password directly in native Material 3 fields while the
 * headless background browser and API engine handle authentication and session persistence.
 */
@Composable
fun NativeLoginAndAccountScreen(
    uiState: SjpUiState,
    onLoginWithCredentials: (String, String, Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onPayloadCaptured: (String, String) -> Unit,
    onSaveConfig: (String, Int, Boolean, String) -> Unit,
    onClearSession: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val session = uiState.sessionState
    var cnicInput by remember(session.savedCnic) { mutableStateOf(session.savedCnic) }
    var passwordInput by remember(session.savedPassword) { mutableStateOf(session.savedPassword) }
    var rememberCredentials by remember(session.rememberCredentials) { mutableStateOf(session.rememberCredentials) }
    var passwordVisible by remember { mutableStateOf(false) }

    var showConfigDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Native Direct SJP Login Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (session.hasActiveSession) Color(0xFF10B981)
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (session.hasActiveSession) {
                                "SJP Session Connected & Persisted"
                            } else {
                                "Direct In-App SJP Login"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row {
                        IconButton(
                            onClick = { showImportJsonDialog = true },
                            modifier = Modifier.testTag("open_json_import_button")
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "Import JSON Payload")
                        }
                        IconButton(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier.testTag("open_polling_config_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Polling Settings")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sindh Job Portal Account",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Sign in directly with your CNIC and Password. The background engine authenticates with sjp.gos.pk automatically—no browser window required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = cnicInput,
                    onValueChange = { cnicInput = it },
                    label = { Text("CNIC Number (e.g. 41304-1234567-1)") },
                    leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("native_cnic_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("SJP Portal Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("native_password_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberCredentials,
                        onCheckedChange = { rememberCredentials = it }
                    )
                    Text(
                        text = "Remember credentials & keep me logged in across restarts",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                AnimatedVisibility(visible = !uiState.loginProgressMessage.isNullOrBlank()) {
                    uiState.loginProgressMessage?.let { stepMsg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stepMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onLoginWithCredentials(cnicInput, passwordInput, rememberCredentials)
                    },
                    enabled = !uiState.isLoggingIn && cnicInput.isNotBlank() && passwordInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("native_login_submit_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isLoggingIn) "Signing In & Syncing…" else "Sign In & Fetch SJP Data",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (session.hasActiveSession) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onSyncNow()
                            onNavigateToDashboard()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("run_browser_fetch_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync All SJP Applications Now")
                    }
                }
            }
        }

        // 2. Session Telemetry & Auto-Polling Status Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Background Sync & Session Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Endpoint: ${session.customEndpointUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Background Polling: Every ${session.pollingIntervalMinutes} minutes (${if (session.autoPollEnabled) "Enabled" else "Paused"})",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Last Status: ${session.lastPollMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showConfigDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Configure Polling")
                    }
                    if (session.hasActiveSession || session.hasSavedCredentials) {
                        TextButton(
                            onClick = onClearSession,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
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
            sessionState = session,
            onDismiss = { showConfigDialog = false },
            onSave = { token, interval, autoPoll, endpoint ->
                onSaveConfig(token, interval, autoPoll, endpoint)
                showConfigDialog = false
            },
            onClearSession = {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
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
                    placeholder = { Text("Auto-captured by background engine or paste JWT…") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

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
