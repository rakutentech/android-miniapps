package com.rakuten.tech.mobile.miniapp.js

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.rakuten.tech.mobile.miniapp.errors.MiniAppBridgeErrorModel
import com.rakuten.tech.mobile.miniapp.errors.MiniAppDownloadFileError
import com.rakuten.tech.mobile.miniapp.file.MiniAppFileDownloader
import com.rakuten.tech.mobile.miniapp.js.userinfo.UserInfoBridge

internal class MiniAppFileDownloadDispatcher {
    private lateinit var bridgeExecutor: MiniAppBridgeExecutor
    private lateinit var miniAppFileDownloader: MiniAppFileDownloader
    private lateinit var activity: Activity

    fun setBridgeExecutor(activity: Activity, bridgeExecutor: MiniAppBridgeExecutor) {
        this.activity = activity
        this.bridgeExecutor = bridgeExecutor
    }

    fun setFileDownloader(miniAppFileDownloader: MiniAppFileDownloader) {
        this.miniAppFileDownloader = miniAppFileDownloader
    }

    private fun <T> whenReady(callback: () -> T) {
        if (this::bridgeExecutor.isInitialized &&
            this::activity.isInitialized &&
            this::miniAppFileDownloader.isInitialized
        ) {
            callback.invoke()
        }
    }

    fun onFileDownload(callbackId: String, jsonStr: String) = whenReady {
        val callbackObj: FileDownloadCallbackObj? = createFileDownloadCallbackObj(jsonStr)
        if (callbackObj != null) {
            val onDownloadSuccess = { fileName: String ->
                bridgeExecutor.postValue(callbackId, fileName)
            }
            val onDownloadFailed = { error: MiniAppDownloadFileError ->
                bridgeExecutor.postError(callbackId, Gson().toJson(error))
            }
            miniAppFileDownloader.onStartFileDownload(
                callbackObj.param?.filename ?: "",
                callbackObj.param?.url ?: "",
                callbackObj.param?.headers ?: emptyMap(),
                onDownloadSuccess,
                onDownloadFailed
            )
        } else {
            bridgeExecutor.postError(callbackId, "$ERR_FILE_DOWNLOAD $ERR_WRONG_JSON_FORMAT")
        }
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    internal fun createFileDownloadCallbackObj(jsonStr: String): FileDownloadCallbackObj? = try {
        Gson().fromJson(jsonStr, FileDownloadCallbackObj::class.java)
    } catch (e: Exception) {
        null
    }

    @VisibleForTesting
    internal companion object {
        const val ERR_FILE_DOWNLOAD = "DOWNLOAD FAILED:"
        const val ERR_WRONG_JSON_FORMAT = "Can not parse file download json object"
    }

}