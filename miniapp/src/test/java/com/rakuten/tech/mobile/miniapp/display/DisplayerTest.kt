package com.rakuten.tech.mobile.miniapp.display

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import com.rakuten.tech.mobile.miniapp.TEST_MA_ID
import com.rakuten.tech.mobile.miniapp.js.MiniAppMessageBridge
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayerTest {

    private lateinit var context: Context
    private val miniAppMessageBridge: MiniAppMessageBridge = mock()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `for a given base path returns an implementer of MiniAppDisplay`() =
        runBlockingTest {
            val obtainedDisplay = getMiniAppDisplay()
            obtainedDisplay shouldBeInstanceOf MiniAppDisplay::class
        }

    private fun getMiniAppDisplay(): MiniAppDisplay =
        Displayer().createMiniAppDisplay(
            basePath = context.filesDir.path,
            appId = TEST_MA_ID,
            miniAppMessageBridge = miniAppMessageBridge
        )
}
