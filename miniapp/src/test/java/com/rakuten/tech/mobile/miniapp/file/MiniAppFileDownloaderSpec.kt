package com.rakuten.tech.mobile.miniapp.file

import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.rakuten.tech.mobile.miniapp.TEST_CALLBACK_ID
import com.rakuten.tech.mobile.miniapp.TestActivity
import com.rakuten.tech.mobile.miniapp.display.WebViewListener
import com.rakuten.tech.mobile.miniapp.js.*
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.When
import org.amshove.kluent.any
import org.amshove.kluent.calling
import org.amshove.kluent.itReturns
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.File

class MiniAppFileDownloaderSpec {
    private val webViewListener: WebViewListener = mock()
    private val bridgeExecutor = Mockito.spy(MiniAppBridgeExecutor(webViewListener))
    private val activity = TestActivity()
    private val TEST_FILENAME = "test.jpg"
    private val TEST_MIME = "image/jpg"
    private val TEST_FILE_PATH = "com/example/test"
    private val TEST_FILE_URL = "https://sample/com/test.jpg"
    private val TEST_HEADER_OBJECT = DownloadFileHeaderObj(null)
    private val fileDownloadCallbackObj = FileDownloadCallbackObj(
        action = ActionType.FILE_DOWNLOAD.action,
        param = FileDownloadParams(TEST_FILENAME, TEST_FILE_URL, TEST_HEADER_OBJECT),
        id = TEST_CALLBACK_ID)
    private val fileDownloadJsonStr = Gson().toJson(fileDownloadCallbackObj)
    private val fileDownloader = Mockito.spy(MiniAppFileDownloader::class.java)
    private val mockMimeTypeMap: MimeTypeMap = mock()

    @Before
    fun setup() {
        fileDownloader.activity = activity
        fileDownloader.bridgeExecutor = bridgeExecutor

        When calling fileDownloader.createFileDirectory("test") itReturns mock()
    }

    @Test
    fun `postError should be called when callbackObject is null`() {
        val errorMsg = "DOWNLOAD FAILED: Can not parse file download json object"
        When calling fileDownloader.createFileDownloadCallbackObj(fileDownloadJsonStr) itReturns null
        fileDownloader.onFileDownload(TEST_CALLBACK_ID, fileDownloadJsonStr)
        verify(bridgeExecutor).postError(TEST_CALLBACK_ID, errorMsg)
    }

    @Test
    fun `onStartFileDownload should be called when correct jsonStr`() = runBlockingTest {
        fileDownloader.onFileDownload(TEST_CALLBACK_ID, fileDownloadJsonStr)
        verify(fileDownloader).onStartFileDownload(fileDownloadCallbackObj)
    }

    @Test
    fun `startDownloading should be called when onStartFileDownload get correct callback`() = runBlockingTest {
        fileDownloader.onStartFileDownload(fileDownloadCallbackObj)
        verify(fileDownloader).startDownloading(
            TEST_CALLBACK_ID,
            TEST_FILENAME,
            TEST_FILE_URL,
            TEST_HEADER_OBJECT
        )
    }

    @Test
    fun `postValue should be called when download successful`() = runBlockingTest {
        val mockServer = MockWebServer()
        mockServer.enqueue(MockResponse().setBody(""))
        mockServer.start()
        val url: String = mockServer.url("/sample/com/test.jpg").toString()

        fileDownloader.mimeTypeMap = mockMimeTypeMap
        When calling mockMimeTypeMap.getMimeTypeFromExtension(".jpg") itReturns TEST_MIME
        When calling fileDownloader.createFileDirectory(TEST_FILENAME) itReturns File(TEST_FILE_PATH)
        Mockito.doNothing().`when`(fileDownloader).openShareIntent("image/jpg", File(TEST_FILE_PATH))

        fileDownloader.startDownloading(
            TEST_CALLBACK_ID,
            TEST_FILENAME,
            url,
            TEST_HEADER_OBJECT
        )

        verify(fileDownloader).writeInputStreamToFile(any(), any())
        verify(fileDownloader).openShareIntent(any(), any())
        verify(bridgeExecutor).postValue(TEST_CALLBACK_ID, TEST_FILENAME)

        mockServer.shutdown()
    }

    @Test
    fun `postError should be called when download is not successful`() = runBlockingTest {
        val errorMsg = "DOWNLOAD FAILED: 404"
        val mockServer = MockWebServer()
        mockServer.enqueue(MockResponse().setResponseCode(404))
        mockServer.start()
        val url: String = mockServer.url("/sample/com/test.jpg").toString()

        fileDownloader.mimeTypeMap = mockMimeTypeMap
        When calling mockMimeTypeMap.getMimeTypeFromExtension(".jpg") itReturns "image/jpg"
        When calling fileDownloader.createFileDirectory(TEST_FILENAME) itReturns File(TEST_FILE_PATH)

        fileDownloader.startDownloading(
            TEST_CALLBACK_ID,
            TEST_FILENAME,
            url,
            TEST_HEADER_OBJECT
        )

        verify(bridgeExecutor).postError(TEST_CALLBACK_ID, errorMsg)

        mockServer.shutdown()
    }
}
