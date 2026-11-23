package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.floatXDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "floatx_preferences"
)

class FloatXPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.floatXDataStore

    private object Keys {
        val SIDEBAR_ENABLED = booleanPreferencesKey("sidebar_enabled")
        val SIDEBAR_EDGE = stringPreferencesKey("sidebar_edge")
        val VERTICAL_BIAS = floatPreferencesKey("vertical_bias")
        val HANDLE_HEIGHT_DP = intPreferencesKey("handle_height_dp")
        val HANDLE_WIDTH_DP = intPreferencesKey("handle_width_dp")
        val OPACITY = floatPreferencesKey("opacity")
        val AUTO_HIDE = booleanPreferencesKey("auto_hide")
        val BLUR_EFFECT = booleanPreferencesKey("blur_effect")
        val ACCENT = stringPreferencesKey("accent")
        val DEFAULT_PRESET = stringPreferencesKey("default_preset")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PINNED_PACKAGES = stringSetPreferencesKey("pinned_packages")
    }

    val configFlow: Flow<SidebarConfig> = dataStore.data.map { prefs ->
        SidebarConfig(
            enabled = prefs[Keys.SIDEBAR_ENABLED] ?: false,
            edge = prefs[Keys.SIDEBAR_EDGE]?.let { runCatching { SidebarEdge.valueOf(it) }.getOrNull() }
                ?: SidebarEdge.RIGHT,
            verticalBias = (prefs[Keys.VERTICAL_BIAS] ?: 0.45f).coerceIn(0.15f, 0.85f),
            handleHeightDp = (prefs[Keys.HANDLE_HEIGHT_DP] ?: 86).coerceIn(48, 140),
            handleWidthDp = (prefs[Keys.HANDLE_WIDTH_DP] ?: 6).coerceIn(4, 10),
            opacity = (prefs[Keys.OPACITY] ?: 0.88f).coerceIn(0.35f, 1f),
            autoHide = prefs[Keys.AUTO_HIDE] ?: true,
            blurEffect = prefs[Keys.BLUR_EFFECT] ?: true,
            accent = prefs[Keys.ACCENT]?.let { runCatching { SidebarAccent.valueOf(it) }.getOrNull() }
                ?: SidebarAccent.COBALT,
            defaultPreset = prefs[Keys.DEFAULT_PRESET]?.let { runCatching { WindowSizePreset.valueOf(it) }.getOrNull() }
                ?: WindowSizePreset.BALANCED,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            pinnedPackages = prefs[Keys.PINNED_PACKAGES] ?: emptySet()
        )
    }

    suspend fun setSidebarEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SIDEBAR_ENABLED] = enabled }
    }

    suspend fun setEdge(edge: SidebarEdge) {
        dataStore.edit { it[Keys.SIDEBAR_EDGE] = edge.name }
    }

    suspend fun setVerticalBias(bias: Float) {
        dataStore.edit { it[Keys.VERTICAL_BIAS] = bias.coerceIn(0.15f, 0.85f) }
    }

    suspend fun setHandleHeightDp(heightDp: Int) {
        dataStore.edit { it[Keys.HANDLE_HEIGHT_DP] = heightDp.coerceIn(48, 140) }
    }

    suspend fun setHandleWidthDp(widthDp: Int) {
        dataStore.edit { it[Keys.HANDLE_WIDTH_DP] = widthDp.coerceIn(4, 10) }
    }

    suspend fun setOpacity(opacity: Float) {
        dataStore.edit { it[Keys.OPACITY] = opacity.coerceIn(0.35f, 1f) }
    }

    suspend fun setAutoHide(autoHide: Boolean) {
        dataStore.edit { it[Keys.AUTO_HIDE] = autoHide }
    }

    suspend fun setBlurEffect(blurEffect: Boolean) {
        dataStore.edit { it[Keys.BLUR_EFFECT] = blurEffect }
    }

    suspend fun setAccent(accent: SidebarAccent) {
        dataStore.edit { it[Keys.ACCENT] = accent.name }
    }

    suspend fun setDefaultPreset(preset: WindowSizePreset) {
        dataStore.edit { it[Keys.DEFAULT_PRESET] = preset.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun togglePinnedPackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PINNED_PACKAGES] ?: emptySet()
            prefs[Keys.PINNED_PACKAGES] = if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }
}
