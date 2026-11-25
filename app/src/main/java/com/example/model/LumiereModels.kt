package com.example.model

import android.net.Uri
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ThemePreference {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Photography inspiration item stored in Firestore `images/{imageId}`.
 * Exact schema per SCHEMA.md — never contains `promptText`.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class InspirationImage(
    @DocumentId val id: String = "",
    val title: String = "",
    val shortName: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val imageUrl: String = "",
    val aspectRatio: Double = 0.8,
    val featured: Boolean = false,
    val trending: Boolean = false,
    val recommended: Boolean = false,
    val collectionName: String = "",
    val keywords: String = "",
    val displayOrder: Long = 0L,
    val status: String = "published",
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val unlockCount: Long = 0L,
    val shareCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Prompt document stored in `imagePrompts/{imageId}`.
 */
@Keep
@IgnoreExtraProperties
data class ImagePromptDocument(
    @DocumentId val id: String = "",
    val promptText: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Category document stored in `categories/{categoryId}`.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class CategoryItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val coverImageUrl: String = "",
    val imageCount: Long = 0L,
    val sortOrder: Long = 0L,
    val status: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * AI Tool document stored in `aiTools/{toolId}`.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class AiToolItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val url: String = "",
    val logoUrl: String = "",
    val enabled: Boolean = true,
    val sortOrder: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedLogoUrl(): String {
        val direct = logoUrl.trim()
        if (direct.isNotBlank()) return direct
        val rawUrl = url.trim()
        if (rawUrl.isBlank()) return ""
        val normalized = if (rawUrl.startsWith("http://", ignoreCase = true) ||
            rawUrl.startsWith("https://", ignoreCase = true)
        ) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        val host = runCatching { Uri.parse(normalized).host.orEmpty() }
            .getOrDefault("")
            .trim()
            .removePrefix("www.")
        return if (host.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        } else {
            ""
        }
    }
}

/**
 * User profile stored in `users/{userId}`.
 */
@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val likedCount: Long = 0L,
    val favoriteCount: Long = 0L,
    val unlockedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * User like record stored in `likes/{uid}_{imageId}`.
 */
@Keep
@IgnoreExtraProperties
data class LikeRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val imageId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * User favorite record stored in `favorites/{uid}_{imageId}`.
 */
@Keep
@IgnoreExtraProperties
data class FavoriteRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val imageId: String = "",
    val imageTitle: String = "",
    val imageUrl: String = "",
    val categoryName: String = "",
    val aspectRatio: Double = 0.8,
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * User comment stored in `comments/{commentId}`.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class ImageComment(
    @DocumentId val id: String = "",
    val imageId: String = "",
    val userId: String = "",
    val userName: String = "",
    val text: String = "",
    val reported: Boolean = false,
    val reportReason: String = "",
    val adminReply: String = "",
    val status: String = "visible",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Moderation report stored in `reports/{reportId}`.
 */
@Keep
@IgnoreExtraProperties
data class CommentReport(
    @DocumentId val id: String = "",
    val commentId: String = "",
    val imageId: String = "",
    val reporterId: String = "",
    val reason: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Prompt access receipt stored in `promptUnlocks/{uid}_{imageId}`.
 */
@Keep
@IgnoreExtraProperties
data class PromptUnlock(
    @DocumentId val id: String = "",
    val userId: String = "",
    val imageId: String = "",
    val imageTitle: String = "",
    val rewardVerified: Boolean = true,
    val adNetwork: String = "AdMob",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * In-app notification item stored in `notifications/{notificationId}`.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val imageId: String = "",
    val imageUrl: String = "",
    val categoryName: String = "",
    val status: String = "published",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Merged item displayed in the Notifications screen:
 * either a published broadcast notification or an admin reply to the signed-in user's comment.
 */
@Immutable
sealed interface MergedNotificationItem {
    val uniqueKey: String
    val imageId: String
    val timestampMs: Long

    data class BroadcastItem(
        val notification: AppNotification,
        override val timestampMs: Long
    ) : MergedNotificationItem {
        override val uniqueKey: String = "notif_${notification.id}"
        override val imageId: String = notification.imageId
    }

    data class CommentReplyItem(
        val comment: ImageComment,
        val postTitle: String,
        val postThumbnailUrl: String,
        override val timestampMs: Long
    ) : MergedNotificationItem {
        override val uniqueKey: String = "reply_${comment.id}"
        override val imageId: String = comment.imageId
    }
}

/**
 * Global application configuration stored in `appSettings/global`.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class AppConfigSettings(
    @DocumentId val id: String = "global",
    val playStoreUrl: String = BuildConfig.OFFICIAL_PLAY_STORE_URL,
    val requireLoginForPrompts: Boolean = false,
    val allowComments: Boolean = true,
    val welcomeHeadline: String = "",
    val welcomeSubheadline: String = "",
    val supportEmail: String = AppLinks.SUPPORT_EMAIL,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Global advertisement configuration stored in `advertisementSettings/global`.
 * - If the document exists in Firestore and a unit ID is blank (""), that ad format is disabled.
 * - If the document does NOT exist in Firestore (first run), [defaultWhenDocumentMissing] supplies
 *   the built-in BuildConfig ad unit IDs.
 * - Optional App Open ad fields (`appOpenAdsEnabled` and `appOpenAdUnitId`) default to false / ""
 *   so if either field is missing in Firestore, App Open ads stay completely OFF.
 */
@Immutable
@Keep
@IgnoreExtraProperties
data class AdvertisementSettings(
    @DocumentId val id: String = "global",
    val allAdsEnabled: Boolean = true,
    val homeBannerEnabled: Boolean = true,
    val detailBannerEnabled: Boolean = true,
    val rewardedAdsEnabled: Boolean = true,
    val interstitialAdsEnabled: Boolean = true,
    val appOpenAdsEnabled: Boolean = false,
    val testAdMode: Boolean = false,
    val reusePreviousUnlocks: Boolean = false,
    val interstitialFrequency: Long = 3L,
    val adFallbackMessage: String = "",
    val bannerAdUnitId: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
    val rewardedAdUnitId: String = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
    val interstitialAdUnitId: String = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
    val appOpenAdUnitId: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun defaultWhenDocumentMissing(): AdvertisementSettings = AdvertisementSettings(
            allAdsEnabled = true,
            homeBannerEnabled = true,
            detailBannerEnabled = true,
            rewardedAdsEnabled = true,
            interstitialAdsEnabled = true,
            appOpenAdsEnabled = false,
            testAdMode = false,
            reusePreviousUnlocks = false,
            interstitialFrequency = 3L,
            bannerAdUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
            rewardedAdUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            interstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            appOpenAdUnitId = ""
        )
    }
}

/**
 * Administrator role record stored in `adminUsers/{uid}` for the separate Admin Panel project.
 */
@Keep
@IgnoreExtraProperties
data class AdminUser(
    @DocumentId val id: String = "",
    val role: String = "moderator",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
