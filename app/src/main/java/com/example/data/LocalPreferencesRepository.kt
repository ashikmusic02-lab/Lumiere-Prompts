package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lumiereDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumiere_user_prefs")

class LocalPreferencesRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_preference")
    private val recentlyViewedKey = stringPreferencesKey("recently_viewed_ids")
    private val unlockedPromptsKey = stringSetPreferencesKey("unlocked_prompt_ids")
    private val localLikedKey = stringSetPreferencesKey("local_liked_ids")
    private val localFavoritedKey = stringSetPreferencesKey("local_favorited_ids")

    val themePreference: Flow<ThemePreference> = context.lumiereDataStore.data.map { prefs ->
        val raw = prefs[themeKey] ?: ThemePreference.DARK.name
        ThemePreference.entries.firstOrNull { it.name == raw } ?: ThemePreference.DARK
    }

    val recentlyViewedIds: Flow<List<String>> = context.lumiereDataStore.data.map { prefs ->
        val raw = prefs[recentlyViewedKey].orEmpty()
        if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }
    }

    val localUnlockedIds: Flow<Set<String>> = context.lumiereDataStore.data.map { prefs ->
        prefs[unlockedPromptsKey] ?: emptySet()
    }

    val localLikedIds: Flow<Set<String>> = context.lumiereDataStore.data.map { prefs ->
        prefs[localLikedKey] ?: emptySet()
    }

    val localFavoritedIds: Flow<Set<String>> = context.lumiereDataStore.data.map { prefs ->
        prefs[localFavoritedKey] ?: emptySet()
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        context.lumiereDataStore.edit { prefs ->
            prefs[themeKey] = preference.name
        }
    }

    suspend fun recordRecentlyViewed(imageId: String) {
        if (imageId.isBlank()) return
        context.lumiereDataStore.edit { prefs ->
            val current = prefs[recentlyViewedKey].orEmpty()
                .split(",")
                .filter { it.isNotBlank() && it != imageId }
            val updated = (listOf(imageId) + current).take(20)
            prefs[recentlyViewedKey] = updated.joinToString(",")
        }
    }

    suspend fun markPromptUnlockedLocally(imageId: String) {
        if (imageId.isBlank()) return
        context.lumiereDataStore.edit { prefs ->
            val current = prefs[unlockedPromptsKey] ?: emptySet()
            prefs[unlockedPromptsKey] = current + imageId
        }
    }

    suspend fun toggleLocalLike(imageId: String): Boolean {
        var nowLiked = false
        context.lumiereDataStore.edit { prefs ->
            val current = prefs[localLikedKey] ?: emptySet()
            if (current.contains(imageId)) {
                prefs[localLikedKey] = current - imageId
                nowLiked = false
            } else {
                prefs[localLikedKey] = current + imageId
                nowLiked = true
            }
        }
        return nowLiked
    }

    suspend fun toggleLocalFavorite(imageId: String): Boolean {
        var nowFavorited = false
        context.lumiereDataStore.edit { prefs ->
            val current = prefs[localFavoritedKey] ?: emptySet()
            if (current.contains(imageId)) {
                prefs[localFavoritedKey] = current - imageId
                nowFavorited = false
            } else {
                prefs[localFavoritedKey] = current + imageId
                nowFavorited = true
            }
        }
        return nowFavorited
    }
}
