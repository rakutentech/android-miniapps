package com.rakuten.tech.mobile.miniapp.display

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import java.io.BufferedReader

internal class MiniAppWebChromeClient(val context: Context) : WebChromeClient() {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    @VisibleForTesting
    internal val bridgeJs = try {
        val inputStream = context.assets.open("bridge.js")
        inputStream.bufferedReader().use(BufferedReader::readText)
    } catch (e: Exception) {
        null
    }

    override fun onReceivedTitle(webView: WebView, title: String?) {
        doInjection(webView)
        super.onReceivedTitle(webView, title)
    }

    @Suppress("FunctionMaxLength")
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        val isGranted = (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED)
        callback?.invoke(origin, isGranted, isGranted)
    }

    @VisibleForTesting
    internal fun doInjection(webView: WebView) {
        webView.evaluateJavascript(bridgeJs) {}
    }
}
