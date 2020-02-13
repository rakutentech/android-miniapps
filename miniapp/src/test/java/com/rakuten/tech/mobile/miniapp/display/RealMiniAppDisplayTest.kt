package com.rakuten.tech.mobile.miniapp.display

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rakuten.tech.mobile.miniapp.TEST_BASE_PATH
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RealMiniAppDisplayTest {

    lateinit var context: Context

    @Before
    fun setup() {
        context = getApplicationContext()
    }

    @Test
    fun `for a given basePath RealMiniAppDisplay creates corresponding view for the caller`() =
        runBlockingTest {
            val realDisplay = RealMiniAppDisplay(context, basePath = TEST_BASE_PATH)
            realDisplay.url shouldContain TEST_BASE_PATH
        }

    @Test
    fun `for a given basePath, getMiniAppView should not return WebView to the caller`() =
        runBlockingTest {
            val realDisplay = RealMiniAppDisplay(context, basePath = TEST_BASE_PATH)
            realDisplay.getMiniAppView() shouldNotHaveTheSameClassAs WebView::class
        }

    @Test
    fun `when getLoadUrl is called then a formed path is returned from basePath for loading`() {
        val realDisplay = RealMiniAppDisplay(context, basePath = TEST_BASE_PATH)
        realDisplay.getLoadUrl() shouldBeEqualTo "file://dummy/index.html"
    }

    @Test
    fun `when MiniAppDisplay is created then LayoutParams use MATCH_PARENT for dimensions`() {
        val realDisplay = RealMiniAppDisplay(context, basePath = TEST_BASE_PATH)
        realDisplay.layoutParams.width shouldEqualTo ViewGroup.LayoutParams.MATCH_PARENT
        realDisplay.layoutParams.height shouldEqualTo ViewGroup.LayoutParams.MATCH_PARENT
    }

    @Test
    fun `when MiniAppDisplay is created then required websettings should be enabled`() {
        val miniAppWindow = RealMiniAppDisplay(context, basePath = TEST_BASE_PATH)
        assertTrue { miniAppWindow.settings.javaScriptEnabled }
        assertTrue { miniAppWindow.settings.allowUniversalAccessFromFileURLs }
    }

    @Test
    fun `when MiniAppDisplay is created then WebViewClient is set to MiniAppWebViewClient`() {
        val realDisplay = RealMiniAppDisplay(context, basePath = TEST_BASE_PATH)
        (realDisplay as WebView).webViewClient shouldBeInstanceOf MiniAppWebViewClient::class
    }
}
