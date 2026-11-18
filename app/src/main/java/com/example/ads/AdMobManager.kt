package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.model.AdvertisementSettings
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions

sealed interface RewardedAdFlowState {
    data object Idle : RewardedAdFlowState
    data object LoadingAd : RewardedAdFlowState
    data object ShowingAd : RewardedAdFlowState
    data object RewardVerified : RewardedAdFlowState
    data class Failed(val fallbackMessage: String) : RewardedAdFlowState
}

object AdMobManager {
    private const val TAG = "AdMobManager"

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    @Volatile
    private var initialized = false

    private var cachedInterstitialAd: InterstitialAd? = null
    private var detailOpenCounter: Int = 0

    fun initialize(context: Context) {
        if (initialized) return
        runCatching {
            MobileAds.initialize(context.applicationContext) { status ->
                Log.d(TAG, "AdMob initialized: $status")
            }
            initialized = true
        }.onFailure { e ->
            Log.w(TAG, "AdMob initialization warning: ${e.message}")
        }
    }

    fun resolveBannerUnitId(settings: AdvertisementSettings): String {
        if (settings.testAdMode) return TEST_BANNER_AD_UNIT_ID
        return settings.bannerAdUnitId.ifBlank {
            BuildConfig.ADMOB_BANNER_AD_UNIT_ID.ifBlank { TEST_BANNER_AD_UNIT_ID }
        }
    }

    fun resolveRewardedUnitId(settings: AdvertisementSettings): String {
        if (settings.testAdMode) return TEST_REWARDED_AD_UNIT_ID
        return settings.rewardedAdUnitId.ifBlank {
            BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.ifBlank { TEST_REWARDED_AD_UNIT_ID }
        }
    }

    fun resolveInterstitialUnitId(settings: AdvertisementSettings): String {
        if (settings.testAdMode) return TEST_INTERSTITIAL_AD_UNIT_ID
        return settings.interstitialAdUnitId.ifBlank {
            BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.ifBlank { TEST_INTERSTITIAL_AD_UNIT_ID }
        }
    }

    /**
     * Requests and displays a Google AdMob Rewarded Advertisement.
     *
     * CRITICAL MONETIZATION & SSV INVARIANT:
     * - Never unlocks the prompt before the verified [OnUserEarnedRewardListener] callback fires.
     * - Configures [ServerSideVerificationOptions] with `userId` and `customData` (`imageId`) so that
     *   when an AdMob SSV Cloud Function callback URL is configured on the Firebase Blaze plan,
     *   AdMob passes `(userId, imageId)` directly to the server-side verifier.
     */
    fun showRewardedAdForPromptUnlock(
        context: Context,
        settings: AdvertisementSettings,
        userId: String? = null,
        imageId: String? = null,
        onStateChanged: (RewardedAdFlowState) -> Unit,
        onVerifiedRewardEarned: () -> Unit
    ) {
        if (!settings.allAdsEnabled || !settings.rewardedAdsEnabled) {
            onStateChanged(RewardedAdFlowState.RewardVerified)
            onVerifiedRewardEarned()
            return
        }

        val activity = context as? Activity
        if (activity == null || activity.isFinishing) {
            onStateChanged(RewardedAdFlowState.Failed(settings.adFallbackMessage))
            return
        }

        initialize(context)
        onStateChanged(RewardedAdFlowState.LoadingAd)

        val unitId = resolveRewardedUnitId(settings)
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context.applicationContext,
            unitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    var rewardGrantedByCallback = false

                    // Attach ServerSideVerificationOptions (SSV) payload for Cloud Function SSV verification (Blaze plan)
                    if (!userId.isNullOrBlank() || !imageId.isNullOrBlank()) {
                        val ssvBuilder = ServerSideVerificationOptions.Builder()
                        if (!userId.isNullOrBlank()) ssvBuilder.setUserId(userId)
                        if (!imageId.isNullOrBlank()) ssvBuilder.setCustomData(imageId)
                        rewardedAd.setServerSideVerificationOptions(ssvBuilder.build())
                    }

                    rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            onStateChanged(RewardedAdFlowState.ShowingAd)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.w(TAG, "Rewarded ad failed to show: ${adError.message}")
                            onStateChanged(RewardedAdFlowState.Failed(settings.adFallbackMessage))
                        }

                        override fun onAdDismissedFullScreenContent() {
                            if (!rewardGrantedByCallback) {
                                onStateChanged(
                                    RewardedAdFlowState.Failed(
                                        "Please watch the complete rewarded advertisement to unlock this prompt."
                                    )
                                )
                            }
                        }
                    }

                    rewardedAd.show(
                        activity,
                        OnUserEarnedRewardListener { rewardItem ->
                            Log.d(TAG, "Verified reward callback received: ${rewardItem.amount} ${rewardItem.type}")
                            rewardGrantedByCallback = true
                            onStateChanged(RewardedAdFlowState.RewardVerified)
                            onVerifiedRewardEarned()
                        }
                    )
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    onStateChanged(RewardedAdFlowState.Failed(settings.adFallbackMessage))
                }
            }
        )
    }

    fun onDetailScreenOpened(context: Context, settings: AdvertisementSettings) {
        if (!settings.allAdsEnabled || !settings.interstitialAdsEnabled) return
        val frequency = settings.interstitialFrequency.coerceIn(1L, 50L).toInt()
        detailOpenCounter++
        val activity = context as? Activity ?: return

        if (detailOpenCounter % frequency == 0) {
            val ad = cachedInterstitialAd
            if (ad != null) {
                ad.show(activity)
                cachedInterstitialAd = null
            }
            preloadInterstitial(context, settings)
        } else if (cachedInterstitialAd == null) {
            preloadInterstitial(context, settings)
        }
    }

    private fun preloadInterstitial(context: Context, settings: AdvertisementSettings) {
        initialize(context)
        val unitId = resolveInterstitialUnitId(settings)
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    cachedInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    cachedInterstitialAd = null
                }
            }
        )
    }
}

/**
 * Clean, policy-compliant Google AdMob Banner Ad container.
 * - Independently controlled by AdvertisementSettings.
 * - Collapses completely (zero height, zero empty gap) while loading or if loading fails.
 * - Never overlaps images or action buttons.
 */
@Composable
fun LumiereBannerAdSlot(
    settings: AdvertisementSettings,
    enabledForPlacement: Boolean,
    modifier: Modifier = Modifier,
    testTagName: String = "admob_banner_slot"
) {
    val shouldShow = settings.allAdsEnabled && enabledForPlacement
    if (!shouldShow) return

    val context = LocalContext.current
    val unitId = remember(settings.testAdMode, settings.bannerAdUnitId) {
        AdMobManager.resolveBannerUnitId(settings)
    }
    var adLoadedSuccessfully by remember(unitId) { mutableStateOf(false) }
    var adFailed by remember(unitId) { mutableStateOf(false) }

    if (adFailed) return

    val adView = remember(unitId) {
        AdMobManager.initialize(context)
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = unitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adLoadedSuccessfully = true
                    adFailed = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoadedSuccessfully = false
                    adFailed = true
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            runCatching { adView.destroy() }
        }
    }

    if (adLoadedSuccessfully) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(testTagName)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SPONSORED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { adView },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Zero-height attachment so AdView can complete its initial load without leaving any visible gap
        Box(modifier = Modifier.size(0.dp).alpha(0f)) {
            AndroidView(factory = { adView })
        }
    }
}
