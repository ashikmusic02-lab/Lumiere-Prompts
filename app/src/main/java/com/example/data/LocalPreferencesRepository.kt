package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CategoryItem
import com.example.model.InspirationImage
import com.example.model.ThemePreference
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.promptLensDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "promptlens_user_prefs"
)

class LocalPreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_preference")
    private val languageKey = stringPreferencesKey("language_tag")
    private val lastSeenNotifTsKey = longPreferencesKey("last_seen_notification_ts_ms")
    private val lastCatalogSyncTsKey = longPreferencesKey("last_catalog_sync_ts_ms")
    private val blockedUserIdsKey = stringSetPreferencesKey("blocked_user_ids")
    private val notificationPermissionAskedKey = booleanPreferencesKey("notif_permission_asked")
    private val cachedImagesJsonKey = stringPreferencesKey("cached_published_images_json")
    private val cachedCategoriesJsonKey = stringPreferencesKey("cached_published_categories_json")

    val themePreference: Flow<ThemePreference> = context.promptLensDataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: ThemePreference.DARK.name
        ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.DARK
    }

    val languageTag: Flow<String> = context.promptLensDataStore.data.map { prefs ->
        prefs[languageKey] ?: "en"
    }

    val lastSeenNotificationTimestampMs: Flow<Long> = context.promptLensDataStore.data.map { prefs ->
        prefs[lastSeenNotifTsKey] ?: 0L
    }

    val blockedUserIds: Flow<Set<String>> = context.promptLensDataStore.data.map { prefs ->
        prefs[blockedUserIdsKey] ?: emptySet()
    }

    val notificationPermissionAsked: Flow<Boolean> = context.promptLensDataStore.data.map { prefs ->
        prefs[notificationPermissionAskedKey] ?: false
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.promptLensDataStore.edit { prefs ->
            prefs[themeKey] = preference.name
        }
    }

    suspend fun setLanguageTag(tag: String) {
        context.promptLensDataStore.edit { prefs ->
            prefs[languageKey] = tag
        }
    }

    suspend fun markNotificationsSeen(timestampMs: Long = System.currentTimeMillis()) {
        context.promptLensDataStore.edit { prefs ->
            val current = prefs[lastSeenNotifTsKey] ?: 0L
            if (timestampMs > current) {
                prefs[lastSeenNotifTsKey] = timestampMs
            }
        }
    }

    suspend fun blockUser(userId: String) {
        if (userId.isBlank()) return
        context.promptLensDataStore.edit { prefs ->
            val current = prefs[blockedUserIdsKey] ?: emptySet()
            prefs[blockedUserIdsKey] = current + userId
        }
    }

    suspend fun markNotificationPermissionAsked() {
        context.promptLensDataStore.edit { prefs ->
            prefs[notificationPermissionAskedKey] = true
        }
    }

    suspend fun getLastCatalogSyncTimestampMs(): Long {
        val prefs = context.promptLensDataStore.data.first()
        return prefs[lastCatalogSyncTsKey] ?: 0L
    }

    suspend fun loadCachedImages(): List<InspirationImage> {
        val raw = context.promptLensDataStore.data.first()[cachedImagesJsonKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        InspirationImage(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            shortName = obj.optString("shortName"),
                            description = obj.optString("description"),
                            categoryId = obj.optString("categoryId"),
                            categoryName = obj.optString("categoryName"),
                            imageUrl = obj.optString("imageUrl"),
                            aspectRatio = obj.optDouble("aspectRatio", 0.8),
                            featured = obj.optBoolean("featured", false),
                            trending = obj.optBoolean("trending", false),
                            recommended = obj.optBoolean("recommended", false),
                            collectionName = obj.optString("collectionName"),
                            keywords = obj.optString("keywords"),
                            displayOrder = obj.optLong("displayOrder", 0L),
                            status = obj.optString("status", "published"),
                            viewCount = obj.optLong("viewCount", 0L),
                            likeCount = obj.optLong("likeCount", 0L),
                            commentCount = obj.optLong("commentCount", 0L),
                            unlockCount = obj.optLong("unlockCount", 0L),
                            shareCount = obj.optLong("shareCount", 0L),
                            createdAt = obj.optLong("createdAtSec", 0L).takeIf { it > 0L }?.let { Timestamp(it, 0) },
                            updatedAt = obj.optLong("updatedAtSec", 0L).takeIf { it > 0L }?.let { Timestamp(it, 0) }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun loadCachedCategories(): List<CategoryItem> {
        val raw = context.promptLensDataStore.data.first()[cachedCategoriesJsonKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CategoryItem(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            description = obj.optString("description"),
                            coverImageUrl = obj.optString("coverImageUrl"),
                            imageCount = obj.optLong("imageCount", 0L),
                            sortOrder = obj.optLong("sortOrder", 0L),
                            status = obj.optString("status", "published")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Reconciles the local catalog cache with the fresh published catalog from Firestore.
     * Note: Prompt text is NEVER passed here or written to disk.
     * Returns the list of removed/unpublished image URLs so their cached image files can be evicted.
     */
    suspend fun reconcileAndSaveCatalog(
        freshImages: List<InspirationImage>,
        freshCategories: List<CategoryItem>
    ): Set<String> {
        val previousImages = loadCachedImages()
        val freshUrlSet = freshImages.mapNotNull { it.imageUrl.takeIf(String::isNotBlank) }.toSet()
        val removedUrls = previousImages
            .mapNotNull { it.imageUrl.takeIf(String::isNotBlank) }
            .filter { it !in freshUrlSet }
            .toSet()

        val imagesArray = JSONArray()
        freshImages.forEach { img ->
            imagesArray.put(
                JSONObject().apply {
                    put("id", img.id)
                    put("title", img.title)
                    put("shortName", img.shortName)
                    put("description", img.description)
                    put("categoryId", img.categoryId)
                    put("categoryName", img.categoryName)
                    put("imageUrl", img.imageUrl)
                    put("aspectRatio", img.aspectRatio)
                    put("featured", img.featured)
                    put("trending", img.trending)
                    put("recommended", img.recommended)
                    put("collectionName", img.collectionName)
                    put("keywords", img.keywords)
                    put("displayOrder", img.displayOrder)
                    put("status", img.status)
                    put("viewCount", img.viewCount)
                    put("likeCount", img.likeCount)
                    put("commentCount", img.commentCount)
                    put("unlockCount", img.unlockCount)
                    put("shareCount", img.shareCount)
                    put("createdAtSec", img.createdAt?.seconds ?: 0L)
                    put("updatedAtSec", img.updatedAt?.seconds ?: 0L)
                }
            )
        }

        val categoriesArray = JSONArray()
        freshCategories.forEach { cat ->
            categoriesArray.put(
                JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    put("description", cat.description)
                    put("coverImageUrl", cat.coverImageUrl)
                    put("imageCount", cat.imageCount)
                    put("sortOrder", cat.sortOrder)
                    put("status", cat.status)
                }
            )
        }

        context.promptLensDataStore.edit { prefs ->
            prefs[cachedImagesJsonKey] = imagesArray.toString()
            prefs[cachedCategoriesJsonKey] = categoriesArray.toString()
            prefs[lastCatalogSyncTsKey] = System.currentTimeMillis()
        }

        return removedUrls
    }
}
