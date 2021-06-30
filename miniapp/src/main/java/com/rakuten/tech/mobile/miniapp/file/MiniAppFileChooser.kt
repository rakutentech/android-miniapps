package com.rakuten.tech.mobile.miniapp.file

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.annotation.VisibleForTesting

/**
 * The file chooser of a miniapp with `onShowFileChooser` function.
 **/
interface MiniAppFileChooser {

    /**
     * For choosing the files which has been invoked by [WebChromeClient.onShowFileChooser]
     * inside the miniapp webview.
     * @param filePathCallback a callback to provide the array of file-paths to select.
     * @param fileChooserParams the parameters can be used to customize the options of file chooser.
     * @param context the Activity context can be used to start the intent to choose file.
     **/
    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        context: Context
    ): Boolean
}

/**
 * The default file chooser of a miniapp.
 * @param requestCode of file choosing using an intent inside sdk, which will also be used
 * to retrieve the data by [Activity.onActivityResult] in the HostApp.
 **/
class MiniAppFileChooserDefault(var requestCode: Int) : MiniAppFileChooser {

    internal var callback: ValueCallback<Array<Uri>>? = null

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "LongMethod")
    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        context: Context
    ): Boolean {
        try {
            callback = filePathCallback
            val intent = fileChooserParams?.createIntent()
            if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent?.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            // Uses Intent.EXTRA_MIME_TYPES to pass multiple mime types.
            fileChooserParams?.acceptTypes?.let { acceptTypes ->
                if (!acceptTypes.equals("") && acceptTypes.size > 1) {
                    // Accept all first.
                    intent?.type = "*/*"
                    val imageExtensionList = listOf(".jpg", ".jpeg", ".png")
                    // Transform extensions into acceptable MIME Type.
                    acceptTypes.forEachIndexed { index, type ->
                        if (type.startsWith(".") && imageExtensionList.contains(type)) {
                            // transform into valid MIME TYPE.
                            acceptTypes[index] = "image/${type.replace(".", "")}"
                        }
                        // transform jpg as jpeg because image/jpg is not a valid MIME TYPE.
                        if (acceptTypes[index].equals("image/jpg"))
                            acceptTypes[index] = "image/jpeg"
                    }
                    // filter mime types by Intent.EXTRA_MIME_TYPES.
                    intent?.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                }
            }
            (context as Activity).startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            resetCallback()
            Log.e(MiniAppFileChooser::class.java.simpleName, e.message.toString())
            return false
        }
        return true
    }

    /**
     * Receive the files from the HostApp.
     * @param intent The data after successfully retrieved by [Activity.onActivityResult] in the HostApp.
     */
    @Suppress("OptionalWhenBraces", "LongMethod")
    fun onReceivedFiles(intent: Intent) {
        val data = intent.data
        val clipData = intent.clipData
        when {
            data != null -> {
                callback?.onReceiveValue((arrayOf(data)))
            }
            clipData != null -> {
                val uriList = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    uriList.add(clipData.getItemAt(i).uri)
                }

                callback?.onReceiveValue((uriList.toTypedArray()))
            }
            else -> {
                callback?.onReceiveValue(null)
            }
        }
        resetCallback()
    }

    /**
     * Can be used when HostApp wants to cancel the file choosing operation.
     */
    fun onCancel() {
        callback?.onReceiveValue(null)
        resetCallback()
    }

    @VisibleForTesting
    internal fun resetCallback() {
        callback = null
    }
}
