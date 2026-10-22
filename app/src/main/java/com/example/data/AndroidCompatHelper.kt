package com.example.data

import android.app.Activity
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.view.WindowCompat

/**
 * Ensures consistent behavior across Android 7.0 (API 24) through Android 16 (API 36)
 * and Android 17 (API 36.1).
 */
object AndroidCompatHelper {

    val sdkVersionLabel: String
        get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /**
     * Configures edge-to-edge system bar appearance compatibly across Android 16, Android 17,
     * and all earlier versions down to API 24.
     */
    fun configureSystemBars(activity: Activity, isDarkTheme: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
    }

    /**
     * Configures a WebView (either interactive or headless) with cross-version settings
     * so cookies, DOM storage, and mixed content behave identically on Android 16 and 17.
     */
    fun configureCompatibleWebView(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            userAgentString =
                "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
    }

    fun flushCookiesCompat() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {
        }
    }

    fun requiresRuntimeNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
