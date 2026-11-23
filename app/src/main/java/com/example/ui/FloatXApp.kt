package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.CustomizeScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.WindowsScreen
import com.example.ui.theme.FloatXTheme

enum class FloatXTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    STUDIO("studio", "Studio", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    APPS("apps", "Apps", Icons.Rounded.Apps, Icons.Outlined.Apps),
    WINDOWS("windows", "Windows", Icons.Rounded.PictureInPictureAlt, Icons.Outlined.PictureInPictureAlt),
    CUSTOMIZE("customize", "Sidebar", Icons.Rounded.Tune, Icons.Outlined.Tune)
}

@Composable
fun FloatXApp(
    viewModel: FloatXViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by rememberSaveable { mutableStateOf(FloatXTab.STUDIO) }

    // Request POST_NOTIFICATIONS on Android 13+ when enabling the overlay service
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Service still operates even if notification permission is declined */ }
    )

    val handleToggleService: (Boolean) -> Unit = { enable ->
        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.toggleSidebarService(enable)
    }

    // Refresh overlay permission & service state whenever returning from Android Settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    // Display transient status updates cleanly via Material 3 Snackbar
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Back press on secondary tabs returns cleanly to Studio home
    BackHandler(enabled = currentTab != FloatXTab.STUDIO) {
        currentTab = FloatXTab.STUDIO
    }

    FloatXTheme(themeMode = uiState.config.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            FloatXTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            selected = selected,
                                            activeWindowCount = uiState.activeWindows.size
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                        ) {
                            Spacer(modifier = Modifier.weight(0.5f))
                            FloatXTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        TabIconWithBadge(
                                            tab = tab,
                                            selected = selected,
                                            activeWindowCount = uiState.activeWindows.size
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_rail_${tab.route}")
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab_crossfade"
                        ) { tab ->
                            when (tab) {
                                FloatXTab.STUDIO -> StudioScreen(
                                    uiState = uiState,
                                    getIconBitmap = viewModel::getAppIconBitmap,
                                    onToggleService = handleToggleService,
                                    onToggleSidebarPanel = viewModel::toggleSidebarPanelVisible,
                                    onCycleTheme = viewModel::cycleThemeMode,
                                    onBrowseApps = { currentTab = FloatXTab.APPS },
                                    onOpenWindowsTab = { currentTab = FloatXTab.WINDOWS },
                                    onLaunchApp = { app -> viewModel.launchAppInFloatingWindow(app) },
                                    onFocusWindow = viewModel::focusWindow,
                                    onMoveWindow = viewModel::moveWindow,
                                    onResizeWindow = viewModel::resizeWindow,
                                    onMinimizeWindow = viewModel::setWindowMinimized,
                                    onCloseWindow = viewModel::closeWindow,
                                    onOpenFreeform = viewModel::relaunchSessionInFreeform,
                                    onGrantOverlayPermission = viewModel::openOverlayPermissionSettings,
                                    onOpenDeveloperOptions = viewModel::openDeveloperOptions,
                                    onOpenAppSettings = viewModel::openAppDetailsSettings
                                )

                                FloatXTab.APPS -> AppsScreen(
                                    uiState = uiState,
                                    getIconBitmap = viewModel::getAppIconBitmap,
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onFilterChange = viewModel::setAppFilter,
                                    onRefreshApps = viewModel::refreshInstalledApps,
                                    onTogglePin = viewModel::togglePinApp,
                                    onLaunchFloating = { app -> viewModel.launchAppInFloatingWindow(app) }
                                )

                                FloatXTab.WINDOWS -> WindowsScreen(
                                    uiState = uiState,
                                    getIconBitmap = viewModel::getAppIconBitmap,
                                    onBrowseApps = { currentTab = FloatXTab.APPS },
                                    onCascadeAll = viewModel::cascadeAllWindows,
                                    onMinimizeAll = viewModel::minimizeAllWindows,
                                    onCloseAll = viewModel::closeAllWindows,
                                    onLaunchFreeform = viewModel::relaunchSessionInFreeform,
                                    onToggleMinimize = viewModel::setWindowMinimized,
                                    onApplyPreset = viewModel::applyWindowPreset,
                                    onChangeOpacity = viewModel::setWindowOpacity,
                                    onCloseWindow = viewModel::closeWindow
                                )

                                FloatXTab.CUSTOMIZE -> CustomizeScreen(
                                    config = uiState.config,
                                    isServiceRunning = uiState.isServiceRunning,
                                    onToggleService = handleToggleService,
                                    onSetEdge = viewModel::setSidebarEdge,
                                    onSetVerticalBias = viewModel::setVerticalBias,
                                    onSetHandleHeight = viewModel::setHandleHeightDp,
                                    onSetHandleWidth = viewModel::setHandleWidthDp,
                                    onSetOpacity = viewModel::setOpacity,
                                    onSetAutoHide = viewModel::setAutoHide,
                                    onSetBlurEffect = viewModel::setBlurEffect,
                                    onSetAccent = viewModel::setAccent,
                                    onSetDefaultPreset = viewModel::setDefaultPreset,
                                    onSetThemeMode = viewModel::setThemeMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconWithBadge(
    tab: FloatXTab,
    selected: Boolean,
    activeWindowCount: Int
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (tab == FloatXTab.WINDOWS && activeWindowCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(text = activeWindowCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = tab.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = tab.label)
    }
}
