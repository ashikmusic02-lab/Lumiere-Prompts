package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdMobManager {
    private const val TAG = "AdMobManager"
    private const val ON_DEMAND_AD_TIMEOUT_MS = 5_000L
    private const val INTERSTITIAL_COOLDOWN_MS = 45_000L

    // Official Google AdMob Test Ad Unit IDs (used when testAdMode == true in Firestore or as emulator fallback)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    @Volatile
    private var mobileAdsInitialized = false

    private val _canRequestAdsFlow = MutableStateFlow(true)
    val canRequestAdsFlow: StateFlow<Boolean> = _canRequestAdsFlow.asStateFlow()

    private val _isPrivacyOptionsRequiredFlow = MutableStateFlow(false)
    val isPrivacyOptionsRequiredFlow: StateFlow<Boolean> = _isPrivacyOptionsRequiredFlow.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var cachedInterstitialAd: InterstitialAd? = null
    private var cachedInterstitialUnitId: String? = null
    private var isPreloadingInterstitial = false

    private var cachedRewardedAd: RewardedAd? = null
    private var cachedRewardedUnitId: String? = null
    private var isPreloadingRewarded = false

    // Session tracking
    private var postsOpenedInSessionCount: Int = 0
    private var detailExitCount: Int = 0
    private var lastInterstitialShownAtMs: Long = 0L
    private var rewardedAdShownInCurrentVisit: Boolean = false

    /**
     * Unwraps any [ContextWrapper] chain to safely obtain the hosting [Activity].
     */
    fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    internal fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.PRODUCT.contains("emulator") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
    }

    /**
     * Cleans up unsigned/corrupted Chromium WebView variations seed files (`variations_seed`,
     * `variations_seed_new`, `variations_stamp`) written by the emulator's VariationsSeedServer so
     * Chromium never logs `E/chromium: [ERROR:variations_seed_loader.cc(37)] Seed missing signature.`
     */
    fun purgeCorruptedWebViewVariationsSeeds(context: Context) {
        runCatching {
            val appContext = context.applicationContext ?: context
            val candidateDirs = buildList {
                runCatching { add(appContext.getDir("webview", Context.MODE_PRIVATE)) }
                runCatching { add(java.io.File(appContext.applicationInfo.dataDir, "app_webview")) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    runCatching {
                        val dpContext = appContext.createDeviceProtectedStorageContext()
                        add(dpContext.getDir("webview", Context.MODE_PRIVATE))
                        add(java.io.File(dpContext.dataDir, "app_webview"))
                    }
                }
            }.distinctBy { it.absolutePath }

            val seedFileNames = listOf(
                "variations_seed",
                "variations_seed_new",
                "variations_stamp"
            )

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    for (fileName in seedFileNames) {
                        val target = java.io.File(dir, fileName)
                        if (target.exists()) {
                            runCatching { target.delete() }
                        }
                    }
                }
            }
        }
    }

    /**
     * Initializes MobileAds and updates UMP consent flags asynchronously in the background.
     * Never blocks ad loading if UMP form is not configured in the AdMob console.
     */
    fun requestConsentAndInitializeAds(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        purgeCorruptedWebViewVariationsSeeds(activity)
        ensureMobileAdsInitialized(activity)

        // On emulators, skip UMP network WebView check because UMP console forms are not served to emulators
        if (isRunningOnEmulator()) {
            _canRequestAdsFlow.value = true
            return
        }

        runCatching {
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()

            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "UMP consent form warning: ${formError.message}")
                            }
                            updateConsentFlags(activity, consentInfo)
                        }
                    }
                },
                { requestError ->
                    Log.w(TAG, "UMP consent update warning: ${requestError.message}")
                    updateConsentFlags(activity, consentInfo)
                }
            )
        }.onFailure {
            _canRequestAdsFlow.value = true
            ensureMobileAdsInitialized(activity)
        }
    }

    private fun updateConsentFlags(context: Context, consentInfo: ConsentInformation) {
        val privacyRequired = runCatching {
            consentInfo.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }.getOrDefault(false)
        _isPrivacyOptionsRequiredFlow.value = privacyRequired

        val allowed = if (privacyRequired) {
            runCatching { consentInfo.canRequestAds() }.getOrDefault(true)
        } else {
            true
        }
        _canRequestAdsFlow.value = allowed
        if (allowed) {
            ensureMobileAdsInitialized(context)
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
        return _canRequestAdsFlow.value
    }

    fun ensureMobileAdsInitialized(context: Context) {
        purgeCorruptedWebViewVariationsSeeds(context)
        if (mobileAdsInitialized) return
        runCatching {
            val appCtx = context.applicationContext ?: context
            MobileAds.initialize(appCtx) {
                Log.d(TAG, "MobileAds initialized")
                purgeCorruptedWebViewVariationsSeeds(appCtx)
            }
            mobileAdsInitialized = true
            // Also purge any newly written unsigned seed from VariationsSeedServer after WebView init
            mainHandler.postDelayed({ purgeCorruptedWebViewVariationsSeeds(appCtx) }, 2_500L)
            mainHandler.postDelayed({ purgeCorruptedWebViewVariationsSeeds(appCtx) }, 7_000L)
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
        if (!settings.allAdsEnabled || (!settings.homeBannerEnabled && !settings.detailBannerEnabled)) return ""
        if (settings.testAdMode) return TEST_BANNER_AD_UNIT_ID
        val candidate = settings.bannerAdUnitId.trim().ifBlank { BuildConfig.ADMOB_BANNER_AD_UNIT_ID.trim() }
        return if (isValidAdUnitId(candidate)) candidate else TEST_BANNER_AD_UNIT_ID
    }

    fun resolveRewardedUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.rewardedAdsEnabled) return ""
        if (settings.testAdMode) return TEST_REWARDED_AD_UNIT_ID
        val candidate = settings.rewardedAdUnitId.trim().ifBlank { BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.trim() }
        return if (isValidAdUnitId(candidate)) candidate else TEST_REWARDED_AD_UNIT_ID
    }

    fun resolveInterstitialUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled) return ""
        if (!settings.interstitialAdsEnabled && !settings.rewardedAdsEnabled) return ""
        if (settings.testAdMode) return TEST_INTERSTITIAL_AD_UNIT_ID
        val candidate = settings.interstitialAdUnitId.trim().ifBlank { BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.trim() }
        return if (isValidAdUnitId(candidate)) candidate else TEST_INTERSTITIAL_AD_UNIT_ID
    }

    /**
     * Called when an Image Detail screen is opened:
     * Immediately starts preloading both the Rewarded Ad and Interstitial Ad in the background.
     */
    fun onDetailScreenEntered(context: Context, settings: AdvertisementSettings) {
        postsOpenedInSessionCount++
        rewardedAdShownInCurrentVisit = false
        preloadRewardedAdIfNeeded(context, settings)
        preloadInterstitialIfNeeded(context, settings)
    }

    /**
     * Preloads a Rewarded Ad in the background so that when the user taps "Get Prompt",
     * the ad is ready immediately.
     */
    fun preloadRewardedAdIfNeeded(context: Context, settings: AdvertisementSettings) {
        val unitId = resolveRewardedUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(context)) return
        if (cachedRewardedAd != null && cachedRewardedUnitId == unitId) return
        if (isPreloadingRewarded) return

        ensureMobileAdsInitialized(context)
        isPreloadingRewarded = true

        runCatching {
            RewardedAd.load(
                context.applicationContext,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isPreloadingRewarded = false
                        cachedRewardedAd = ad
                        cachedRewardedUnitId = unitId
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if ((BuildConfig.DEBUG || isRunningOnEmulator()) && unitId != TEST_REWARDED_AD_UNIT_ID) {
                            RewardedAd.load(
                                context.applicationContext,
                                TEST_REWARDED_AD_UNIT_ID,
                                AdRequest.Builder().build(),
                                object : RewardedAdLoadCallback() {
                                    override fun onAdLoaded(ad: RewardedAd) {
                                        isPreloadingRewarded = false
                                        cachedRewardedAd = ad
                                        cachedRewardedUnitId = unitId
                                    }

                                    override fun onAdFailedToLoad(fallbackError: LoadAdError) {
                                        isPreloadingRewarded = false
                                        cachedRewardedAd = null
                                    }
                                }
                            )
                        } else {
                            isPreloadingRewarded = false
                            cachedRewardedAd = null
                        }
                    }
                }
            )
        }.onFailure {
            isPreloadingRewarded = false
        }
    }

    /**
     * Shows a Rewarded Ad or Interstitial Ad when the user taps "Get Prompt" ("প্রম্পট নিন"):
     * 1. If a preloaded Rewarded Ad is ready right now, shows it immediately -> reveals prompt.
     * 2. Else if a preloaded Interstitial Ad is ready right now, shows it immediately -> reveals prompt.
     * 3. Else requests a Rewarded Ad (and fallback Interstitial Ad) with a short timeout,
     *    shows whichever ad loads first, and then reveals the prompt.
     */
    fun showRewardedOrInterstitialForPrompt(
        context: Context,
        settings: AdvertisementSettings,
        userId: String?,
        imageId: String,
        onProceedToPrompt: (adNetworkUsed: String) -> Unit
    ) {
        val activity = findActivity(context)
        if (!settings.allAdsEnabled || activity == null || activity.isFinishing || !canRequestAds(context)) {
            onProceedToPrompt("none")
            return
        }

        val rewardedUnitId = resolveRewardedUnitId(settings)
        val interstitialUnitId = resolveInterstitialUnitId(settings)

        // 1. Check preloaded Rewarded Ad first
        val readyRewarded = cachedRewardedAd
        if (rewardedUnitId.isNotBlank() && readyRewarded != null) {
            cachedRewardedAd = null
            showRewardedAdInstance(
                activity = activity,
                rewardedAd = readyRewarded,
                settings = settings,
                userId = userId,
                imageId = imageId,
                onProceedToPrompt = onProceedToPrompt
            )
            return
        }

        // 2. Check preloaded Interstitial Ad second
        val readyInterstitial = cachedInterstitialAd
        if (interstitialUnitId.isNotBlank() && readyInterstitial != null) {
            cachedInterstitialAd = null
            showInterstitialAdInstance(
                activity = activity,
                interstitialAd = readyInterstitial,
                settings = settings,
                onProceedToPrompt = onProceedToPrompt
            )
            return
        }

        // 3. If neither is preloaded yet, load Rewarded (with automatic fallback to Interstitial)
        if (rewardedUnitId.isBlank() && interstitialUnitId.isBlank()) {
            onProceedToPrompt("none")
            return
        }

        ensureMobileAdsInitialized(context)
        var completed = false
        val timeoutRunnable = Runnable {
            if (!completed) {
                completed = true
                preloadRewardedAdIfNeeded(context, settings)
                preloadInterstitialIfNeeded(context, settings)
                onProceedToPrompt("none")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, ON_DEMAND_AD_TIMEOUT_MS)

        val tryLoadInterstitialFallback: (String) -> Unit = { targetInterstitialUnit ->
            if (!completed && targetInterstitialUnit.isNotBlank()) {
                runCatching {
                    InterstitialAd.load(
                        context.applicationContext,
                        targetInterstitialUnit,
                        AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                if (completed) {
                                    cachedInterstitialAd = ad
                                    cachedInterstitialUnitId = interstitialUnitId
                                    return
                                }
                                completed = true
                                mainHandler.removeCallbacks(timeoutRunnable)
                                showInterstitialAdInstance(
                                    activity = activity,
                                    interstitialAd = ad,
                                    settings = settings,
                                    onProceedToPrompt = onProceedToPrompt
                                )
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                if (!completed && (BuildConfig.DEBUG || isRunningOnEmulator()) && targetInterstitialUnit != TEST_INTERSTITIAL_AD_UNIT_ID) {
                                    InterstitialAd.load(
                                        context.applicationContext,
                                        TEST_INTERSTITIAL_AD_UNIT_ID,
                                        AdRequest.Builder().build(),
                                        object : InterstitialAdLoadCallback() {
                                            override fun onAdLoaded(ad: InterstitialAd) {
                                                if (completed) {
                                                    cachedInterstitialAd = ad
                                                    cachedInterstitialUnitId = interstitialUnitId
                                                    return
                                                }
                                                completed = true
                                                mainHandler.removeCallbacks(timeoutRunnable)
                                                showInterstitialAdInstance(
                                                    activity = activity,
                                                    interstitialAd = ad,
                                                    settings = settings,
                                                    onProceedToPrompt = onProceedToPrompt
                                                )
                                            }

                                            override fun onAdFailedToLoad(fallbackErr: LoadAdError) {
                                                if (!completed) {
                                                    completed = true
                                                    mainHandler.removeCallbacks(timeoutRunnable)
                                                    onProceedToPrompt("none")
                                                }
                                            }
                                        }
                                    )
                                } else if (!completed) {
                                    completed = true
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    onProceedToPrompt("none")
                                }
                            }
                        }
                    )
                }.onFailure {
                    if (!completed) {
                        completed = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        onProceedToPrompt("none")
                    }
                }
            } else if (!completed) {
                completed = true
                mainHandler.removeCallbacks(timeoutRunnable)
                onProceedToPrompt("none")
            }
        }

        if (rewardedUnitId.isNotBlank()) {
            runCatching {
                RewardedAd.load(
                    context.applicationContext,
                    rewardedUnitId,
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(rewardedAd: RewardedAd) {
                            if (completed) {
                                cachedRewardedAd = rewardedAd
                                cachedRewardedUnitId = rewardedUnitId
                                return
                            }
                            completed = true
                            mainHandler.removeCallbacks(timeoutRunnable)
                            showRewardedAdInstance(
                                activity = activity,
                                rewardedAd = rewardedAd,
                                settings = settings,
                                userId = userId,
                                imageId = imageId,
                                onProceedToPrompt = onProceedToPrompt
                            )
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            // Rewarded failed to fill -> fall back to Interstitial Ad
                            tryLoadInterstitialFallback(interstitialUnitId)
                        }
                    }
                )
            }.onFailure {
                tryLoadInterstitialFallback(interstitialUnitId)
            }
        } else {
            tryLoadInterstitialFallback(interstitialUnitId)
        }
    }

    private fun showRewardedAdInstance(
        activity: Activity,
        rewardedAd: RewardedAd,
        settings: AdvertisementSettings,
        userId: String?,
        imageId: String,
        onProceedToPrompt: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onProceedToPrompt("none")
            return
        }
        rewardedAdShownInCurrentVisit = true
        runCatching {
            if (!userId.isNullOrBlank() || imageId.isNotBlank()) {
                val ssvBuilder = ServerSideVerificationOptions.Builder()
                if (!userId.isNullOrBlank()) ssvBuilder.setUserId(userId)
                if (imageId.isNotBlank()) ssvBuilder.setCustomData(imageId)
                rewardedAd.setServerSideVerificationOptions(ssvBuilder.build())
            }

            var handled = false
            val completeOnce: (String) -> Unit = { network ->
                if (!handled) {
                    handled = true
                    preloadRewardedAdIfNeeded(activity, settings)
                    preloadInterstitialIfNeeded(activity, settings)
                    onProceedToPrompt(network)
                }
            }

            rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    completeOnce("none")
                }

                override fun onAdDismissedFullScreenContent() {
                    completeOnce("AdMob")
                }
            }

            rewardedAd.show(
                activity,
                OnUserEarnedRewardListener { }
            )
        }.onFailure {
            preloadRewardedAdIfNeeded(activity, settings)
            onProceedToPrompt("none")
        }
    }

    private fun showInterstitialAdInstance(
        activity: Activity,
        interstitialAd: InterstitialAd,
        settings: AdvertisementSettings,
        onProceedToPrompt: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onProceedToPrompt("none")
            return
        }
        rewardedAdShownInCurrentVisit = true
        lastInterstitialShownAtMs = System.currentTimeMillis()
        runCatching {
            var handled = false
            val completeOnce: (String) -> Unit = { network ->
                if (!handled) {
                    handled = true
                    preloadInterstitialIfNeeded(activity, settings)
                    preloadRewardedAdIfNeeded(activity, settings)
                    onProceedToPrompt(network)
                }
            }

            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    completeOnce("none")
                }

                override fun onAdDismissedFullScreenContent() {
                    completeOnce("AdMob")
                }
            }

            interstitialAd.show(activity)
        }.onFailure {
            preloadInterstitialIfNeeded(activity, settings)
            onProceedToPrompt("none")
        }
    }

    /**
     * Called when the user leaves an Image Detail screen back to the list.
     */
    fun onDetailScreenExited(
        context: Context,
        settings: AdvertisementSettings,
        isCommentInputActive: Boolean
    ) {
        val unitId = resolveInterstitialUnitId(settings)
        if (unitId.isBlank() || !settings.interstitialAdsEnabled || !canRequestAds(context)) return

        detailExitCount++
        val frequency = settings.interstitialFrequency.coerceIn(1L, 50L).toInt()
        val now = System.currentTimeMillis()
        val cooldownElapsed = (now - lastInterstitialShownAtMs) >= INTERSTITIAL_COOLDOWN_MS

        val eligibleToShow = !isCommentInputActive &&
            !rewardedAdShownInCurrentVisit &&
            postsOpenedInSessionCount >= 2 &&
            (detailExitCount % frequency == 0) &&
            cooldownElapsed

        val activity = findActivity(context)
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

        ensureMobileAdsInitialized(context)
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
                        if ((BuildConfig.DEBUG || isRunningOnEmulator()) && unitId != TEST_INTERSTITIAL_AD_UNIT_ID) {
                            InterstitialAd.load(
                                context.applicationContext,
                                TEST_INTERSTITIAL_AD_UNIT_ID,
                                AdRequest.Builder().build(),
                                object : InterstitialAdLoadCallback() {
                                    override fun onAdLoaded(ad: InterstitialAd) {
                                        isPreloadingInterstitial = false
                                        cachedInterstitialAd = ad
                                        cachedInterstitialUnitId = unitId
                                    }

                                    override fun onAdFailedToLoad(fallbackError: LoadAdError) {
                                        isPreloadingInterstitial = false
                                        cachedInterstitialAd = null
                                    }
                                }
                            )
                        } else {
                            isPreloadingInterstitial = false
                            cachedInterstitialAd = null
                        }
                    }
                }
            )
        }.onFailure {
            isPreloadingInterstitial = false
        }
    }
}

/**
 * Native-styled Banner Ad Card container.
 * Uses a sleek card surface and automatically retries with standard BANNER / emulator test fallback
 * if a large banner or unapproved production unit returns no-fill on an emulator.
 */
@Composable
fun LumiereBannerAdSlot(
    settings: AdvertisementSettings,
    enabledForPlacement: Boolean,
    modifier: Modifier = Modifier,
    reserveFixedHeight: Boolean = false,
    useLargeNativeBanner: Boolean = false,
    testTagName: String = "admob_banner_slot"
) {
    val context = LocalContext.current
    val canRequestAds by AdMobManager.canRequestAdsFlow.collectAsStateWithFallback(
        initial = AdMobManager.canRequestAds(context)
    )
    val primaryUnitId = remember(settings) {
        AdMobManager.resolveBannerUnitId(settings)
    }

    val shouldRequestAd = settings.allAdsEnabled &&
        enabledForPlacement &&
        canRequestAds &&
        primaryUnitId.isNotBlank()

    if (!shouldRequestAd) return

    var activeUnitId by remember(primaryUnitId) { mutableStateOf(primaryUnitId) }
    var activeUseLargeBanner by remember(primaryUnitId, useLargeNativeBanner) {
        mutableStateOf(useLargeNativeBanner)
    }
    var adLoadedSuccessfully by remember(activeUnitId, activeUseLargeBanner) { mutableStateOf(false) }
    var adFailedPermanently by remember(primaryUnitId) { mutableStateOf(false) }

    val targetAdSize = if (activeUseLargeBanner) AdSize.LARGE_BANNER else AdSize.BANNER
    val minHeightDp = if (activeUseLargeBanner) 104.dp else 58.dp

    val adView = remember(activeUnitId, activeUseLargeBanner) {
        AdMobManager.ensureMobileAdsInitialized(context)
        AdView(context).apply {
            setAdSize(targetAdSize)
            adUnitId = activeUnitId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adLoadedSuccessfully = true
                    adFailedPermanently = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoadedSuccessfully = false
                    when {
                        activeUseLargeBanner -> {
                            // Fall back from LARGE_BANNER to standard BANNER first
                            activeUseLargeBanner = false
                        }
                        (BuildConfig.DEBUG || AdMobManager.isRunningOnEmulator()) &&
                            activeUnitId != AdMobManager.TEST_BANNER_AD_UNIT_ID -> {
                            // On emulator / debug build, fall back to official Google test banner if production unit has no fill
                            activeUnitId = AdMobManager.TEST_BANNER_AD_UNIT_ID
                        }
                        else -> {
                            adFailedPermanently = true
                        }
                    }
                }
            }
            runCatching { loadAd(AdRequest.Builder().build()) }
                .onFailure { adFailedPermanently = true }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            runCatching { adView.destroy() }
        }
    }

    if (adFailedPermanently && !reserveFixedHeight) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (reserveFixedHeight || adLoadedSuccessfully) minHeightDp else 0.dp)
            .testTag(testTagName),
        shape = RoundedCornerShape(18.dp),
        color = if (reserveFixedHeight || adLoadedSuccessfully) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        border = if (reserveFixedHeight || adLoadedSuccessfully) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        } else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (reserveFixedHeight || adLoadedSuccessfully) minHeightDp else 0.dp)
                .padding(vertical = 4.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!adFailedPermanently) {
                AndroidView(
                    factory = {
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        adView
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
