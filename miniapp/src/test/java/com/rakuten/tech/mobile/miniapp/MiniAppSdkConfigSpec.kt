package com.rakuten.tech.mobile.miniapp

import org.amshove.kluent.shouldEqual
import org.junit.Test

class MiniAppSdkConfigSpec {

    @Test
    fun `the key pattern should match the defined scheme`() {
        val config = MiniAppSdkConfig(
            baseUrl = TEST_URL_HTTPS_2,
            isTestMode = true,
            adsEnabled = false,
            rasAppId = TEST_HA_ID_APP,
            subscriptionKey = TEST_HA_SUBSCRIPTION_KEY,
            hostAppVersionId = TEST_HA_ID_VERSION,
            hostAppUserAgentInfo = TEST_HA_NAME
        )
        config.key shouldEqual "${config.baseUrl}-${config.isTestMode}" +
                "-${config.rasAppId}-${config.subscriptionKey}-${config.hostAppVersionId}"
    }

    @Test(expected = MiniAppSdkException::class)
    fun `should throw exception when api url is not https`() {
        MiniAppSdkConfig(
            baseUrl = "http://www.example.com/1",
            isTestMode = false,
            adsEnabled = false,
            rasAppId = TEST_HA_ID_APP,
            subscriptionKey = TEST_HA_SUBSCRIPTION_KEY,
            hostAppVersionId = TEST_HA_ID_VERSION,
            hostAppUserAgentInfo = TEST_HA_NAME
        )
    }

    @Test(expected = MiniAppSdkException::class)
    fun `should throw exception when api url is blank`() {
        MiniAppSdkConfig(
            baseUrl = " ",
            isTestMode = true,
            adsEnabled = false,
            rasAppId = TEST_HA_ID_APP,
            subscriptionKey = TEST_HA_SUBSCRIPTION_KEY,
            hostAppVersionId = TEST_HA_ID_VERSION,
            hostAppUserAgentInfo = TEST_HA_NAME
        )
    }

    @Test(expected = MiniAppSdkException::class)
    fun `should throw exception when rasAppId is blank`() {
        MiniAppSdkConfig(
            baseUrl = TEST_URL_HTTPS_2,
            isTestMode = true,
            adsEnabled = false,
            rasAppId = " ",
            subscriptionKey = TEST_HA_SUBSCRIPTION_KEY,
            hostAppVersionId = TEST_HA_ID_VERSION,
            hostAppUserAgentInfo = TEST_HA_NAME
        )
    }

    @Test(expected = MiniAppSdkException::class)
    fun `should throw exception when subscriptionKey is blank`() {
        MiniAppSdkConfig(
            baseUrl = TEST_URL_HTTPS_2,
            isTestMode = true,
            adsEnabled = false,
            rasAppId = TEST_HA_ID_APP,
            subscriptionKey = " ",
            hostAppVersionId = TEST_HA_ID_VERSION,
            hostAppUserAgentInfo = TEST_HA_NAME
        )
    }

    @Test(expected = MiniAppSdkException::class)
    fun `should throw exception when hostAppVersionId is blank`() {
        MiniAppSdkConfig(
            baseUrl = TEST_URL_HTTPS_2,
            isTestMode = true,
            adsEnabled = false,
            rasAppId = TEST_HA_ID_APP,
            subscriptionKey = TEST_HA_SUBSCRIPTION_KEY,
            hostAppVersionId = " ",
            hostAppUserAgentInfo = TEST_HA_NAME
        )
    }
}
