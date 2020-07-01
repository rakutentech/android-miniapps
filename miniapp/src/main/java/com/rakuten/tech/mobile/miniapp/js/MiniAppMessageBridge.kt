package com.rakuten.tech.mobile.miniapp.js

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.rakuten.tech.mobile.miniapp.display.WebViewListener

@Suppress("TooGenericExceptionCaught", "SwallowedException")
/** Bridge interface for communicating with mini app. **/
abstract class MiniAppMessageBridge {
    private lateinit var webViewListener: WebViewListener

    /** Get provided id of mini app for any purpose. **/
    abstract fun getUniqueId(): String

    /** Post permission request from external. **/
    abstract fun requestPermission(
        callbackId: String,
        miniAppPermissionType: String,
        permissions: Array<String>
    )

    /** Handle the message from external. **/
    @JavascriptInterface
    fun postMessage(jsonStr: String) {
        val callbackObj = Gson().fromJson(jsonStr, CallbackObj::class.java)

        when (callbackObj.action) {
            ActionType.GET_UNIQUE_ID.action -> onGetUniqueId(callbackObj)
            ActionType.REQUEST_PERMISSION.action -> onRequestPermission(callbackObj)
        }
    }

    private fun onGetUniqueId(callbackObj: CallbackObj) {
        try {
            postValue(callbackObj.id, getUniqueId())
        } catch (e: Exception) {
            postError(callbackObj.id, "Cannot get unique id: ${e.message}")
        }
    }

    private fun onRequestPermission(callbackObj: CallbackObj) {
        try {
            val permissionParam = Gson().fromJson(callbackObj.param, Permission::class.java)
            requestPermission(
                callbackId = callbackObj.id,
                miniAppPermissionType = permissionParam.permission,
                permissions = MiniAppPermission.getPermissionRequest(permissionParam.permission))
        } catch (e: Exception) {
            postError(callbackObj.id, "Cannot request permission: ${e.message}")
        }
    }

    /** Inform the permission request result to MiniApp. **/
    fun onRequestPermissionsResult(callbackId: String, grantResult: Int) =
        postValue(callbackId, MiniAppPermission.getPermissionResult(grantResult))

    /** Return a value to mini app. **/
    internal fun postValue(callbackId: String, value: String) {
        webViewListener.runSuccessCallback(callbackId, value)
    }

    /** Emit an error to mini app. **/
    internal fun postError(callbackId: String, errorMessage: String) {
        webViewListener.runErrorCallback(callbackId, errorMessage)
    }

    internal fun setWebViewListener(webViewListener: WebViewListener) {
        this.webViewListener = webViewListener
    }
}
