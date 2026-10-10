package com.example.data.preferences

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.SortOrder
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_user_prefs")

@Immutable
data class UserPreferences(
    val minDurationSeconds: Int = 50, // Por defecto 50s según requisitos para filtrar tonos/WhatsApp
    val excludedFolders: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val customAccentColorArgb: Long = 0xFF1DB954L, // Verde esmeralda vibrante estilo Spotify por defecto
    val useDynamicAlbumPalette: Boolean = true,
    val customBackgroundUri: String? = null,
    val backgroundBlurRadius: Float = 14f,
    val backgroundDimAlpha: Float = 0.65f,
    val solidBackgroundColorArgb: Long = 0xFF0E1318L,
    val gaplessPlayback: Boolean = true,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val sortOrder: SortOrder = SortOrder.TITLE_ASC,
    val listMemoryLimit: Int = 500
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val MIN_DURATION_SEC = intPreferencesKey("min_duration_seconds")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR_ARGB = longPreferencesKey("accent_color_argb")
        val USE_DYNAMIC_PALETTE = booleanPreferencesKey("use_dynamic_palette")
        val CUSTOM_BG_URI = stringPreferencesKey("custom_bg_uri")
        val BG_BLUR_RADIUS = floatPreferencesKey("bg_blur_radius")
        val BG_DIM_ALPHA = floatPreferencesKey("bg_dim_alpha")
        val SOLID_BG_COLOR_ARGB = longPreferencesKey("solid_bg_color_argb")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val PAUSE_ON_HEADSET = booleanPreferencesKey("pause_on_headset_disconnect")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val LIST_MEMORY_LIMIT = intPreferencesKey("list_memory_limit")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.AMOLED.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.AMOLED)

        val sortStr = prefs[Keys.SORT_ORDER] ?: SortOrder.TITLE_ASC.name
        val sortOrder = runCatching { SortOrder.valueOf(sortStr) }.getOrDefault(SortOrder.TITLE_ASC)

        UserPreferences(
            minDurationSeconds = (prefs[Keys.MIN_DURATION_SEC] ?: 50).coerceIn(0, 120),
            excludedFolders = prefs[Keys.EXCLUDED_FOLDERS] ?: emptySet(),
            themeMode = themeMode,
            customAccentColorArgb = prefs[Keys.ACCENT_COLOR_ARGB] ?: 0xFF1DB954L,
            useDynamicAlbumPalette = prefs[Keys.USE_DYNAMIC_PALETTE] ?: true,
            customBackgroundUri = prefs[Keys.CUSTOM_BG_URI]?.takeIf { it.isNotBlank() },
            backgroundBlurRadius = (prefs[Keys.BG_BLUR_RADIUS] ?: 14f).coerceIn(0f, 25f),
            backgroundDimAlpha = (prefs[Keys.BG_DIM_ALPHA] ?: 0.65f).coerceIn(0.1f, 0.92f),
            solidBackgroundColorArgb = prefs[Keys.SOLID_BG_COLOR_ARGB] ?: 0xFF0E1318L,
            gaplessPlayback = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            pauseOnHeadsetDisconnect = prefs[Keys.PAUSE_ON_HEADSET] ?: true,
            sortOrder = sortOrder,
            listMemoryLimit = (prefs[Keys.LIST_MEMORY_LIMIT] ?: 500).coerceIn(100, 2000)
        )
    }

    suspend fun setMinDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.MIN_DURATION_SEC] = seconds.coerceIn(0, 120) }
    }

    suspend fun addExcludedFolder(folderPath: String) {
        val clean = folderPath.trim()
        if (clean.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_FOLDERS] ?: emptySet()
            prefs[Keys.EXCLUDED_FOLDERS] = current + clean
        }
    }

    suspend fun removeExcludedFolder(folderPath: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_FOLDERS] ?: emptySet()
            prefs[Keys.EXCLUDED_FOLDERS] = current - folderPath
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCustomAccentColor(argb: Long) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR_ARGB] = argb }
    }

    suspend fun setUseDynamicAlbumPalette(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DYNAMIC_PALETTE] = enabled }
    }

    suspend fun setCustomBackgroundUri(uriString: String?) {
        context.dataStore.edit { prefs ->
            if (uriString.isNullOrBlank()) {
                prefs.remove(Keys.CUSTOM_BG_URI)
            } else {
                prefs[Keys.CUSTOM_BG_URI] = uriString
            }
        }
    }

    suspend fun setBackgroundBlurRadius(radius: Float) {
        context.dataStore.edit { it[Keys.BG_BLUR_RADIUS] = radius.coerceIn(0f, 25f) }
    }

    suspend fun setBackgroundDimAlpha(alpha: Float) {
        context.dataStore.edit { it[Keys.BG_DIM_ALPHA] = alpha.coerceIn(0.1f, 0.92f) }
    }

    suspend fun setSolidBackgroundColor(argb: Long) {
        context.dataStore.edit { it[Keys.SOLID_BG_COLOR_ARGB] = argb }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setPauseOnHeadsetDisconnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PAUSE_ON_HEADSET] = enabled }
    }

    suspend fun setSortOrder(order: SortOrder) {
        context.dataStore.edit { it[Keys.SORT_ORDER] = order.name }
    }
}
