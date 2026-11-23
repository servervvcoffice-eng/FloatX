package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FloatingWindowSession
import com.example.data.InstalledAppInfo
import com.example.data.ThemeMode
import com.example.ui.FloatXUiState
import com.example.ui.components.FrostedCard
import com.example.ui.components.InteractiveFloatingStage
import com.example.ui.theme.WindowFreeformGreen
import com.example.ui.theme.WindowMinimizeAmber
import kotlin.math.roundToInt

@Composable
fun StudioScreen(
    uiState: FloatXUiState,
    getIconBitmap: (String) -> Bitmap?,
    onToggleService: (Boolean) -> Unit,
    onToggleSidebarPanel: () -> Unit,
    onCycleTheme: () -> Unit,
    onBrowseApps: () -> Unit,
    onOpenWindowsTab: () -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onFocusWindow: (String) -> Unit,
    onMoveWindow: (String, Float, Float) -> Unit,
    onResizeWindow: (String, Float, Float) -> Unit,
    onMinimizeWindow: (String, Boolean) -> Unit,
    onCloseWindow: (String) -> Unit,
    onOpenFreeform: (FloatingWindowSession) -> Unit,
    onGrantOverlayPermission: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDotColor by animateColorAsState(
        targetValue = if (uiState.isServiceRunning) WindowFreeformGreen
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        label = "engine_status_dot"
    )

    val themeIcon = when (uiState.config.themeMode) {
        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Minimal iOS-inspired Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.deviceInfo.hyperOsLabel} · API ${uiState.deviceInfo.apiLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Real-time RAM footprint pill
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Memory,
                                    contentDescription = "Memory Usage",
                                    tint = WindowFreeformGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${uiState.ramUsageMb.roundToInt().coerceAtLeast(1)} MB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Quick Light / Dark / System Theme Toggle
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            IconButton(
                                onClick = onCycleTheme,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("theme_cycle_button")
                            ) {
                                Icon(
                                    imageVector = themeIcon,
                                    contentDescription = "Switch Theme (${uiState.config.themeMode.label})",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Master Floating Sidebar Engine Card
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (uiState.isServiceRunning)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Layers,
                                        contentDescription = "FloatX Engine",
                                        tint = if (uiState.isServiceRunning)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(activeDotColor)
                                        )
                                        Text(
                                            text = if (uiState.isServiceRunning)
                                                stringResource(R.string.sidebar_status_active)
                                            else
                                                stringResource(R.string.sidebar_status_inactive),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${uiState.config.edge.label} · ${uiState.activeWindows.size} active windows",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = uiState.isServiceRunning,
                                onCheckedChange = onToggleService,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("sidebar_master_switch")
                            )
                        }

                        // Permission prompt callout when overlay permission is missing
                        if (!uiState.hasOverlayPermission) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = WindowMinimizeAmber.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, WindowMinimizeAmber.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = "Permission Needed",
                                        tint = WindowMinimizeAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.permission_overlay_title),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Allow FloatX to draw over other apps.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = onGrantOverlayPermission,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("grant_overlay_button")
                                    ) {
                                        Text(
                                            text = "Allow",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Control Pill Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onToggleSidebarPanel,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("toggle_sidebar_panel_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSidebarExpanded) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isSidebarExpanded) "Hide Bar" else "Show Bar",
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = onBrowseApps,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("quick_launch_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesomeMotion,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Float App",
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Live Interactive Multi-Window Workspace Stage
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Multi-Window Stage",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (uiState.activeWindows.isNotEmpty()) {
                            Text(
                                text = "Manage (${uiState.activeWindows.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenWindowsTab() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    InteractiveFloatingStage(
                        config = uiState.config,
                        activeWindows = uiState.activeWindows,
                        quickApps = uiState.pinnedApps.ifEmpty { uiState.installedApps },
                        isSidebarExpanded = uiState.isSidebarExpanded,
                        getIconBitmap = getIconBitmap,
                        onToggleSidebar = onToggleSidebarPanel,
                        onLaunchApp = onLaunchApp,
                        onFocusWindow = onFocusWindow,
                        onMoveWindow = onMoveWindow,
                        onResizeWindow = onResizeWindow,
                        onMinimizeWindow = onMinimizeWindow,
                        onCloseWindow = onCloseWindow,
                        onOpenFreeform = onOpenFreeform,
                        onBrowseApps = onBrowseApps
                    )
                }
            }

            // Android 16 & HyperOS 3 Compatibility Deck
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hyperos_guide_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.hyperos_guide_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SystemShortcutRow(
                            icon = Icons.Rounded.Shield,
                            title = "Overlay Permission",
                            subtitle = if (uiState.hasOverlayPermission) "Active for system sidebar & windows"
                            else "Required for floating overlay",
                            badge = if (uiState.hasOverlayPermission) "Ready" else "Setup",
                            badgeIsPositive = uiState.hasOverlayPermission,
                            onClick = onGrantOverlayPermission,
                            testTag = "shortcut_overlay_permission"
                        )

                        SystemShortcutRow(
                            icon = Icons.Rounded.DeveloperMode,
                            title = "Freeform Multi-Window",
                            subtitle = "Enable freeform windows in Developer Options",
                            badge = "Android 16",
                            badgeIsPositive = true,
                            onClick = onOpenDeveloperOptions,
                            testTag = "shortcut_developer_options"
                        )

                        SystemShortcutRow(
                            icon = Icons.Rounded.SettingsApplications,
                            title = "HyperOS 3 Background & Pop-ups",
                            subtitle = "Enable Autostart & Display pop-up windows",
                            badge = "HyperOS",
                            badgeIsPositive = true,
                            onClick = onOpenAppSettings,
                            testTag = "shortcut_hyperos_settings"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemShortcutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    badgeIsPositive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (badgeIsPositive && badge == "Ready") {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = WindowFreeformGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badgeIsPositive) MaterialTheme.colorScheme.primary else WindowMinimizeAmber
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
