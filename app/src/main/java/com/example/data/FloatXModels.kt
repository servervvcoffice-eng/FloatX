package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AccentAmethyst
import com.example.ui.theme.AccentCobalt
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentGray
import com.example.ui.theme.AccentIceWhite
import com.example.ui.theme.AccentSunset

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class SidebarEdge(val label: String) {
    LEFT("Left Edge"),
    RIGHT("Right Edge")
}

enum class SidebarAccent(
    val label: String,
    val color: Color,
    val argb: Int
) {
    GRAY("Gray", AccentGray, 0xFF8E8E93.toInt()),
    COBALT("Cobalt", AccentCobalt, 0xFF007AFF.toInt()),
    ICE_WHITE("Frost", AccentIceWhite, 0xFFE2E8F0.toInt()),
    EMERALD("Emerald", AccentEmerald, 0xFF30D158.toInt()),
    AMETHYST("Amethyst", AccentAmethyst, 0xFFAF52DE.toInt()),
    SUNSET("Sunset", AccentSunset, 0xFFFF6B4A.toInt())
}

enum class WindowSizePreset(
    val label: String,
    val widthDp: Int,
    val heightDp: Int
) {
    COMPACT("Compact", 248, 320),
    BALANCED("Balanced", 296, 410),
    TALL("Tall", 316, 510)
}

data class SidebarConfig(
    val enabled: Boolean = false,
    val edge: SidebarEdge = SidebarEdge.RIGHT,
    val verticalBias: Float = 0.45f,
    val handleHeightDp: Int = 86,
    val handleWidthDp: Int = 6,
    val opacity: Float = 0.92f,
    val autoHide: Boolean = true,
    val blurEffect: Boolean = true,
    val showFloatingBubbles: Boolean = true,
    val accent: SidebarAccent = SidebarAccent.GRAY,
    val defaultPreset: WindowSizePreset = WindowSizePreset.BALANCED,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pinnedPackages: Set<String> = emptySet()
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val activityName: String,
    val isSystemApp: Boolean,
    val isPinned: Boolean = false
)

data class FloatingWindowSession(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val isMinimized: Boolean = false,
    val showBoundsEditor: Boolean = false,
    val opacity: Float = 0.96f,
    val zOrder: Long = System.nanoTime(),
    val freeformLaunched: Boolean = false,
    val accentArgb: Int = SidebarAccent.GRAY.argb
)

enum class AppFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    USER("User")
}
