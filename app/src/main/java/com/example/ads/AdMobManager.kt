package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.model.AdvertisementSettings
import com.example.ui.theme.IndigoSoftBorder
import com.example.ui.theme.IndigoSurfaceDark
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
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
    private const val INTERSTITIAL_COOLDOWN_MS = 45_000L
    private const val APP_OPEN_COOLDOWN_MS = 30L * 60L * 1000L // 30 minutes
    private const val APP_OPEN_MAX_AGE_MS = 4L * 60L * 60L * 1000L // 4 hours
    private const val AD_PREFS_NAME = "promptlens_ad_prefs"
    private const val KEY_FIRST_LAUNCH_COMPLETED = "first_launch_completed"
    private const val KEY_LAST_APP_OPEN_SHOWN_MS = "last_app_open_shown_ms"

    // Official Google AdMob Test Ad Unit IDs (used when testAdMode == true in Firestore)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

    @Volatile
    private var mobileAdsInitialized = false

    @Volatile
    private var webViewSilencerInstalled = false

    private val _canRequestAdsFlow = MutableStateFlow(false)
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

    private var cachedAppOpenAd: AppOpenAd? = null
    private var cachedAppOpenUnitId: String? = null
    private var cachedAppOpenLoadedAtMs: Long = 0L
    private var isPreloadingAppOpen = false

    @Volatile
    private var isShowingFullScreenAd = false

    // Session tracking
    private var postsOpenedInSessionCount: Int = 0
    private var lastInterstitialShownAtMs: Long = 0L
    private var rewardedAdShownInCurrentVisit: Boolean = false

    @Volatile
    private var launchSessionChecked = false

    @Volatile
    private var isFirstLaunchAfterInstallSession = false

    /**
     * Checks and records whether this process session is the very first app launch after install.
     * Never shows an App Open ad during the very first launch after install.
     */
    fun markAndCheckIsFirstLaunchSession(context: Context): Boolean {
        if (!launchSessionChecked) {
            synchronized(this) {
                if (!launchSessionChecked) {
                    val prefs = context.applicationContext
                        .getSharedPreferences(AD_PREFS_NAME, Context.MODE_PRIVATE)
                    val alreadyCompleted = prefs.getBoolean(KEY_FIRST_LAUNCH_COMPLETED, false)
                    if (!alreadyCompleted) {
                        isFirstLaunchAfterInstallSession = true
                        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_COMPLETED, true).apply()
                    } else {
                        isFirstLaunchAfterInstallSession = false
                    }
                    launchSessionChecked = true
                }
            }
        }
        return isFirstLaunchAfterInstallSession
    }

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
     * Requests UMP consent asynchronously in the background without delaying the UI, and initializes
     * the AdMob SDK only after consent allows it.
     */
    fun requestConsentAndInitializeAds(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        markAndCheckIsFirstLaunchSession(activity)
        purgeCorruptedWebViewVariationsSeeds(activity)

        runCatching {
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

            // If previous session already gathered consent, initialize immediately
            if (runCatching { consentInfo.canRequestAds() }.getOrDefault(false)) {
                _canRequestAdsFlow.value = true
                ensureMobileAdsInitialized(activity)
            }

            if (isRunningOnEmulator()) {
                _canRequestAdsFlow.value = true
                ensureMobileAdsInitialized(activity)
                return
            }

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
            runCatching { consentInfo.canRequestAds() }.getOrDefault(false)
        } else {
            runCatching { consentInfo.canRequestAds() }.getOrDefault(true)
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
        installGlobalAdWebViewConsoleSilencer(context)
        if (mobileAdsInitialized) return
        runCatching {
            val appCtx = context.applicationContext ?: context
            runCatching {
                val requestConfig = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(requestConfig)
            }
            MobileAds.initialize(appCtx) {
                Log.d(TAG, "MobileAds initialized")
                runCatching { MobileAds.setAppMuted(true) }
                purgeCorruptedWebViewVariationsSeeds(appCtx)
            }
            mobileAdsInitialized = true
            mainHandler.postDelayed({ purgeCorruptedWebViewVariationsSeeds(appCtx) }, 2_500L)
            mainHandler.postDelayed({ purgeCorruptedWebViewVariationsSeeds(appCtx) }, 7_000L)
        }.onFailure { e ->
            Log.w(TAG, "MobileAds initialization warning: ${e.message}")
        }
    }

    /**
     * Installs a process-wide listener on all Activities (including AdActivity and MainActivity)
     * and ViewGroups to wrap any internal AdMob WebView's WebChromeClient so third-party HTML5/SVG/Video
     * ad creative JS console messages (e.g. `E/Ads: JS: Error: <svg> attribute viewBox...` or
     * `E/Ads: JS: Uncaught TypeError...`) are consumed silently instead of polluting logcat at ERROR level.
     */
    private fun installGlobalAdWebViewConsoleSilencer(context: Context) {
        if (webViewSilencerInstalled) return
        val app = (context.applicationContext as? Application) ?: return
        webViewSilencerInstalled = true

        runCatching {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    attachSilencerToActivity(activity)
                }

                override fun onActivityStarted(activity: Activity) {
                    attachSilencerToActivity(activity)
                }

                override fun onActivityResumed(activity: Activity) {
                    attachSilencerToActivity(activity)
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
        findActivity(context)?.let { attachSilencerToActivity(it) }
    }

    private fun attachSilencerToActivity(activity: Activity) {
        runCatching {
            val decorView = activity.window?.decorView ?: return
            silenceWebViewsRecursively(decorView)
            mainHandler.postDelayed({ silenceWebViewsRecursively(decorView) }, 150L)
            mainHandler.postDelayed({ silenceWebViewsRecursively(decorView) }, 600L)
        }
    }

    fun silenceWebViewsRecursively(root: View?) {
        if (root == null) return
        runCatching {
            if (root is WebView) {
                silenceSingleWebView(root)
            }
            if (root is ViewGroup) {
                root.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        silenceWebViewsRecursively(child)
                        mainHandler.postDelayed({ silenceWebViewsRecursively(child) }, 60L)
                        mainHandler.postDelayed({ silenceWebViewsRecursively(child) }, 300L)
                        mainHandler.postDelayed({ silenceWebViewsRecursively(child) }, 1000L)
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) {}
                })
                for (i in 0 until root.childCount) {
                    silenceWebViewsRecursively(root.getChildAt(i))
                }
            }
        }
    }

    private fun silenceSingleWebView(webView: WebView) {
        runCatching {
            val existingClient = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { webView.webChromeClient }.getOrNull()
            } else {
                null
            }
            if (existingClient !is SilencingWebChromeClient) {
                webView.webChromeClient = SilencingWebChromeClient(existingClient)
            }
            runCatching {
                webView.evaluateJavascript(
                    "try{window.onerror=function(){return true;};if(window.console){window.console.error=function(){};window.console.warn=function(){};}}catch(e){}",
                    null
                )
            }
        }
    }

    private class SilencingWebChromeClient(
        private val delegate: WebChromeClient?
    ) : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            // Return true to indicate the console message has been handled and suppress `E/Ads: JS: ...`
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
            // Suppressed
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            if (view != null && (newProgress == 15 || newProgress == 50 || newProgress == 100)) {
                runCatching {
                    view.evaluateJavascript(
                        "try{window.onerror=function(){return true;};if(window.console){window.console.error=function(){};window.console.warn=function(){};}}catch(e){}",
                        null
                    )
                }
            }
            runCatching { delegate?.onProgressChanged(view, newProgress) ?: super.onProgressChanged(view, newProgress) }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            runCatching { delegate?.onReceivedTitle(view, title) ?: super.onReceivedTitle(view, title) }
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            runCatching { delegate?.onReceivedIcon(view, icon) ?: super.onReceivedIcon(view, icon) }
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            runCatching { delegate?.onShowCustomView(view, callback) ?: super.onShowCustomView(view, callback) }
        }

        override fun onHideCustomView() {
            runCatching { delegate?.onHideCustomView() ?: super.onHideCustomView() }
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            return runCatching {
                delegate?.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                    ?: super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
            }.getOrDefault(false)
        }

        override fun onCloseWindow(window: WebView?) {
            runCatching { delegate?.onCloseWindow(window) ?: super.onCloseWindow(window) }
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            return runCatching {
                delegate?.onJsAlert(view, url, message, result) ?: super.onJsAlert(view, url, message, result)
            }.getOrDefault(false)
        }

        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            return runCatching {
                delegate?.onJsConfirm(view, url, message, result) ?: super.onJsConfirm(view, url, message, result)
            }.getOrDefault(false)
        }

        override fun onJsPrompt(
            view: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?
        ): Boolean {
            return runCatching {
                delegate?.onJsPrompt(view, url, message, defaultValue, result)
                    ?: super.onJsPrompt(view, url, message, defaultValue, result)
            }.getOrDefault(false)
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            runCatching { delegate?.onPermissionRequest(request) ?: super.onPermissionRequest(request) }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            runCatching { delegate?.onPermissionRequestCanceled(request) ?: super.onPermissionRequestCanceled(request) }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            runCatching {
                delegate?.onGeolocationPermissionsShowPrompt(origin, callback)
                    ?: super.onGeolocationPermissionsShowPrompt(origin, callback)
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            runCatching { delegate?.onGeolocationPermissionsHidePrompt() ?: super.onGeolocationPermissionsHidePrompt() }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            return runCatching {
                delegate?.onShowFileChooser(webView, filePathCallback, fileChooserParams)
                    ?: super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            }.getOrDefault(false)
        }

        override fun getDefaultVideoPoster(): Bitmap? {
            return runCatching { delegate?.defaultVideoPoster ?: super.getDefaultVideoPoster() }.getOrNull()
        }

        override fun getVideoLoadingProgressView(): View? {
            return runCatching { delegate?.videoLoadingProgressView ?: super.getVideoLoadingProgressView() }.getOrNull()
        }
    }

    private fun isValidAdUnitId(raw: String): Boolean {
        val cleaned = raw.trim()
        return cleaned.isNotBlank() &&
            !cleaned.startsWith("YOUR_") &&
            !cleaned.startsWith("REPLACE_") &&
            cleaned.contains("/")
    }

    /**
     * Resolves the Banner ad unit ID:
     * - If `allAdsEnabled == false` or both banner switches are off -> `""` (never loads or shows).
     * - If `testAdMode == true` -> official Google test unit ID.
     * - Otherwise uses `settings.bannerAdUnitId`; if blank or invalid, returns `""` so the ad is skipped silently.
     */
    fun resolveBannerUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || (!settings.homeBannerEnabled && !settings.detailBannerEnabled)) return ""
        if (settings.testAdMode) return TEST_BANNER_AD_UNIT_ID
        val candidate = settings.bannerAdUnitId.trim()
        return if (isValidAdUnitId(candidate)) candidate else ""
    }

    /**
     * Resolves the Rewarded ad unit ID:
     * - If `allAdsEnabled == false` or `rewardedAdsEnabled == false` -> `""`.
     * - If `testAdMode == true` -> official Google test unit ID.
     * - Otherwise uses `settings.rewardedAdUnitId`; if blank or invalid, returns `""` so the ad is skipped silently.
     */
    fun resolveRewardedUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.rewardedAdsEnabled) return ""
        if (settings.testAdMode) return TEST_REWARDED_AD_UNIT_ID
        val candidate = settings.rewardedAdUnitId.trim()
        return if (isValidAdUnitId(candidate)) candidate else ""
    }

    /**
     * Resolves the Interstitial ad unit ID:
     * - If `allAdsEnabled == false` or `interstitialAdsEnabled == false` -> `""`.
     * - If `testAdMode == true` -> official Google test unit ID.
     * - Otherwise uses `settings.interstitialAdUnitId`; if blank or invalid, returns `""` so the ad is skipped silently.
     */
    fun resolveInterstitialUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.interstitialAdsEnabled) return ""
        if (settings.testAdMode) return TEST_INTERSTITIAL_AD_UNIT_ID
        val candidate = settings.interstitialAdUnitId.trim()
        return if (isValidAdUnitId(candidate)) candidate else ""
    }

    /**
     * Resolves the App Open Ad unit ID ONLY when:
     * - `settings.allAdsEnabled == true`
     * - `settings.appOpenAdsEnabled == true` (absent = false = completely OFF)
     * - If `testAdMode == true`, uses [TEST_APP_OPEN_AD_UNIT_ID]
     * - Otherwise requires a valid non-blank `settings.appOpenAdUnitId` (blank or invalid = skipped silently)
     */
    fun resolveAppOpenUnitId(settings: AdvertisementSettings): String {
        if (!settings.allAdsEnabled || !settings.appOpenAdsEnabled) {
            return ""
        }
        if (settings.testAdMode) return TEST_APP_OPEN_AD_UNIT_ID
        val configuredId = settings.appOpenAdUnitId.trim()
        return if (isValidAdUnitId(configuredId)) configuredId else ""
    }

    /**
     * Preloads an App Open ad in the background if enabled and configured in `advertisementSettings/global`.
     */
    fun preloadAppOpenAdIfNeeded(context: Context, settings: AdvertisementSettings) {
        val unitId = resolveAppOpenUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(context)) {
            if (unitId.isBlank()) {
                cachedAppOpenAd = null
                cachedAppOpenUnitId = null
            }
            return
        }
        val now = System.currentTimeMillis()
        val isCurrentAdFresh = cachedAppOpenAd != null &&
            cachedAppOpenUnitId == unitId &&
            (now - cachedAppOpenLoadedAtMs) < APP_OPEN_MAX_AGE_MS
        if (isCurrentAdFresh || isPreloadingAppOpen) return

        ensureMobileAdsInitialized(context)
        isPreloadingAppOpen = true

        runCatching {
            AppOpenAd.load(
                context.applicationContext,
                unitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isPreloadingAppOpen = false
                        cachedAppOpenAd = ad
                        cachedAppOpenUnitId = unitId
                        cachedAppOpenLoadedAtMs = System.currentTimeMillis()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isPreloadingAppOpen = false
                        cachedAppOpenAd = null
                    }
                }
            )
        }.onFailure {
            isPreloadingAppOpen = false
        }
    }

    /**
     * Shows the preloaded App Open ad when the app comes to the foreground / cold start ONLY if:
     * 1. Not the very first app launch after install
     * 2. Ads are allowed (UMP consent)
     * 3. `advertisementSettings/global` has `allAdsEnabled == true`, `appOpenAdsEnabled == true`,
     *    and a valid unit ID (or testAdMode == true)
     * 4. The App Open ad is ALREADY loaded (never blocks startup; if not ready, skips silently and preloads)
     * 5. At least 30 minutes have passed since the last App Open ad was shown
     */
    fun showAppOpenAdIfEligible(
        activity: Activity,
        settings: AdvertisementSettings,
        onComplete: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }
        if (markAndCheckIsFirstLaunchSession(activity)) {
            preloadAppOpenAdIfNeeded(activity, settings)
            onComplete()
            return
        }
        if (isShowingFullScreenAd) {
            onComplete()
            return
        }

        val unitId = resolveAppOpenUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(activity)) {
            if (unitId.isBlank()) {
                cachedAppOpenAd = null
                cachedAppOpenUnitId = null
            }
            onComplete()
            return
        }

        val prefs = activity.applicationContext.getSharedPreferences(AD_PREFS_NAME, Context.MODE_PRIVATE)
        val lastShownMs = prefs.getLong(KEY_LAST_APP_OPEN_SHOWN_MS, 0L)
        val now = System.currentTimeMillis()
        if (lastShownMs > 0L && (now - lastShownMs) < APP_OPEN_COOLDOWN_MS) {
            preloadAppOpenAdIfNeeded(activity, settings)
            onComplete()
            return
        }

        val ad = cachedAppOpenAd
        val isAdFresh = ad != null &&
            cachedAppOpenUnitId == unitId &&
            (now - cachedAppOpenLoadedAtMs) < APP_OPEN_MAX_AGE_MS

        if (!isAdFresh || ad == null) {
            cachedAppOpenAd = null
            preloadAppOpenAdIfNeeded(activity, settings)
            onComplete()
            return
        }

        cachedAppOpenAd = null
        isShowingFullScreenAd = true
        prefs.edit().putLong(KEY_LAST_APP_OPEN_SHOWN_MS, now).apply()

        var completed = false
        val finishOnce: () -> Unit = {
            if (!completed) {
                completed = true
                onComplete()
            }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                preloadAppOpenAdIfNeeded(activity, settings)
                finishOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd = false
                preloadAppOpenAdIfNeeded(activity, settings)
                finishOnce()
            }
        }
        runCatching {
            ad.show(activity)
        }.onFailure {
            isShowingFullScreenAd = false
            preloadAppOpenAdIfNeeded(activity, settings)
            finishOnce()
        }
    }

    /**
     * Called when an Image Detail screen is opened:
     * - Preloads Rewarded (and fallback Interstitial) ads in the background if enabled.
     * - Never shows an interstitial ad when opening a post.
     */
    fun onDetailScreenEntered(context: Context, settings: AdvertisementSettings) {
        postsOpenedInSessionCount++
        rewardedAdShownInCurrentVisit = false

        if (!settings.allAdsEnabled) {
            cachedRewardedAd = null
            cachedRewardedUnitId = null
            cachedInterstitialAd = null
            cachedInterstitialUnitId = null
            cachedAppOpenAd = null
            cachedAppOpenUnitId = null
            return
        }

        if (settings.rewardedAdsEnabled) {
            preloadRewardedAdIfNeeded(context, settings)
        } else {
            cachedRewardedAd = null
            cachedRewardedUnitId = null
        }

        if (settings.interstitialAdsEnabled) {
            preloadInterstitialIfNeeded(context, settings)
        } else {
            cachedInterstitialAd = null
            cachedInterstitialUnitId = null
        }
    }

    /**
     * Preloads a Rewarded Ad in the background so that when the user taps "Get Prompt",
     * the ad is ready immediately.
     */
    fun preloadRewardedAdIfNeeded(context: Context, settings: AdvertisementSettings) {
        if (!settings.allAdsEnabled || !settings.rewardedAdsEnabled) {
            cachedRewardedAd = null
            cachedRewardedUnitId = null
            return
        }
        val unitId = resolveRewardedUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(context)) {
            if (unitId.isBlank()) {
                cachedRewardedAd = null
                cachedRewardedUnitId = null
            }
            return
        }
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
                        isPreloadingRewarded = false
                        cachedRewardedAd = null
                    }
                }
            )
        }.onFailure {
            isPreloadingRewarded = false
        }
    }

    /**
     * On "Get Prompt":
     * - If `rewardedAdsEnabled == true` and the rewarded ad is already loaded, shows the rewarded ad, then reveals the prompt.
     * - Otherwise, if `interstitialAdsEnabled == true` and an interstitial ad is already loaded (and cooldown elapsed),
     *   uses the interstitial ad as the fallback on "Get Prompt", then reveals the prompt.
     * - If neither ad is ready or fails, reveals the prompt immediately; never a spinner, never forced.
     */
    fun showRewardedOrInterstitialForPrompt(
        context: Context,
        settings: AdvertisementSettings,
        userId: String?,
        imageId: String,
        onProceedToPrompt: (adNetworkUsed: String) -> Unit
    ) {
        if (!settings.allAdsEnabled) {
            cachedRewardedAd = null
            cachedInterstitialAd = null
            onProceedToPrompt("none")
            return
        }
        if (!settings.rewardedAdsEnabled) {
            cachedRewardedAd = null
        }
        if (!settings.interstitialAdsEnabled) {
            cachedInterstitialAd = null
        }

        if (!settings.rewardedAdsEnabled && !settings.interstitialAdsEnabled) {
            onProceedToPrompt("none")
            return
        }

        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed || !canRequestAds(context)) {
            onProceedToPrompt("none")
            return
        }

        val rewardedUnitId = resolveRewardedUnitId(settings)
        val interstitialUnitId = resolveInterstitialUnitId(settings)

        // 1. Show preloaded Rewarded Ad immediately if enabled & ready
        val readyRewarded = cachedRewardedAd
        if (rewardedUnitId.isNotBlank() && readyRewarded != null && cachedRewardedUnitId == rewardedUnitId) {
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

        // 2. Otherwise use preloaded Interstitial Ad as fallback on "Get Prompt" if interstitialAdsEnabled & ready & 45s cooldown elapsed
        val readyInterstitial = cachedInterstitialAd
        val now = System.currentTimeMillis()
        val cooldownElapsed = (now - lastInterstitialShownAtMs) >= INTERSTITIAL_COOLDOWN_MS
        if (interstitialUnitId.isNotBlank() &&
            readyInterstitial != null &&
            cachedInterstitialUnitId == interstitialUnitId &&
            cooldownElapsed &&
            !isShowingFullScreenAd
        ) {
            cachedInterstitialAd = null
            showInterstitialAdInstance(
                activity = activity,
                interstitialAd = readyInterstitial,
                settings = settings,
                onProceedToPrompt = onProceedToPrompt
            )
            return
        }

        // 3. If neither ad is preloaded/ready yet, trigger background preload and reveal immediately (never a spinner)
        preloadRewardedAdIfNeeded(context, settings)
        preloadInterstitialIfNeeded(context, settings)
        onProceedToPrompt("none")
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
        isShowingFullScreenAd = true
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
                    isShowingFullScreenAd = false
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
            isShowingFullScreenAd = false
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
        isShowingFullScreenAd = true
        lastInterstitialShownAtMs = System.currentTimeMillis()
        runCatching {
            var handled = false
            val completeOnce: (String) -> Unit = { network ->
                if (!handled) {
                    handled = true
                    isShowingFullScreenAd = false
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
            isShowingFullScreenAd = false
            preloadInterstitialIfNeeded(activity, settings)
            onProceedToPrompt("none")
        }
    }

    /**
     * Called when the user leaves an Image Detail screen back to the list:
     * Ensures interstitials are preloaded in the background for the next N-th post opened.
     */
    fun onDetailScreenExited(
        context: Context,
        settings: AdvertisementSettings,
        isCommentInputActive: Boolean
    ) {
        if (!settings.allAdsEnabled || !settings.interstitialAdsEnabled) {
            cachedInterstitialAd = null
            cachedInterstitialUnitId = null
            return
        }
        preloadInterstitialIfNeeded(context, settings)
    }

    fun preloadInterstitialIfNeeded(context: Context, settings: AdvertisementSettings) {
        if (!settings.allAdsEnabled || !settings.interstitialAdsEnabled) {
            cachedInterstitialAd = null
            cachedInterstitialUnitId = null
            return
        }
        val unitId = resolveInterstitialUnitId(settings)
        if (unitId.isBlank() || !canRequestAds(context)) {
            if (unitId.isBlank()) {
                cachedInterstitialAd = null
                cachedInterstitialUnitId = null
            }
            return
        }
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
                        isPreloadingInterstitial = false
                        cachedInterstitialAd = null
                    }
                }
            )
        }.onFailure {
            isPreloadingInterstitial = false
        }
    }
}

/**
 * Rounded 20.dp Banner Ad Container with fixed reserved height and tidy spacing:
 * - Always reserves fixed height when enabled so that even if it fails to load,
 *   tidy empty spacing is preserved and the layout never jumps or breaks.
 */
@Composable
fun LumiereBannerAdSlot(
    settings: AdvertisementSettings,
    enabledForPlacement: Boolean,
    modifier: Modifier = Modifier,
    reserveFixedHeight: Boolean = true,
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

    val shouldShowSlot = settings.allAdsEnabled &&
        enabledForPlacement &&
        primaryUnitId.isNotBlank()

    if (!shouldShowSlot) return

    var activeUseLargeBanner by remember(primaryUnitId, useLargeNativeBanner) {
        mutableStateOf(useLargeNativeBanner)
    }
    var adFailedPermanently by remember(primaryUnitId) { mutableStateOf(false) }

    val targetAdSize = if (activeUseLargeBanner) AdSize.LARGE_BANNER else AdSize.BANNER
    val minHeightDp = if (activeUseLargeBanner) 104.dp else 60.dp

    val adView = remember(primaryUnitId, activeUseLargeBanner, canRequestAds) {
        if (!canRequestAds) {
            null
        } else {
            AdMobManager.ensureMobileAdsInitialized(context)
            AdView(context).apply {
                setAdSize(targetAdSize)
                adUnitId = primaryUnitId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        adFailedPermanently = false
                        AdMobManager.silenceWebViewsRecursively(this@apply)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (activeUseLargeBanner) {
                            activeUseLargeBanner = false
                        } else {
                            adFailedPermanently = true
                        }
                    }
                }
                AdMobManager.silenceWebViewsRecursively(this)
                runCatching { loadAd(AdRequest.Builder().build()) }
                    .onFailure { adFailedPermanently = true }
            }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            runCatching { adView?.destroy() }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp)
            .testTag(testTagName),
        shape = RoundedCornerShape(20.dp),
        color = IndigoSurfaceDark.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, IndigoSoftBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeightDp)
                .padding(vertical = 4.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!adFailedPermanently && adView != null) {
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

/**
 * Plain Home Banner Ad slot without any surrounding box/container or reserved empty space:
 * - Shows nothing (0dp) when disabled, loading, or failed.
 * - When the ad loads, renders the banner directly using [loadedModifier].
 */
@Composable
fun HomePlainBannerAdSlot(
    settings: AdvertisementSettings,
    enabledForPlacement: Boolean,
    loadedModifier: Modifier = Modifier,
    onAdLoadedChanged: (Boolean) -> Unit = {},
    testTagName: String = "home_bottom_banner_ad"
) {
    val context = LocalContext.current
    val canRequestAds by AdMobManager.canRequestAdsFlow.collectAsStateWithFallback(
        initial = AdMobManager.canRequestAds(context)
    )
    val primaryUnitId = remember(settings) {
        AdMobManager.resolveBannerUnitId(settings)
    }

    val shouldShowSlot = settings.allAdsEnabled &&
        enabledForPlacement &&
        primaryUnitId.isNotBlank()

    var isAdLoaded by remember(primaryUnitId, shouldShowSlot, canRequestAds) { mutableStateOf(false) }
    var adFailedPermanently by remember(primaryUnitId, shouldShowSlot, canRequestAds) { mutableStateOf(false) }

    LaunchedEffect(shouldShowSlot, isAdLoaded, adFailedPermanently) {
        onAdLoadedChanged(shouldShowSlot && isAdLoaded && !adFailedPermanently)
    }

    if (!shouldShowSlot || adFailedPermanently) return

    val adView = remember(primaryUnitId, canRequestAds) {
        if (!canRequestAds) {
            null
        } else {
            AdMobManager.ensureMobileAdsInitialized(context)
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = primaryUnitId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        adFailedPermanently = false
                        isAdLoaded = true
                        AdMobManager.silenceWebViewsRecursively(this@apply)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAdLoaded = false
                        adFailedPermanently = true
                    }
                }
                AdMobManager.silenceWebViewsRecursively(this)
                runCatching { loadAd(AdRequest.Builder().build()) }
                    .onFailure {
                        isAdLoaded = false
                        adFailedPermanently = true
                    }
            }
        }
    }

    DisposableEffect(adView) {
        onDispose {
            onAdLoadedChanged(false)
            runCatching { adView?.destroy() }
        }
    }

    if (adView != null) {
        Box(
            modifier = if (isAdLoaded) {
                loadedModifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag(testTagName)
            } else {
                Modifier.size(0.dp)
            },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = {
                    (adView.parent as? ViewGroup)?.removeView(adView)
                    adView
                },
                modifier = if (isAdLoaded) Modifier.fillMaxWidth() else Modifier.size(0.dp)
            )
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateWithFallback(initial: T): androidx.compose.runtime.State<T> {
    val state = remember { mutableStateOf(initial) }
    LaunchedEffect(this) {
        collect { state.value = it }
    }
    return state
}
