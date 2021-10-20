package com.rakuten.tech.mobile.miniapp.display

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import androidx.annotation.VisibleForTesting
import androidx.webkit.WebViewAssetLoader
import com.rakuten.tech.mobile.miniapp.MiniAppScheme
import com.rakuten.tech.mobile.miniapp.navigator.ExternalResultHandler
import com.rakuten.tech.mobile.miniapp.navigator.MiniAppNavigator
import com.rakuten.tech.mobile.miniapp.permission.MiniAppCustomPermissionCache
import com.rakuten.tech.mobile.miniapp.permission.MiniAppCustomPermissionType

internal class MiniAppWebViewClient(
    private val context: Context,
    @VisibleForTesting internal val loader: WebViewAssetLoader?,
    private val miniAppNavigator: MiniAppNavigator,
    private val externalResultHandler: ExternalResultHandler,
    private val miniAppScheme: MiniAppScheme,
    private val customPermissionCache: MiniAppCustomPermissionCache,
    private val miniAppId: String
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val response = loader?.shouldInterceptRequest(request.url)
        interceptMimeType(response, request)
        return response
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        var shouldCancelLoading = super.shouldOverrideUrlLoading(view, request)
        if (request.url != null) {
            val requestUrl = request.url.toString()
            if (requestUrl.startsWith("tel:")) {
                miniAppScheme.openPhoneDialer(context, requestUrl)
                shouldCancelLoading = true
            } else if (requestUrl.startsWith("mailto:")) {
                miniAppScheme.openMailComposer(context, requestUrl)
                shouldCancelLoading = true
            } else if (!miniAppScheme.isMiniAppUrl(requestUrl)) {
                miniAppNavigator.openExternalUrl(requestUrl, externalResultHandler)
                shouldCancelLoading = true
            } else if (miniAppScheme.isBase64(requestUrl)) {
                if (customPermissionCache.hasPermission(
                        miniAppId,
                        MiniAppCustomPermissionType.FILE_DOWNLOAD
                    )
                ) {
                    miniAppNavigator.openExternalUrl(requestUrl, externalResultHandler)
                    shouldCancelLoading = true
                }
            }
        }
        return shouldCancelLoading
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.url != null && request.url.toString().startsWith(miniAppScheme.miniAppCustomScheme)) {
            loadWithCustomDomain(
                view,
                request.url.toString().replace(miniAppScheme.miniAppCustomScheme, miniAppScheme.miniAppCustomDomain)
            )
            return
        }
        super.onReceivedError(view, request, error)
    }

    @VisibleForTesting
    internal fun interceptMimeType(response: WebResourceResponse?, request: WebResourceRequest) {
        response?.let {
            if (request.url != null && request.url.toString().endsWith(".js", true))
                it.mimeType = "application/javascript"
        }
    }

    @Suppress("MagicNumber")
    @VisibleForTesting
    internal fun loadWithCustomDomain(view: WebView, requestUrl: String) {
        view.stopLoading()
        view.postDelayed(
            { view.loadUrl(requestUrl) },
            100
        )
    }
}
