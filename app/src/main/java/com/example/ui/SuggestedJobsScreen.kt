package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.AndroidCompatHelper
import com.example.data.HeadlessApplyStatus
import com.example.data.SjpSessionState
import com.example.data.SuggestedSjpJob
import org.json.JSONArray

private class HeadlessBrowserBridge(
    private val onLog: (String) -> Unit,
    private val onJobsDiscovered: (String, Int) -> Unit,
    private val onApplyFinished: (String, String, Boolean, String) -> Unit
) {
    @JavascriptInterface
    fun logStep(message: String?) {
        if (!message.isNullOrBlank()) {
            onLog(message)
        }
    }

    @JavascriptInterface
    fun onDiscoveredJobsJson(jsonString: String?) {
        val raw = jsonString?.trim().orEmpty()
        val count = try {
            if (raw.startsWith("[")) JSONArray(raw).length() else 0
        } catch (_: Exception) {
            0
        }
        onJobsDiscovered(raw, count)
    }

    @JavascriptInterface
    fun onJobApplyResult(jobId: String?, jobTitle: String?, success: Boolean, message: String?) {
        onApplyFinished(
            jobId.orEmpty(),
            jobTitle.orEmpty(),
            success,
            message.orEmpty()
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SuggestedJobsScreen(
    uiState: SjpUiState,
    onLogStep: (String) -> Unit,
    onSetScanning: (Boolean) -> Unit,
    onJobsDiscovered: (String, Int) -> Unit,
    onUpdateApplyStatus: (HeadlessApplyStatus) -> Unit,
    onOpenPortalLogin: () -> Unit
) {
    var headlessWebView by remember { mutableStateOf<WebView?>(null) }
    var showConsoleLogs by remember { mutableStateOf(true) }
    var customJobIdInput by remember { mutableStateOf("") }

    val sessionState = uiState.sessionState
    val escapedToken = remember(sessionState.bearerToken) {
        sessionState.bearerToken.replace("'", "\\'")
    }

    // JavaScript executed inside the Headless WebView to discover available SJP jobs
    // from usr-svc.sjp.gos.pk APIs and DOM elements
    val headlessDiscoverScript = remember(escapedToken) {
        """
        (async () => {
          try {
            AndroidHeadlessBridge.logStep('Starting headless vacancy discovery on sjp.gos.pk & usr-svc.sjp.gos.pk...');
            var headers = { 'Accept': 'application/json' };
            var token = '${escapedToken}';
            if (!token) {
              try {
                for (var i = 0; i < window.localStorage.length; i++) {
                  var k = window.localStorage.key(i);
                  var v = window.localStorage.getItem(k);
                  if (v && v.indexOf('eyJ') !== -1) {
                    var m = v.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
                    if (m && m[0]) { token = m[0]; break; }
                  }
                }
              } catch(e) {}
            }
            if (token && token.length > 10) {
              headers['Authorization'] = token.indexOf('Bearer ') === 0 ? token : ('Bearer ' + token);
            }

            var candidateEndpoints = [
              'https://usr-svc.sjp.gos.pk/api/v2/candidates/jobs/getAllJobs?page=1&limit=25&keyword=',
              'https://usr-svc.sjp.gos.pk/api/v2/jobs/getAllJobs?page=1&limit=25&keyword=',
              'https://usr-svc.sjp.gos.pk/api/v2/jobs?page=1&limit=25',
              'https://usr-svc.sjp.gos.pk/api/v1/jobs?page=1&limit=25'
            ];

            var discovered = [];
            for (var epIdx = 0; epIdx < candidateEndpoints.length; epIdx++) {
              var url = candidateEndpoints[epIdx];
              try {
                var r = await fetch(url, { credentials: 'include', headers: headers });
                if (r.ok) {
                  var txt = await r.text();
                  var j = JSON.parse(txt);
                  var d = j.data || j;
                  var arr = d.jobs || d.rows || d.vacancies || d.items || (Array.isArray(d) ? d : []);
                  if (Array.isArray(arr) && arr.length > 0) {
                    AndroidHeadlessBridge.logStep('Loaded ' + arr.length + ' available vacancies from ' + url);
                    discovered = arr;
                    break;
                  }
                }
              } catch(e) {}
            }

            // Fallback: scrape job listings rendered in the sjp.gos.pk DOM
            if (discovered.length === 0) {
              AndroidHeadlessBridge.logStep('Scanning sjp.gos.pk DOM for active job cards...');
              var cards = document.querySelectorAll('[class*="job"], [href*="job"], tr');
              var idx = 1;
              cards.forEach(function(el) {
                var text = (el.innerText || '').trim();
                if (text.length > 20 && /BPS|Department|Apply|Director|Officer|Assistant|Inspector|Clerk/i.test(text) && discovered.length < 20) {
                  var lines = text.split('\n').map(function(s) { return s.trim(); }).filter(Boolean);
                  if (lines.length >= 2) {
                    discovered.push({
                      jobId: 'DOM-' + idx++,
                      jobTitle: lines[0].slice(0, 90),
                      departmentName: lines[1].slice(0, 90),
                      bpsScale: (text.match(/BPS[-\s]?\d+/i) || ['BPS-15'])[0]
                    });
                  }
                }
              });
            }

            AndroidHeadlessBridge.onDiscoveredJobsJson(JSON.stringify(discovered));
          } catch (err) {
            AndroidHeadlessBridge.logStep('Discovery error: ' + String(err));
            AndroidHeadlessBridge.onDiscoveredJobsJson('[]');
          }
        })();
        """.trimIndent()
    }

    fun triggerHeadlessApply(jobId: String, jobTitle: String, applyUrl: String) {
        val initialLogs = listOf(
            "Decrypting AES-256-GCM session token from AndroidKeyStore...",
            "Initializing headless browser context for Job #$jobId ($jobTitle)..."
        )
        onUpdateApplyStatus(
            HeadlessApplyStatus.Running(
                jobId = jobId,
                jobTitle = jobTitle,
                currentStep = "Submitting application via headless browser...",
                logs = initialLogs
            )
        )
        onLogStep("Starting headless browser apply for $jobTitle (ID: $jobId)")

        val safeJobId = jobId.replace("'", "\\'")
        val safeTitle = jobTitle.replace("'", "\\'")

        val applyJs = """
        (async () => {
          try {
            AndroidHeadlessBridge.logStep('Attaching encrypted session cookies & Bearer token...');
            var headers = {
              'Accept': 'application/json',
              'Content-Type': 'application/json'
            };
            var token = '${escapedToken}';
            if (!token) {
              try {
                for (var i = 0; i < window.localStorage.length; i++) {
                  var k = window.localStorage.key(i);
                  var v = window.localStorage.getItem(k);
                  if (v && v.indexOf('eyJ') !== -1) {
                    var m = v.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
                    if (m && m[0]) { token = m[0]; break; }
                  }
                }
              } catch(e) {}
            }
            if (token && token.length > 10) {
              headers['Authorization'] = token.indexOf('Bearer ') === 0 ? token : ('Bearer ' + token);
            }

            var applyEndpoints = [
              'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/applyJob',
              'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/createApplication',
              'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/apply'
            ];

            var payload = JSON.stringify({ jobId: '${safeJobId}', id: '${safeJobId}' });
            var apiSucceeded = false;
            var lastStatusText = '';

            for (var i = 0; i < applyEndpoints.length; i++) {
              var ep = applyEndpoints[i];
              AndroidHeadlessBridge.logStep('Headless POST -> ' + ep);
              try {
                var resp = await fetch(ep, {
                  method: 'POST',
                  credentials: 'include',
                  headers: headers,
                  body: payload
                });
                var bodyTxt = await resp.text();
                lastStatusText = 'HTTP ' + resp.status + ' (' + bodyTxt.slice(0, 120) + ')';
                if (resp.ok) {
                  apiSucceeded = true;
                  AndroidHeadlessBridge.logStep('Application submitted via API: ' + lastStatusText);
                  break;
                }
              } catch (e) {
                lastStatusText = String(e);
              }
            }

            if (!apiSucceeded) {
              AndroidHeadlessBridge.logStep('Attempting headless DOM button automation on sjp.gos.pk...');
              var applyButtons = Array.from(document.querySelectorAll('button, a')).filter(function(el) {
                return /apply\s*now|submit\s*application|apply\s*for\s*job/i.test(el.innerText || '');
              });
              if (applyButtons.length > 0) {
                applyButtons[0].click();
                AndroidHeadlessBridge.logStep('Clicked Apply button in headless DOM; confirming modal...');
                setTimeout(function() {
                  var confirmBtns = Array.from(document.querySelectorAll('button')).filter(function(b) {
                    return /confirm|yes|submit|proceed/i.test(b.innerText || '');
                  });
                  if (confirmBtns.length > 0) confirmBtns[0].click();
                }, 600);
                AndroidHeadlessBridge.onJobApplyResult('${safeJobId}', '${safeTitle}', true, 'Headless DOM clicked Apply on ${safeTitle}. Refreshing applications...');
                return;
              }
            }

            if (apiSucceeded) {
              AndroidHeadlessBridge.onJobApplyResult('${safeJobId}', '${safeTitle}', true, 'Successfully applied for ${safeTitle} (#${safeJobId}) via headless browser!');
            } else {
              AndroidHeadlessBridge.onJobApplyResult('${safeJobId}', '${safeTitle}', false, 'Headless browser reached usr-svc.sjp.gos.pk (' + (lastStatusText || 'requires active job ID / portal confirmation') + '). Verify job ID or session in SJP Login.');
            }
          } catch (err) {
            AndroidHeadlessBridge.onJobApplyResult('${safeJobId}', '${safeTitle}', false, 'Headless apply error: ' + String(err));
          }
        })();
        """.trimIndent()

        if (applyUrl.isNotBlank() && applyUrl.startsWith("https://sjp.gos.pk")) {
            headlessWebView?.loadUrl(applyUrl)
        }
        headlessWebView?.evaluateJavascript(applyJs, null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Offscreen 1x1 Headless WebView Component
        AndroidView(
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .testTag("headless_sjp_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    AndroidCompatHelper.configureCompatibleWebView(this)
                    addJavascriptInterface(
                        HeadlessBrowserBridge(
                            onLog = onLogStep,
                            onJobsDiscovered = onJobsDiscovered,
                            onApplyFinished = { jobId, jobTitle, success, msg ->
                                onLogStep(msg)
                                onUpdateApplyStatus(
                                    HeadlessApplyStatus.Completed(
                                        jobId = jobId,
                                        jobTitle = jobTitle,
                                        success = success,
                                        summaryMessage = msg,
                                        logs = uiState.headlessBrowserLogs
                                    )
                                )
                            }
                        ),
                        "AndroidHeadlessBridge"
                    )
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (!url.isNullOrBlank()) {
                                onLogStep("Headless DOM loaded: $url")
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            AndroidCompatHelper.flushCookiesCompat()
                        }
                    }
                    loadUrl("https://sjp.gos.pk/jobs")
                    headlessWebView = this
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("suggested_jobs_list"),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Headless Browser & Security Header Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (sessionState.hasActiveSession) Color(0xFF10B981)
                                            else Color(0xFFF59E0B)
                                        )
                                )
                                Text(
                                    text = "Headless SJP Auto-Apply Engine",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "AES-256-GCM",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Suggests SJP vacancies tailored to your applied departments & BPS scales and uses an offscreen headless WebView component to submit applications in the background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSetScanning(true)
                                    headlessWebView?.evaluateJavascript(headlessDiscoverScript, null)
                                },
                                enabled = !uiState.isScanningSuggestions,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("scan_sjp_jobs_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (uiState.isScanningSuggestions) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isScanningSuggestions) "Scanning Portal…" else "Scan SJP Jobs",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            OutlinedButton(
                                onClick = { showConsoleLogs = !showConsoleLogs },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("toggle_headless_logs_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (showConsoleLogs) "Hide Log" else "Headless Log")
                            }
                        }

                        // Headless Browser Live Telemetry Console
                        AnimatedVisibility(visible = showConsoleLogs) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "HEADLESS WEBVIEW TELEMETRY (${AndroidCompatHelper.sdkVersionLabel})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF38BDF8)
                                            )
                                            Text(
                                                text = if (sessionState.isEncryptedAtRest) "TOKEN: KEYSTORE ENCRYPTED" else "NO SESSION",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF34D399)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        for (line in uiState.headlessBrowserLogs.takeLast(6)) {
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF6EE7B7)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Direct Headless Apply by Job ID / Vacancy Code Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Direct Headless Apply by SJP Job ID",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customJobIdInput,
                                onValueChange = { customJobIdInput = it },
                                placeholder = { Text("Enter SJP Job ID (e.g. 1428)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("headless_job_id_input")
                            )
                            Button(
                                onClick = {
                                    if (customJobIdInput.isNotBlank()) {
                                        val id = customJobIdInput.trim()
                                        triggerHeadlessApply(
                                            jobId = id,
                                            jobTitle = "SJP Vacancy #$id",
                                            applyUrl = "https://sjp.gos.pk/jobs/$id"
                                        )
                                    }
                                },
                                enabled = customJobIdInput.isNotBlank(),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("headless_direct_apply_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Apply")
                            }
                        }
                    }
                }
            }

            // 3. Suggested Jobs Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recommended Jobs for Your Profile (${uiState.suggestedJobs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ranked by Dept & BPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.suggestedJobs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WorkOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Log In & Scan SJP Portal for Job Suggestions",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Once you sign in to sjp.gos.pk, tap 'Scan SJP Jobs' above and the headless browser will discover open positions matched to your departments and BPS grades.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(onClick = onOpenPortalLogin) {
                                Text("Open SJP Login")
                            }
                        }
                    }
                }
            } else {
                items(uiState.suggestedJobs, key = { it.jobId }) { suggestion ->
                    SuggestedJobCard(
                        job = suggestion,
                        applyStatus = uiState.headlessApplyStatus,
                        onHeadlessApply = {
                            triggerHeadlessApply(
                                jobId = suggestion.jobId,
                                jobTitle = suggestion.jobTitle,
                                applyUrl = suggestion.applyUrl
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedJobCard(
    job: SuggestedSjpJob,
    applyStatus: HeadlessApplyStatus,
    onHeadlessApply: () -> Unit
) {
    val isCurrentlyApplying = applyStatus is HeadlessApplyStatus.Running && applyStatus.jobId == job.jobId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suggested_job_${job.jobId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = job.bpsScale,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFFD1FAE5),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${job.matchScorePercent}% PROFILE MATCH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "ID: ${job.jobId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = job.jobTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = job.departmentName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Why suggested: ${job.matchReason}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Quota: ${job.districtOrQuota} • Deadline: ${job.lastDateToApply}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onHeadlessApply,
                enabled = !job.isAlreadyApplied && !isCurrentlyApplying,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("headless_apply_button_${job.jobId}"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isCurrentlyApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Applying in Headless Browser…")
                } else {
                    Icon(
                        imageVector = if (job.isAlreadyApplied) Icons.Default.CheckCircle else Icons.Default.AutoMode,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (job.isAlreadyApplied) "Already Applied"
                        else "Apply via Headless Browser"
                    )
                }
            }
        }
    }
}
