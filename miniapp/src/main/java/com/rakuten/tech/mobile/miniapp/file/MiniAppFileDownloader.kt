package com.rakuten.tech.mobile.miniapp.file

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.rakuten.tech.mobile.miniapp.errors.MiniAppDownloadFileError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The file downloader of a miniapp with `onStartFileDownload` function.
 * To start file download on the device.
 **/
interface MiniAppFileDownloader {
    /**
     * For downloading the files which has been invoked by miniapp.
     * @param fileName return the name of the file.
     * @param url return the download url of the file.
     * @param headers returns the header of the file if there is any.
     * @param onDownloadSuccess contains file name send from host app to miniapp.
     * @param onDownloadFailed contains custom error message send from host app.
     **/
    fun onStartFileDownload(
        fileName: String,
        url: String,
        headers: Map<String, String>,
        onDownloadSuccess: (String) -> Unit,
        onDownloadFailed: (MiniAppDownloadFileError) -> Unit
    )
}

/**
 * The default file downloader of a miniapp.
 * @param requestCode of file downloading using an intent inside sdk, which will also be used
 * to retrieve the Uri of the file by [Activity.onActivityResult] in the HostApp.
 **/
class MiniAppFileDownloaderDefault(var activity: Activity, var requestCode: Int) : MiniAppFileDownloader {
    private lateinit var fileName: String
    private lateinit var url: String
    private lateinit var headers: Map<String, String>
    private lateinit var onDownloadSuccess: (String) -> Unit
    private lateinit var onDownloadFailed: (MiniAppDownloadFileError) -> Unit
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var okHttpClient: OkHttpClient? = null

    /**
     * Retrieve the Uri of the file by [Activity.onActivityResult] in the HostApp.
     **/
    fun onReceivedResult(destinationUri: Uri) {
        val client = okHttpClient ?: OkHttpClient.Builder().build().apply { okHttpClient = this }
        var request = createRequest(url, headers)
        scope.launch {
            startDownloading(destinationUri, client, request)
        }
    }

    private fun startDownloading(destinationUri: Uri, client: OkHttpClient, request: Request) {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            response.body?.let { responseBody ->
                activity.contentResolver.openOutputStream(destinationUri)?.let { outputStream ->
                    outputStream.write(responseBody.bytes())
                    outputStream.close()
                    onDownloadSuccess.invoke(fileName)
                } ?: {
                    onDownloadFailed.invoke(MiniAppDownloadFileError.saveFailureError)
                    Log.e("Downloader", "Failed to download file: could not open OutputStream.")
                }
            } ?: run {
                onDownloadFailed.invoke(MiniAppDownloadFileError.downloadFailedError)
            }
        } else {
            onDownloadFailed.invoke(MiniAppDownloadFileError.custom(response.code.toString(), response.message))
        }
    }

    override fun onStartFileDownload(
        fileName: String,
        url: String,
        headers: Map<String, String>,
        onDownloadSuccess: (String) -> Unit,
        onDownloadFailed: (MiniAppDownloadFileError) -> Unit
    ) {
        this.fileName = fileName
        this.url = url
        this.headers = headers
        this.onDownloadSuccess = onDownloadSuccess
        this.onDownloadFailed = onDownloadFailed

        openCreateDocIntent(activity, fileName)
    }

    private fun openCreateDocIntent(activity: Activity, fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = getMimeType(fileName)
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        activity.startActivityForResult(intent, requestCode)
    }

    private fun getMimeType(fileName: String): String {
        val extension = if (fileName.contains('.'))
            fileName.split('.').last()
        else
            ""
        val mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return if (!mimetype.isNullOrBlank())
            mimetype
        else
            "text/plain"
    }

    private fun createRequest(url: String, headers: Map<String, String>): Request {
        val builder = Request.Builder()
        headers?.forEach { header ->
            builder.addHeader(header.key, header.value)
        }
        return builder.url(url).build()
    }
}
