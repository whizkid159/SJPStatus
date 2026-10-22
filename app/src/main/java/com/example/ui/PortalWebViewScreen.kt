package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SjpSessionState
import kotlinx.coroutines.flow.SharedFlow

/**
 * Exact Firefox Bookmarklet Multi-Page Fetcher:
 * Uses the exact fetch call from the user's Firefox bookmarklet:
 *   fetch('https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate?page=' + p + '&limit=10&keyword=', {
 *     credentials: 'include',
 *     headers: { Accept: 'application/json' }
 *   })
 * First tries without extra headers (so CORS preflight is not triggered unnecessarily).
 * If HTTP 401/403 occurs and a Bearer token was captured from SJP's SPA, retries with Authorization header.
 * Loops through all pages (1..totalPages) and passes the full JSON to AndroidSjpBridge.
 */
fun buildExactBookmarkletSyncJs(savedBearerToken: String = ""): String {
    val escapedToken = savedBearerToken.replace("\\", "\\\\").replace("'", "\\'")
    return """
    (async () => {
      try {
        async function fetchSjpUrl(url, useAuthFallback) {
          var hdrs = { 'Accept': 'application/json' };
          if (useAuthFallback) {
            var tok = window.__sjpCapturedToken || '${escapedToken}';
            if (!tok) {
              try {
                for (var i = 0; i < window.localStorage.length; i++) {
                  var k = window.localStorage.key(i);
                  var v = window.localStorage.getItem(k) || '';
                  var m = v.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
                  if (m && m[0]) { tok = m[0]; break; }
                }
                if (!tok) {
                  for (var j = 0; j < window.sessionStorage.length; j++) {
                    var ks = window.sessionStorage.key(j);
                    var vs = window.sessionStorage.getItem(ks) || '';
                    var ms = vs.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
                    if (ms && ms[0]) { tok = ms[0]; break; }
                  }
                }
              } catch(e) {}
            }
            if (tok && tok.length > 10) {
              hdrs['Authorization'] = tok.indexOf('Bearer ') === 0 ? tok : ('Bearer ' + tok);
              AndroidSjpBridge.onAuthTokenFound(tok);
            }
          }
          return await fetch(url, {
            credentials: 'include',
            headers: hdrs
          });
        }

        var page = 1;
        var totalPages = 1;
        var totalCount = 0;
        var allApps = [];
        var useAuth = false;

        while (page <= totalPages && page <= 50) {
          var u = 'https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate?page=' + page + '&limit=10&keyword=';
          var r = await fetchSjpUrl(u, useAuth);
          if (!r.ok && page === 1 && !useAuth) {
            useAuth = true;
            r = await fetchSjpUrl(u, true);
          }
          var t = await r.text();
          if (!r.ok) {
            if (page === 1) {
              AndroidSjpBridge.onFetchError('HTTP ' + r.status + ': Please sign in on the SJP WebView first, then tap "Fetch SJP Data".');
              return;
            }
            break;
          }
          var j = JSON.parse(t);
          var d = j.data || {};
          var apps = d.applications || [];
          totalPages = (d.pagination && d.pagination.totalPages) ? Number(d.pagination.totalPages) : 1;
          totalCount = (d.pagination && d.pagination.total) ? Number(d.pagination.total) : apps.length;
          for (var idx = 0; idx < apps.length; idx++) {
            allApps.push(apps[idx]);
          }
          // Send immediate update on page 1 so UI populates right away
          if (page === 1 && totalPages > 1) {
            AndroidSjpBridge.onAppliedJobsPayload(t);
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

        // Also fetch Open SJP Jobs for "Jobs For You" Eligibility Matching
        try {
          var jobEndpoints = [
            'https://usr-svc.sjp.gos.pk/api/v2/jobs/getAllJobs?page=1&limit=30&keyword=',
            'https://usr-svc.sjp.gos.pk/api/v2/candidates/jobs/getAllJobs?page=1&limit=30&keyword=',
            'https://usr-svc.sjp.gos.pk/api/v2/jobs?page=1&limit=30'
          ];
          for (var je = 0; je < jobEndpoints.length; je++) {
            var jr = await fetchSjpUrl(jobEndpoints[je], useAuth);
            if (jr.ok) {
              var jt = await jr.text();
              if (jt && jt.indexOf('{') === 0) {
                AndroidSjpBridge.onOpenJobsPayload(jt);
                break;
              }
            }
          }
        } catch(e) {}

        // Also fetch Candidate Profile / Education for Eligibility Matching
        try {
          var profEndpoints = [
            'https://usr-svc.sjp.gos.pk/api/v2/candidates/profile',
            'https://usr-svc.sjp.gos.pk/api/v2/candidates/getCandidateProfile'
          ];
          for (var pe = 0; pe < profEndpoints.length; pe++) {
            var pr = await fetchSjpUrl(profEndpoints[pe], useAuth);
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
 * The user's exact Firefox Bookmarklet Visual Overlay (`__sjpviewer`),
 * enhanced so every page it loads is ALSO automatically synced into the native SJP Pulse app!
 */
fun buildFirefoxBookmarkletOverlayJs(): String {
    return """
    (async()=>{
      const ID='__sjpviewer';
      document.getElementById(ID)?.remove();
      const esc=s=>String(s??'').replace(/[&<>"]/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[m]));
      const fmt=s=>{try{return new Date(s).toLocaleString([],{year:'numeric',month:'short',day:'2-digit',hour:'2-digit',minute:'2-digit'})}catch{return s}};
      const col=s=>/REJECTED/.test(s)?'#c62828':/OFFERED|JOINED/.test(s)?'#16803c':/WAITING/.test(s)?'#b26a00':/SHORTLISTED|INTERVIEW/.test(s)?'#1757b8':'#444';
      let page=Number(new URL(location.href).searchParams.get('page'))||1;
      let root=document.createElement('div');
      root.id=ID;
      root.style='position:fixed;z-index:2147483647;inset:5px;background:#fff;color:#111;border:2px solid #333;border-radius:14px;padding:14px;overflow:auto;font:14px system-ui,sans-serif;box-shadow:0 5px 30px #0006';
      document.body.append(root);
      async function load(p){
        page=p;
        root.innerHTML='<b>Loading SJP page '+page+'...</b>';
        try{
          let u='https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate?page='+p+'&limit=10&keyword=';
          let hdrs={Accept:'application/json'};
          if(window.__sjpCapturedToken){
            hdrs['Authorization']=window.__sjpCapturedToken.indexOf('Bearer ')===0?window.__sjpCapturedToken:('Bearer '+window.__sjpCapturedToken);
          }
          let r=await fetch(u,{credentials:'include',headers:hdrs});
          let t=await r.text();
          if(!r.ok)throw Error('HTTP '+r.status+' '+t.slice(0,300));
          // Also pipe directly into native Android app database!
          try { AndroidSjpBridge.onAppliedJobsPayload(t); } catch(e) {}
          let j=JSON.parse(t),d=j.data||{},apps=d.applications||[],tp=d.pagination?.totalPages||1;
          let h='<div style="position:sticky;top:-14px;background:white;padding:8px 0;z-index:2"><div style="display:flex;gap:6px;align-items:center;flex-wrap:wrap"><b style="font-size:18px">SJP APPLICATION VIEWER</b><button id=x>Close</button><button id=pr>←</button><button id=nx>→</button><label>Page <input id=pg type=number min=1 max='+tp+' value='+page+' style="width:55px"></label><button id=go>Load</button><input id=search placeholder="Search jobs/departments..." style="flex:1;min-width:180px;padding:7px"></div><div style="color:#666;margin-top:7px">'+(d.pagination?.total||apps.length)+' applications · page '+page+'/'+tp+' (Synced to App!)</div></div><div id=list>';
          for(let z of apps){
            let j=z.Job||{},hist=z.ApplicationStatuses||[];
            h+='<section class="app" data-search="'+esc((j.jobTitle||'')+' '+(j.Department?.departmentName||'')+' '+(z.applicationStatus||''))+'" style="border:1px solid #ccc;border-radius:12px;padding:12px;margin:12px 0"><h2 style="margin:0 0 3px;font-size:19px">'+esc(j.jobTitle||'Unknown job')+'</h2><b>'+esc(j.Department?.departmentName||'Unknown department')+'</b><table style="width:100%;margin-top:9px;border-collapse:collapse"><tr><td><b>Application ID</b></td><td>'+esc(z.applicationId)+'</td></tr><tr><td><b>Current status</b></td><td><b style="color:'+col(z.applicationStatus)+'">'+esc(z.applicationStatus)+'</b></td></tr><tr><td><b>Job offered</b></td><td>'+(z.jobOffered?'YES':'NO')+'</td></tr><tr><td><b>Interview</b></td><td>'+esc(j.jobInterviewStatus||'—')+'</td></tr><tr><td><b>Offer record</b></td><td>'+(z.Offer?'PRESENT':'NONE')+'</td></tr><tr><td><b>Rejection reason</b></td><td>'+esc(z.reasonForRejected||'—')+'</td></tr></table><h3 style="margin:14px 0 6px">STATUS HISTORY</h3>';
            for(let x of hist)h+='<div style="padding:5px 0;border-bottom:1px solid #eee"><span style="display:inline-block;min-width:145px;color:#666">'+esc(fmt(x.changedAt))+'</span><b style="color:'+col(x.status)+'">'+esc(x.status)+'</b></div>';
            h+='</section>';
          }
          h+='</div>';
          root.innerHTML=h;
          root.querySelector('#x').onclick=()=>root.remove();
          root.querySelector('#go').onclick=()=>load(Math.max(1,Math.min(tp,+root.querySelector('#pg').value||1)));
          root.querySelector('#pr').onclick=()=>page>1&&load(page-1);
          root.querySelector('#nx').onclick=()=>page<tp&&load(page+1);
          root.querySelector('#search').oninput=e=>{
            let q=e.target.value.toLowerCase();
            root.querySelectorAll('.app').forEach(a=>a.style.display=a.dataset.search.toLowerCase().includes(q)?'block':'none');
          };
        }catch(e){
          root.innerHTML='<b>Could not fetch SJP data</b><pre style="white-space:pre-wrap">'+esc(e)+'</pre><button id=x>Close</button>';
          root.querySelector('#x').onclick=()=>root.remove();
        }
      }
      load(page);
    })();
    """.trimIndent()
}

/**
 * Passive Network Interceptor injected into the WebView on every page load:
 * 1) Passively listens to all XHR and window.fetch calls made by the SJP portal SPA itself.
 * 2) Captures any Authorization Bearer token sent by the SJP frontend.
 * 3) Automatically captures any JSON responses from usr-svc.sjp.gos.pk (applied jobs, profile, open jobs)
 *    and triggers full multi-page sync as soon as the user logs in!
 */
fun buildPassivePortalNetworkInterceptorJs(): String {
    return """
    (function() {
      if (window.__sjpPassiveInterceptorInstalled) return;
      window.__sjpPassiveInterceptorInstalled = true;

      function inspectUrlAndBody(url, text) {
        if (!url || !text) return;
        try {
          if (url.indexOf('getAllAppliedJobsByCandidate') !== -1 && text.indexOf('{') === 0) {
            AndroidSjpBridge.onAppliedJobsPayload(text);
          } else if ((url.indexOf('/profile') !== -1 || url.indexOf('CandidateProfile') !== -1) && text.indexOf('{') === 0) {
            AndroidSjpBridge.onCandidateProfilePayload(text);
          } else if ((url.indexOf('getAllJobs') !== -1 || url.indexOf('/jobs') !== -1) && text.indexOf('getAllAppliedJobs') === -1 && text.indexOf('{') === 0) {
            AndroidSjpBridge.onOpenJobsPayload(text);
          } else if (url.indexOf('login') !== -1 || url.indexOf('signin') !== -1 || url.indexOf('auth') !== -1) {
            if (text.indexOf('eyJ') !== -1) {
              var m = text.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
              if (m && m[0]) {
                window.__sjpCapturedToken = m[0];
                AndroidSjpBridge.onAuthTokenFound(m[0]);
              }
            }
            // Automatically fetch applied jobs 1.5s after login response!
            setTimeout(function() {
              ${buildExactBookmarkletSyncJs()}
            }, 1500);
          }
        } catch(e) {}
      }

      // Hook XMLHttpRequest
      var origOpen = XMLHttpRequest.prototype.open;
      var origSetHeader = XMLHttpRequest.prototype.setRequestHeader;
      var origSend = XMLHttpRequest.prototype.send;

      XMLHttpRequest.prototype.open = function(method, url) {
        this.__sjpUrl = url ? String(url) : '';
        return origOpen.apply(this, arguments);
      };

      XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
        if (header && header.toLowerCase() === 'authorization' && value) {
          window.__sjpCapturedToken = value;
          AndroidSjpBridge.onAuthTokenFound(value);
        }
        return origSetHeader.apply(this, arguments);
      };

      XMLHttpRequest.prototype.send = function() {
        var self = this;
        this.addEventListener('load', function() {
          try {
            if (self.status >= 200 && self.status < 300 && self.responseText) {
              inspectUrlAndBody(self.__sjpUrl, self.responseText);
            }
          } catch(e) {}
        });
        return origSend.apply(this, arguments);
      };

      // Hook window.fetch
      var origFetch = window.fetch;
      if (origFetch) {
        window.fetch = async function(input, init) {
          var url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
          try {
            if (init && init.headers) {
              var authVal = init.headers['Authorization'] || init.headers['authorization'];
              if (authVal) {
                window.__sjpCapturedToken = authVal;
                AndroidSjpBridge.onAuthTokenFound(authVal);
              }
            }
          } catch(e) {}
          var response = await origFetch.apply(this, arguments);
          try {
            if (url && url.indexOf('sjp.gos.pk') !== -1) {
              var clone = response.clone();
              clone.text().then(function(bodyText) {
                inspectUrlAndBody(url, bodyText);
              }).catch(function() {});
            }
          } catch(e) {}
          return response;
        };
      }
    })();
    """.trimIndent()
}

/**
 * Optional 1-tap auto-fill helper for the visible WebView login form.
 */
fun buildAutoFillLoginInputsJs(cnic: String, password: String): String {
    val safeCnic = cnic.replace("\\", "\\\\").replace("'", "\\'")
    val safePass = password.replace("\\", "\\\\").replace("'", "\\'")
    return """
    (function() {
      function setNativeValue(element, value) {
        if (!element) return;
        var valueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
        var prototype = Object.getPrototypeOf(element);
        var prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;
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

      var passInput = document.querySelector('input[type="password"]');
      var textInputs = Array.from(document.querySelectorAll('input:not([type="password"]):not([type="hidden"]):not([type="checkbox"])'));
      var userField = textInputs.find(function(el) {
        var attr = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '')).toLowerCase();
        return attr.indexOf('cnic') !== -1 || attr.indexOf('user') !== -1 || attr.indexOf('email') !== -1 || attr.indexOf('nic') !== -1;
      }) || textInputs[0];

      if (userField) setNativeValue(userField, '${safeCnic}');
      if (passInput) setNativeValue(passInput, '${safePass}');
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
        var tok = window.__sjpCapturedToken || '${safeToken}';
        if (tok && tok.length > 10) {
          headers['Authorization'] = tok.indexOf('Bearer ') === 0 ? tok : ('Bearer ' + tok);
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
          if (r.ok) {
            setTimeout(function() {
              ${buildExactBookmarkletSyncJs(savedBearerToken)}
            }, 800);
            break;
          }
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
 * Interactive Portal WebView Screen.
 * Kept alive in the root layout so the user's logged-in WebView session is NEVER lost
 * when switching between tabs.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalWebViewScreen(
    uiState: SjpUiState,
    browserCommands: SharedFlow<BackgroundBrowserCommand>,
    onSessionCaptured: (String?, String?) -> Unit,
    onPayloadCaptured: (String, String) -> Unit,
    onOpenJobsCaptured: (String) -> Unit,
    onProfileCaptured: (String) -> Unit,
    onFetchError: (String) -> Unit,
    onSaveCredentials: (String, String, Boolean) -> Unit,
    onSaveConfig: (String, Int, Boolean, String) -> Unit,
    onClearSession: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = uiState.sessionState
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://sjp.gos.pk") }
    var isPageLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var showAutoFillDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    // Listen for background sync or apply commands from ViewModel (works even while user is on other tabs!)
    LaunchedEffect(browserCommands, webViewRef) {
        browserCommands.collect { cmd ->
            val wv = webViewRef ?: return@collect
            runCatching {
                when (cmd) {
                    is BackgroundBrowserCommand.PerformHeadlessLogin -> {
                        wv.evaluateJavascript(
                            buildAutoFillLoginInputsJs(cmd.cnic, cmd.password),
                            null
                        )
                    }
                    is BackgroundBrowserCommand.FetchAllPortalData -> {
                        wv.evaluateJavascript(
                            buildExactBookmarkletSyncJs(cmd.bearerToken),
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

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Interactive WebView Control Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Status indicator, current URL, and Primary "Fetch SJP Data" CTA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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
                                    if (uiState.totalCount > 0) Color(0xFF10B981)
                                    else Color(0xFFF59E0B)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (uiState.totalCount > 0) {
                                    "SJP Connected (${uiState.totalCount} Apps Synced)"
                                } else {
                                    "Log in to SJP below, then tap Fetch Data"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            webViewRef?.evaluateJavascript(
                                buildExactBookmarkletSyncJs(session.bearerToken),
                                null
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("fetch_sjp_data_webview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Fetch SJP Data",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isSyncing) "Fetching…" else "Fetch SJP Data",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Row 2: Browser Navigation + Bookmarklet Viewer + Quick Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Portal",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Run exact Firefox Bookmarklet Viewer inside WebView
                    AssistChip(
                        onClick = {
                            webViewRef?.evaluateJavascript(
                                buildFirefoxBookmarkletOverlayJs(),
                                null
                            )
                        },
                        label = { Text("Bookmarklet Viewer") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("run_bookmarklet_overlay_button")
                    )

                    // Quick Auto-Fill CNIC/Password into WebView login inputs
                    AssistChip(
                        onClick = {
                            if (session.hasSavedCredentials) {
                                webViewRef?.evaluateJavascript(
                                    buildAutoFillLoginInputsJs(session.savedCnic, session.savedPassword),
                                    null
                                )
                            } else {
                                showAutoFillDialog = true
                            }
                        },
                        label = {
                            Text(if (session.hasSavedCredentials) "Auto-Fill Login" else "Save Login for Auto-Fill")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    AssistChip(
                        onClick = { webViewRef?.loadUrl("https://sjp.gos.pk") },
                        label = { Text("SJP Home") }
                    )

                    IconButton(
                        onClick = { showAutoFillDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageAccounts,
                            contentDescription = "Edit Saved Credentials",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { showImportJsonDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("open_json_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Import JSON Payload",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("open_polling_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Polling Settings",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Feedback / Sync banner inside the Portal screen so the user sees immediate confirmation
                AnimatedVisibility(visible = !uiState.bannerFeedback.isNullOrBlank()) {
                    uiState.bannerFeedback?.let { feedback ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = feedback,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.totalCount > 0) {
                                    TextButton(
                                        onClick = onNavigateToDashboard,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("View Applied (${uiState.totalCount})")
                                    }
                                }
                            }
                        }
                    }
                }

                if (isPageLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Full Interactive SJP Portal WebView
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("sjp_portal_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    runCatching {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        // Clean mobile Chrome UA (strip "; wv" so SJP treats it as standard mobile Chrome)
                        val defaultUa = settings.userAgentString ?: ""
                        if (defaultUa.isNotBlank()) {
                            settings.userAgentString = defaultUa.replace("; wv", "")
                        }

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
                                    val combinedCookies = listOf(
                                        cookieManager.getCookie("https://usr-svc.sjp.gos.pk").orEmpty(),
                                        cookieManager.getCookie("https://sjp.gos.pk").orEmpty()
                                    ).filter { it.isNotBlank() }.joinToString("; ")
                                    onSessionCaptured(null, combinedCookies)
                                    onPayloadCaptured(json, "SJP WebView Sync")
                                },
                                onOpenJobsCaptured = onOpenJobsCaptured,
                                onProfileCaptured = onProfileCaptured,
                                onErrorCaptured = onFetchError
                            ),
                            "AndroidSjpBridge"
                        )

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                }
                                // Install passive XHR/fetch interceptor as early as possible
                                view?.evaluateJavascript(buildPassivePortalNetworkInterceptorJs(), null)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                }

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

                                    // Re-ensure passive XHR/fetch interceptor is active
                                    view?.evaluateJavascript(buildPassivePortalNetworkInterceptorJs(), null)

                                    // If user has saved auto-fill credentials and is on login page, auto-fill inputs gently
                                    if (session.rememberCredentials && session.hasSavedCredentials) {
                                        view?.evaluateJavascript(
                                            buildAutoFillLoginInputsJs(session.savedCnic, session.savedPassword),
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
            },
            update = { wv ->
                canGoBack = wv.canGoBack()
                canGoForward = wv.canGoForward()
            }
        )
    }

    if (showAutoFillDialog) {
        AutoFillCredentialsDialog(
            initialCnic = session.savedCnic,
            initialPassword = session.savedPassword,
            initialRemember = session.rememberCredentials,
            onDismiss = { showAutoFillDialog = false },
            onSaveAndFill = { cnic, pass, remember ->
                onSaveCredentials(cnic, pass, remember)
                webViewRef?.evaluateJavascript(
                    buildAutoFillLoginInputsJs(cnic, pass),
                    null
                )
                showAutoFillDialog = false
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
private fun AutoFillCredentialsDialog(
    initialCnic: String,
    initialPassword: String,
    initialRemember: Boolean,
    onDismiss: () -> Unit,
    onSaveAndFill: (String, String, Boolean) -> Unit
) {
    var cnic by remember { mutableStateOf(initialCnic) }
    var password by remember { mutableStateOf(initialPassword) }
    var rememberMe by remember { mutableStateOf(initialRemember) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick WebView Auto-Fill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Optionally save your CNIC and password to auto-fill the SJP WebView login fields with one tap:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it },
                    label = { Text("CNIC Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it }
                    )
                    Text(
                        text = "Auto-fill login page when opened",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveAndFill(cnic, password, rememberMe) },
                enabled = cnic.isNotBlank()
            ) {
                Text("Save & Fill WebView")
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
                    placeholder = { Text("Auto-captured by WebView or paste JWT…") },
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
