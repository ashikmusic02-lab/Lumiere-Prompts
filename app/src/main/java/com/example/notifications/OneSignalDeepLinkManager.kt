package com.example.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.auth.GoogleSignInManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Firebase Cloud Messaging & Deep-Link Router (Requirement E):
 * - Subscribes to FCM topic `"all"` on app start and ensures notification channel `"general"` exists.
 * - Handles incoming notification and deep-link intents in all 3 app states (killed, background, foreground):
 *   - Data key `"link"` (e.g. `promptlens://image/{id}`)
 *   - Data key `"imageId"` / `"image_id"` (plain id)
 *   - Custom URI scheme `promptlens://image/{id}`
 * - Zero OneSignal or ONESIGNAL_APP_ID usage.
 */
object OneSignalDeepLinkManager {
    private const val TAG = "PromptLensMessaging"
    private const val TOPIC_ALL = "all"

    private val _pendingImageDeepLink = MutableStateFlow<String?>(null)
    val pendingImageDeepLink: StateFlow<String?> = _pendingImageDeepLink.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        val appContext = context.applicationContext ?: context
        PromptLensMessagingService.ensureGeneralNotificationChannel(appContext)
        if (initialized) return
        initialized = true
        if (GoogleSignInManager.isFirebaseConfigured(appContext)) {
            ensureSubscribedToAllTopic()
        }
    }

    fun ensureSubscribedToAllTopic() {
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to FCM topic '$TOPIC_ALL'")
                    } else {
                        Log.w(TAG, "FCM topic '$TOPIC_ALL' subscription warning: ${task.exception?.message}")
                    }
                }
        }.onFailure { e ->
            Log.w(TAG, "FCM topic '$TOPIC_ALL' subscription skipped: ${e.message}")
        }
    }

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Check direct intent extras or FCM background notification extras ("imageId", "image_id", "link")
        val extras = intent.extras
        val extraImageId = intent.getStringExtra("imageId")
            ?: intent.getStringExtra("image_id")
            ?: extras?.getString("imageId")
            ?: extras?.getString("image_id")

        if (!extraImageId.isNullOrBlank()) {
            val extracted = extractImageIdFromLinkString(extraImageId) ?: extraImageId.trim()
            if (extracted.isNotBlank()) {
                dispatchDeepLinkImageId(extracted)
                return
            }
        }

        val extraLink = intent.getStringExtra("link")
            ?: extras?.getString("link")
            ?: extras?.getString("gcm.notification.link")
        if (!extraLink.isNullOrBlank()) {
            extractImageIdFromLinkString(extraLink)?.let { id ->
                dispatchDeepLinkImageId(id)
                return
            }
        }

        // 2. Check URI data (promptlens://image/{imageId})
        val dataUri = intent.data
        if (dataUri != null) {
            extractImageIdFromUri(dataUri)?.let { id ->
                dispatchDeepLinkImageId(id)
            }
        }
    }

    fun extractImageIdFromLinkString(rawLink: String?): String? {
        val cleaned = rawLink?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        if (cleaned.startsWith("promptlens://", ignoreCase = true)) {
            return runCatching { Uri.parse(cleaned) }.getOrNull()?.let(::extractImageIdFromUri)
        }
        return null
    }

    fun extractImageIdFromUri(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        // Never redirect to or accept arbitrary untrusted websites
        if (scheme == "promptlens" && uri.host?.lowercase() == "image") {
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
