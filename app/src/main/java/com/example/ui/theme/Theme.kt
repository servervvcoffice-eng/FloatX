package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.ThemeMode

private val FloatXLightColorScheme = lightColorScheme(
    primary = FloatXCobaltLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF002C66),
    secondary = FloatXIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E6FF),
    onSecondaryContainer = Color(0xFF1F1D6B),
    tertiary = WindowFreeformGreen,
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = LightInkPrimary,
    surface = LightSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceElevated,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineSubtle,
    error = WindowCloseRed,
    onError = Color.White
)

private val FloatXDarkColorScheme = darkColorScheme(
    primary = FloatXCobaltDark,
    onPrimary = Color(0xFF001C3D),
    primaryContainer = Color(0xFF0D2D57),
    onPrimaryContainer = Color(0xFFD4E8FF),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF111340),
    secondaryContainer = Color(0xFF232559),
    onSecondaryContainer = Color(0xFFE0E2FF),
    tertiary = WindowFreeformGreen,
    onTertiary = Color(0xFF04260E),
    background = DarkCanvas,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLowest = Color(0xFF05070B),
    surfaceContainerLow = Color(0xFF10141D),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkOutline,
    outlineVariant = DarkOutlineSubtle,
    error = WindowCloseRed,
    onError = Color.White
)

val FloatXShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FloatXTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDarkTheme) FloatXDarkColorScheme else FloatXLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FloatXTypography,
        shapes = FloatXShapes,
        content = content
    )
}
