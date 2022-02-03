package com.rakuten.tech.mobile.miniapp.ads

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import org.mockito.kotlin.spy
import com.rakuten.tech.mobile.miniapp.TEST_AD_UNIT_ID
import com.rakuten.tech.mobile.miniapp.TestActivity
import org.amshove.kluent.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
class AdMobDisplayer19Spec {
    private lateinit var context: Activity
    private lateinit var adDisplayer19: AdMobDisplayer19

    @Before
    fun setup() {
        ActivityScenario.launch(TestActivity::class.java).onActivity { activity ->
            context = activity
            adDisplayer19 = Mockito.spy(AdMobDisplayer19(context))
        }
    }

    @Test
    fun `should show interstitial ads when it is ready`() {
        adDisplayer19.loadInterstitialAd(TEST_AD_UNIT_ID, {}, {})

        val map = HashMap<String, InterstitialAd>()
        val ad = Mockito.spy(InterstitialAd(context))
        ad.adUnitId = TEST_AD_UNIT_ID
        When calling ad.isLoaded itReturns true
        map[TEST_AD_UNIT_ID] = ad
        adDisplayer19.initAdMap(interstitialAdMap = map)

        adDisplayer19.showInterstitialAd(TEST_AD_UNIT_ID, {}, {})
    }

    @Test
    fun `should invoke error when interstitial ads when is not ready`() {
        val onError: (msg: String) -> Unit = {
            it shouldBe "Ad is not loaded yet"
        }
        adDisplayer19.showInterstitialAd(TEST_AD_UNIT_ID, {}, onError)

        adDisplayer19.loadInterstitialAd(TEST_AD_UNIT_ID, {}, {})
        adDisplayer19.showInterstitialAd(TEST_AD_UNIT_ID, {}, onError)
    }

    @Test
    fun `should invoke error when interstitial ads is already on queue`() {
        val onError: (msg: String) -> Unit = {
            it shouldBeEqualTo "Previous $TEST_AD_UNIT_ID is still in progress"
        }
        adDisplayer19.loadInterstitialAd(TEST_AD_UNIT_ID, {}, {})

        adDisplayer19.loadInterstitialAd(TEST_AD_UNIT_ID, {}, onError)
    }

    @Test
    fun `should show rewarded ads when it is ready`() {
        adDisplayer19.loadRewardedAd(TEST_AD_UNIT_ID, {}, {})

        val map = HashMap<String, RewardedAd>()
        val ad = Mockito.spy(RewardedAd(context, TEST_AD_UNIT_ID))
        When calling ad.isLoaded itReturns true
        map[TEST_AD_UNIT_ID] = ad
        adDisplayer19.initAdMap(rewardedAdMap = map)

        val onClosed: (reward: Reward?) -> Unit = {}
        val onError: (msg: String) -> Unit = {}
        val rewardedAdCallback = adDisplayer19.createRewardedAdShowCallback(TEST_AD_UNIT_ID, onClosed, onError)
        When calling adDisplayer19.createRewardedAdShowCallback(TEST_AD_UNIT_ID, onClosed, onError) itReturns
                rewardedAdCallback

        adDisplayer19.showRewardedAd(TEST_AD_UNIT_ID, onClosed, onError)
    }

    @Test
    fun `should invoke sdk callbacks without error`() {
        val rewardedAdLoadCallback = adDisplayer19.createRewardedAdLoadCallback(TEST_AD_UNIT_ID, spy(), spy())
        val rewardedAdShowCallback = adDisplayer19.createRewardedAdShowCallback(TEST_AD_UNIT_ID, spy(), spy())
        val rewardItem = object : RewardItem {
            override fun getType(): String = ""

            override fun getAmount(): Int = 0
        }

        rewardedAdLoadCallback.onRewardedAdLoaded()
        rewardedAdLoadCallback.onRewardedAdFailedToLoad(LoadAdError(0, "", "", null, null))

        rewardedAdShowCallback.onUserEarnedReward(rewardItem)
        rewardedAdShowCallback.onRewardedAdClosed()
    }

    @Test
    fun `should invoke error when rewarded ads when is not ready`() {
        val onError: (msg: String) -> Unit = {
            it shouldBe "Ad is not loaded yet"
        }
        adDisplayer19.showRewardedAd(TEST_AD_UNIT_ID, {}, onError)

        adDisplayer19.loadRewardedAd(TEST_AD_UNIT_ID, {}, {})
        adDisplayer19.showRewardedAd(TEST_AD_UNIT_ID, {}, onError)
    }

    @Test
    fun `should invoke error when rewarded ads is already on queue`() {
        val onError: (msg: String) -> Unit = {
            it shouldBeEqualTo "Previous $TEST_AD_UNIT_ID is still in progress"
        }
        adDisplayer19.loadRewardedAd(TEST_AD_UNIT_ID, {}, {})

        adDisplayer19.loadRewardedAd(TEST_AD_UNIT_ID, {}, onError)
    }

    @Test
    fun `should not have ad in queue when it loads failed`() {
        val map = HashMap<String, RewardedAd>()
        val ad = Mockito.spy(RewardedAd(context, TEST_AD_UNIT_ID))
        map[TEST_AD_UNIT_ID] = ad
        adDisplayer19.initAdMap(interstitialAdMap = mutableMapOf(), rewardedAdMap = map)

        val rewardedAdLoadCallback = adDisplayer19.createRewardedAdLoadCallback(TEST_AD_UNIT_ID, spy(), spy())
        rewardedAdLoadCallback.onRewardedAdLoaded()
        rewardedAdLoadCallback.onRewardedAdFailedToLoad(LoadAdError(0, "", "", null, null))

        adDisplayer19.rewardedAdMap.containsKey(TEST_AD_UNIT_ID) shouldBe false
    }
}
