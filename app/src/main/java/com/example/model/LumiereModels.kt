package com.example.model

import androidx.annotation.Keep
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
 * Represents a photography inspiration item stored in Firestore `images/{imageId}`.
 *
 * SECURITY NOTE (Prompt Lock):
 * `images/{imageId}` intentionally does NOT contain `promptText`.
 * The AI generation prompt is stored exclusively in `imagePrompts/{imageId}` and can only be
 * read after a verified `promptUnlocks/{uid}_{imageId}` document exists for the user.
 */
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
 * Locked prompt payload stored in `imagePrompts/{imageId}`.
 * Readable only when `promptUnlocks/{uid}_{imageId}` exists for the signed-in user (or admin/owner).
 */
@Keep
@IgnoreExtraProperties
data class ImagePromptDocument(
    @DocumentId val id: String = "",
    val promptText: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Photography category stored in `categories/{categoryId}`.
 */
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
 * Derived summary for curated collections displayed on the Home screen.
 */
data class ImageCollectionSummary(
    val name: String,
    val subtitle: String,
    val coverImageUrl: String,
    val itemCount: Int,
    val images: List<InspirationImage>
)

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
 * Moderation report on an inappropriate comment stored in `reports/{reportId}`.
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
 * Verified rewarded-ad prompt unlock receipt stored in `promptUnlocks/{uid}_{imageId}`.
 * Once this document exists in Firestore, security rules grant read access to `imagePrompts/{imageId}`.
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
 * Global application configuration stored in `appSettings/global`.
 */
@Keep
@IgnoreExtraProperties
data class AppConfigSettings(
    @DocumentId val id: String = "global",
    val playStoreUrl: String = BuildConfig.OFFICIAL_PLAY_STORE_URL,
    val requireLoginForPrompts: Boolean = false,
    val allowComments: Boolean = true,
    val welcomeHeadline: String = "Curated Visual Poetry & AI Prompts",
    val welcomeSubheadline: String = "Explore studio-grade photography inspirations and unlock verified generation prompts.",
    val supportEmail: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Global advertisement & monetization configuration stored in `advertisementSettings/global`.
 */
@Keep
@IgnoreExtraProperties
data class AdvertisementSettings(
    @DocumentId val id: String = "global",
    val allAdsEnabled: Boolean = true,
    val homeBannerEnabled: Boolean = true,
    val detailBannerEnabled: Boolean = true,
    val rewardedAdsEnabled: Boolean = true,
    val interstitialAdsEnabled: Boolean = false,
    val testAdMode: Boolean = false,
    val reusePreviousUnlocks: Boolean = true,
    val interstitialFrequency: Long = 5L,
    val adFallbackMessage: String = "Rewarded advertisement is temporarily unavailable. Please check your connection and try again.",
    val bannerAdUnitId: String = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
    val rewardedAdUnitId: String = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
    val interstitialAdUnitId: String = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

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
