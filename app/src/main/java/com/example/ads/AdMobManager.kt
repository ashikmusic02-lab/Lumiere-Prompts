package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdMobManager {
    private const val TAG = "AdMobManager"
    private const val REWARDED_LOAD_TIMEOUT_MS = 7_000L
    private const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    // Official Google AdMob Test Ad Unit IDs (used only when testAdMode == true in Firestore)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    @Volatile
    private var mobileAdsInitialized = false

    private val _canRequestAdsFlow = MutableStateFlow(false)
    val canRequestAdsFlow: StateFlow<Boolean> = _canRequestAdsFlow.asStateFlow()

    private val _isPrivacyOptionsRequiredFlow = MutableStateFlow(false)
    val isPrivacyOptionsRequiredFlow: StateFlow<Boolean> = _isPrivacyOptionsRequiredFlow.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var cachedInterstitialAd: InterstitialAd? = null
    private var cachedInterstitialUnitId: String? = null
    private var isPreloadingInterstitial = false

    // Session tracking for Interstitial rules
    private var postsOpenedInSessionCount: Int = 0
    private var detailExitCount: Int = 0
    private var lastInterstitialShownAtMs: Long = 0L
    private var rewardedAdShownInCurrentVisit: Boolean = false

    /**
     * Requests Google User Messaging Platform (UMP) consent information at launch,
     * shows the consent form if required, and initializes MobileAds only after `canRequestAds()`.
     */
    fun requestConsentAndInitializeAds(activity: Activity) {
        runCatching {
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) {
                            Log.w(TAG, "UMP consent form warning: ${formError.message}")
                        }
                        updateConsentFlags(activity, consentInfo)
                    }
                },
                { requestError ->
                    Log.w(TAG, "UMP consent update warning: ${requestError.message}")
                    updateConsentFlags(activity, consentInfo)
                }
            )

            // Check if consent was already gathered in a previous session
            if (consentInfo.canRequestAds()) {
                updateConsentFlags(activity, consentInfo)
            }
        }.onFailure {
            // Safe fallback if UMP fails in non-GMS environment
            _canRequestAdsFlow.value = true
            initializeMobileAdsIfAllowed(activity)
        }
    }

    private fun updateConsentFlags(context: Context, consentInfo: ConsentInformation) {
        val allowed = runCatching { consentInfo.canRequestAds() }.getOrDefault(true)
        _canRequestAdsFlow.value = allowed
        _isPrivacyOptionsRequiredFlow.value = runCatching {
            consentInfo.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }.getOrDefault(false)

        if (allowed) {
            initializeMobileAdsIfAllowed(context)
        }
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
                updateConsentFlags(activity, consentInfo)
                onDismissed()
            }
        }.onFailure {
            onDismissed()
        }
    }

    fun canRequestAds(context: Context): Boolean {
        if (_canRequestAdsFlow.value) return true
        val allowed = runCatching {
            UserMessagingPlatform.getConsentInformation(context.applicationContext).canRequestAds()
        }.getOrDefault(false)
        if (allowed) {
            _canRequestAdsFlow.value = true
        }
        return allowed
    }

    private fun initializeMobileAdsIfAllowed(context: Context) {
        if (mobileAdsInitialized) return
        if (!canRequestAds(context)) return
        runCatching {
            MobileAds.initialize(context.applicationContext) {
                Log.d(TAG, "MobileAds initialized")
            }
            mobileAdsInitialized = true
        }.onFailure { e ->
            Log.w(TAG, "MobileAds initialization warning: ${e.message}")
        }
    }

    private fun isValidAdUnitId(raw: String): Boolean {
        val cleaned = raw.trim()
        return cleaned.isNotBlank() &&
            !cleaned.startsWith("YOUR_") &&
            !cleaned.startsWith("REPLACE_") &&
            cleaned.contains("/")
    }

    fun resolveBannerUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.homeBannerEnabled && !settings.detailBannerEnabled) return ""
        if (settings.testAdMode) return TEST_BANNER_AD_UNIT_ID
        val candidate = settings.bannerAdUnitId.trim()
        return if (isValidAdUnitId(candidate)) candidate else ""
    }

    fun resolveRewardedUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.rewardedAdsEnabled) return ""
        if (settings.testAdMode) return TEST_REWARDED_AD_UNIT_ID
        val candidate = settings.rewardedAdUnitId.trim()
        return if (isValidAdUnitId(candidate)) candidate else ""
    }

    fun resolveInterstitialUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.interstitialAdsEnabled) return ""
        if (settings.testAdMode) return TEST_INTERSTITIAL_AD_UNIT_ID
        val candidate = settings.interstitialAdUnitId.trim()
        return if (isValidAdUnitId(candidate)) candidate else ""
    }

    /**
     * Called when an Image Detail screen is opened.
     */
    fun onDetailScreenEntered(context: Context, settings: AdvertisementSettings) {
        postsOpenedInSessionCount++
        rewardedAdShownInCurrentVisit = false
        preloadInterstitialIfNeeded(context, settings)
    }

    /**
     * Called when the user leaves an Image Detail screen back to the list.
     *
     * Interstitial rules:
     * - Show after every N-th exit from detail screen, N = interstitialFrequency (default 5)
     * - Never in the same visit where a rewarded ad was watched
     * - Never on app launch/exit
     * - Never within 60 seconds of the previous interstitial
     * - Never before the user has opened at least 2 posts in the session
     * - Never while the keyboard/comment box is active
     * - Preload the next interstitial in advance
     */
    fun onDetailScreenExited(
        context: Context,
        settings: AdvertisementSettings,
        isCommentInputActive: Boolean
    ) {
        val unitId = resolveInterstitialUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(context)) return

        detailExitCount++
        val frequency = settings.interstitialFrequency.coerceIn(1L, 50L).toInt()
        val now = System.currentTimeMillis()
        val cooldownElapsed = (now - lastInterstitialShownAtMs) >= INTERSTITIAL_COOLDOWN_MS

        val eligibleToShow = !isCommentInputActive &&
            !rewardedAdShownInCurrentVisit &&
            postsOpenedInSessionCount >= 2 &&
            (detailExitCount % frequency == 0) &&
            cooldownElapsed

        val activity = context as? Activity
        val readyAd = cachedInterstitialAd
        if (eligibleToShow && activity != null && !activity.isFinishing && readyAd != null) {
            cachedInterstitialAd = null
            lastInterstitialShownAtMs = now
            readyAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preloadInterstitialIfNeeded(activity, settings)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    preloadInterstitialIfNeeded(activity, settings)
                }
            }
            runCatching { readyAd.show(activity) }
        } else {
            preloadInterstitialIfNeeded(context, settings)
        }
    }

    fun preloadInterstitialIfNeeded(context: Context, settings: AdvertisementSettings) {
        val unitId = resolveInterstitialUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(context)) return
        if (cachedInterstitialAd != null && cachedInterstitialUnitId == unitId) return
        if (isPreloadingInterstitial) return

        initializeMobileAdsIfAllowed(context)
        isPreloadingInterstitial = true

        runCatching {
            InterstitialAd.load(
                context.applicationContext,
                unitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isPreloadingInterstitial = false
                        cachedInterstitialAd = ad
                        cachedInterstitialUnitId = unitId
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isPreloadingInterstitial = false
                        cachedInterstitialAd = null
                    }
                }
            )
        }.onFailure {
            isPreloadingInterstitial = false
        }
    }

    /**
     * Flow for "Watch ad to view prompt":
     * - Loads a rewarded ad with a strict 7-second timeout.
     * - If rewarded ads are disabled in settings, the rewarded unit ID is blank, UMP consent is not
     *   available, the ad fails to load, fails to show, or times out after 7 seconds:
     *   DOES NOT BLOCK THE USER — immediately calls `onProceedToPrompt("none")`.
     * - If the ad loads within 7 seconds, displays it; when the reward callback fires, calls
     *   `onProceedToPrompt("AdMob")`.
     */
    fun loadAndShowRewardedAdForPrompt(
        context: Context,
        settings: AdvertisementSettings,
        userId: String?,
        imageId: String,
        onLoadingStateChanged: (Boolean) -> Unit,
        onProceedToPrompt: (adNetworkUsed: String) -> Unit
    ) {
        val unitId = resolveRewardedUnitId(settings)
        val activity = context as? Activity

        if (unitId.isBlank() || activity == null || activity.isFinishing || !canRequestAds(context)) {
            onLoadingStateChanged(false)
            onProceedToPrompt("none")
            return
        }

        initializeMobileAdsIfAllowed(context)
        onLoadingStateChanged(true)

        var completedOrTimedOut = false
        val timeoutRunnable = Runnable {
            if (!completedOrTimedOut) {
                completedOrTimedOut = true
                onLoadingStateChanged(false)
                // Timeout after 7s: do not block the user
                onProceedToPrompt("none")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, REWARDED_LOAD_TIMEOUT_MS)

        runCatching {
            RewardedAd.load(
                context.applicationContext,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        if (completedOrTimedOut) return
                        completedOrTimedOut = true
                        mainHandler.removeCallbacks(timeoutRunnable)

                        if (activity.isFinishing) {
                            onLoadingStateChanged(false)
                            onProceedToPrompt("none")
                            return
                        }

                        var rewardEarned = false
                        rewardedAdShownInCurrentVisit = true

                        if (!userId.isNullOrBlank() || imageId.isNotBlank()) {
                            val ssvBuilder = ServerSideVerificationOptions.Builder()
                            if (!userId.isNullOrBlank()) ssvBuilder.setUserId(userId)
                            if (imageId.isNotBlank()) ssvBuilder.setCustomData(imageId)
                            rewardedAd.setServerSideVerificationOptions(ssvBuilder.build())
                        }

                        rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                onLoadingStateChanged(false)
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                onLoadingStateChanged(false)
                                // Ad failed to show: do not block the user
                                onProceedToPrompt("none")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                onLoadingStateChanged(false)
                                if (rewardEarned) {
                                    onProceedToPrompt("AdMob")
                                }
                            }
                        }

                        rewardedAd.show(
                            activity,
                            OnUserEarnedRewardListener {
                                rewardEarned = true
                            }
                        )
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if (completedOrTimedOut) return
                        completedOrTimedOut = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        onLoadingStateChanged(false)
                        // Ad failed to load: do not block the user
                        onProceedToPrompt("none")
                    }
                }
            )
        }.onFailure {
            if (!completedOrTimedOut) {
                completedOrTimedOut = true
                mainHandler.removeCallbacks(timeoutRunnable)
                onLoadingStateChanged(false)
                onProceedToPrompt("none")
            }
        }
    }
}

/**
 * Clean Banner Ad container with NO "SPONSORED" label or decorative text.
 * - Renders only when `allAdsEnabled`, `enabledForPlacement`, `canRequestAds`, and a valid unit ID exist.
 * - Collapses completely (0.dp) while loading or if loading fails.
 */
@Composable
fun LumiereBannerAdSlot(
    settings: AdvertisementSettings,
    enabledForPlacement: Boolean,
    modifier: Modifier = Modifier,
    testTagName: String = "admob_banner_slot"
) {
    val context = LocalContext.current
    val canRequestAds by AdMobManager.canRequestAdsFlow.collectAsStateWithFallback(
        initial = AdMobManager.canRequestAds(context)
    )
    val unitId = remember(settings) {
        AdMobManager.resolveBannerUnitId(settings)
    }

    val shouldShow = settings.allAdsEnabled &&
        enabledForPlacement &&
        canRequestAds &&
        unitId.isNotBlank()

    if (!shouldShow) return

    var adLoadedSuccessfully by remember(unitId) { mutableStateOf(false) }
    var adFailed by remember(unitId) { mutableStateOf(false) }

    if (adFailed) return

    val adView = remember(unitId) {
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
            runCatching { loadAd(AdRequest.Builder().build()) }
                .onFailure { adFailed = true }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            runCatching { adView.destroy() }
        }
    }

    if (adLoadedSuccessfully) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .testTag(testTagName)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { adView },
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        Box(modifier = Modifier.size(0.dp).alpha(0f)) {
            AndroidView(factory = { adView })
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateWithFallback(initial: T): androidx.compose.runtime.State<T> {
    val state = remember { mutableStateOf(initial) }
    androidx.compose.runtime.LaunchedEffect(this) {
        collect { state.value = it }
    }
    return state
}
