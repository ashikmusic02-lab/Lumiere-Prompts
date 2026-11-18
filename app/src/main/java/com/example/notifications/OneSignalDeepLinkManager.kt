package com.example.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OneSignalDeepLinkManager {
    private const val TAG = "OneSignalDeepLink"

    private val _pendingImageDeepLink = MutableStateFlow<String?>(null)
    val pendingImageDeepLink: StateFlow<String?> = _pendingImageDeepLink.asStateFlow()

    private val _isOneSignalConfigured = MutableStateFlow(false)
    val isOneSignalConfigured: StateFlow<Boolean> = _isOneSignalConfigured.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        val appId = BuildConfig.ONESIGNAL_APP_ID.trim()
        val hasValidAppId = appId.isNotBlank() &&
            appId != "YOUR_ONESIGNAL_APP_ID" &&
            !appId.startsWith("REPLACE_")

        _isOneSignalConfigured.value = hasValidAppId

        if (!hasValidAppId) {
            Log.i(TAG, "OneSignal App ID not yet configured in .env; deep-link intent router remains active.")
            initialized = true
            return
        }

        runCatching {
            OneSignal.initWithContext(context.applicationContext, appId)
            OneSignal.Notifications.addClickListener(object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    val data = event.notification.additionalData
                    val imageIdFromData = data?.optString("imageId", "")
                        ?.ifBlank { data.optString("image_id", "") }
                        .orEmpty()

                    if (imageIdFromData.isNotBlank()) {
                        dispatchDeepLinkImageId(imageIdFromData)
                        return
                    }

                    val launchUrl = event.result.url
                    if (!launchUrl.isNullOrBlank()) {
                        extractImageIdFromUri(Uri.parse(launchUrl))?.let { id ->
                            dispatchDeepLinkImageId(id)
                        }
                    }
                }
            })
            initialized = true
            Log.d(TAG, "OneSignal initialized and click listener attached.")
        }.onFailure { e ->
            Log.w(TAG, "OneSignal initialization skipped: ${e.message}")
        }
    }

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Check direct intent extras (from notification payload or internal deep link)
        val extraImageId = intent.getStringExtra("imageId")
            ?: intent.getStringExtra("image_id")
        if (!extraImageId.isNullOrBlank()) {
            dispatchDeepLinkImageId(extraImageId)
            return
        }

        // 2. Check URI data (promptlens://image/{imageId})
        val dataUri = intent.data
        if (dataUri != null) {
            extractImageIdFromUri(dataUri)?.let { id ->
                dispatchDeepLinkImageId(id)
            }
        }
    }

    fun extractImageIdFromUri(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        // Never redirect to or accept arbitrary untrusted websites
        if (scheme == "promptlens" && uri.host == "image") {
            return uri.lastPathSegment?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("id")
        }
        return null
    }

    fun dispatchDeepLinkImageId(imageId: String) {
        val cleaned = imageId.trim()
        if (cleaned.isNotBlank()) {
            _pendingImageDeepLink.value = cleaned
        }
    }

    fun consumeDeepLink() {
        _pendingImageDeepLink.value = null
    }
}
