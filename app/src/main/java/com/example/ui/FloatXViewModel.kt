package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Debug
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppFilter
import com.example.data.FloatXPreferencesRepository
import com.example.data.FloatXWindowRegistry
import com.example.data.FloatingWindowSession
import com.example.data.InstalledAppInfo
import com.example.data.InstalledAppsRepository
import com.example.data.SidebarAccent
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import com.example.data.ThemeMode
import com.example.data.WindowSizePreset
import com.example.overlay.DeviceEnvironmentInfo
import com.example.overlay.FloatXOverlayService
import com.example.overlay.FreeformLaunchResult
import com.example.overlay.FreeformWindowLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FloatXUiState(
    val config: SidebarConfig = SidebarConfig(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val pinnedApps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val appFilter: AppFilter = AppFilter.ALL,
    val isLoadingApps: Boolean = true,
    val activeWindows: List<FloatingWindowSession> = emptyList(),
    val isServiceRunning: Boolean = false,
    val isSidebarExpanded: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val ramUsageMb: Float = 0f,
    val deviceInfo: DeviceEnvironmentInfo = FreeformWindowLauncher.getDeviceEnvironment(),
    val statusMessage: String? = null
)

class FloatXViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefsRepo = FloatXPreferencesRepository(appContext)
    private val appsRepo = InstalledAppsRepository(appContext)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")
    private val _appFilter = MutableStateFlow(AppFilter.ALL)
    private val _hasOverlayPermission = MutableStateFlow(checkOverlayPermission())
    private val _ramUsageMb = MutableStateFlow(measureAppRamMb())

    private val baseDataFlow = combine(
        prefsRepo.configFlow,
        _installedApps,
        _searchQuery,
        _appFilter,
        _isLoadingApps
    ) { config, apps, query, filter, loading ->
        Tuple5(config, apps, query, filter, loading)
    }

    private val runtimeStateFlow = combine(
        FloatXWindowRegistry.windows,
        FloatXWindowRegistry.serviceRunning,
        FloatXWindowRegistry.sidebarExpanded,
        _hasOverlayPermission,
        _ramUsageMb
    ) { windows, running, expanded, overlayPerm, ramMb ->
        Tuple5(windows, running, expanded, overlayPerm, ramMb)
    }

    val uiState: StateFlow<FloatXUiState> = combine(
        baseDataFlow,
        runtimeStateFlow,
        FloatXWindowRegistry.statusBanner
    ) { base, runtime, banner ->
        val config = base.first
        val apps = base.second.map { it.copy(isPinned = it.packageName in config.pinnedPackages) }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.isPinned }
                    .thenBy { it.label.lowercase() }
            )
        val query = base.third.trim()
        val filter = base.fourth

        val filtered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.PINNED -> app.isPinned
                AppFilter.USER -> !app.isSystemApp
            }
            val matchesQuery = query.isEmpty() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }

        FloatXUiState(
            config = config,
            installedApps = apps,
            filteredApps = filtered,
            pinnedApps = apps.filter { it.isPinned },
            searchQuery = base.third,
            appFilter = filter,
            isLoadingApps = base.fifth,
            activeWindows = runtime.first,
            isServiceRunning = runtime.second,
            isSidebarExpanded = runtime.third,
            hasOverlayPermission = runtime.fourth,
            ramUsageMb = runtime.fifth,
            deviceInfo = FreeformWindowLauncher.getDeviceEnvironment(),
            statusMessage = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FloatXUiState()
    )

    init {
        refreshInstalledApps()
        startRamMonitor()
        restoreServiceIfEnabled()
    }

    private fun restoreServiceIfEnabled() {
        viewModelScope.launch {
            val config = prefsRepo.configFlow.first()
            if (config.enabled && checkOverlayPermission() && !FloatXWindowRegistry.serviceRunning.value) {
                FloatXOverlayService.start(appContext)
            }
        }
    }

    fun refreshPermissions() {
        val granted = checkOverlayPermission()
        _hasOverlayPermission.value = granted
        if (!granted) return
        viewModelScope.launch {
            val config = prefsRepo.configFlow.first()
            if (config.enabled && !FloatXWindowRegistry.serviceRunning.value) {
                FloatXOverlayService.start(appContext)
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val pinned = uiState.value.config.pinnedPackages
            _installedApps.value = appsRepo.loadInstalledApps(pinned)
            _isLoadingApps.value = false
        }
    }

    fun getAppIconBitmap(packageName: String): Bitmap? =
        appsRepo.getAppIconBitmap(packageName, 96)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAppFilter(filter: AppFilter) {
        _appFilter.value = filter
    }

    fun toggleSidebarService(enable: Boolean) {
        if (enable && !checkOverlayPermission()) {
            FloatXWindowRegistry.postStatus("Grant 'Display over other apps' permission to enable FloatX Sidebar")
            FreeformWindowLauncher.openOverlayPermissionSettings(appContext)
            return
        }
        viewModelScope.launch {
            // Persist preference FIRST before starting/stopping service to prevent DataStore race
            prefsRepo.setSidebarEnabled(enable)
            if (enable) {
                FloatXOverlayService.start(appContext)
                FloatXWindowRegistry.postStatus("FloatX Sidebar enabled")
            } else {
                FloatXOverlayService.stop(appContext)
                FloatXWindowRegistry.postStatus("FloatX Sidebar paused")
            }
        }
    }

    fun toggleSidebarPanelVisible() {
        if (FloatXWindowRegistry.serviceRunning.value) {
            FloatXWindowRegistry.toggleSidebarExpanded()
        } else if (checkOverlayPermission()) {
            viewModelScope.launch {
                prefsRepo.setSidebarEnabled(true)
                FloatXOverlayService.start(appContext)
                delay(180)
                FloatXWindowRegistry.setSidebarExpanded(true)
            }
        } else {
            FloatXWindowRegistry.toggleSidebarExpanded()
        }
    }

    fun launchAppInFloatingWindow(
        app: InstalledAppInfo,
        alsoOpenSystemFreeform: Boolean = true
    ) {
        val config = uiState.value.config
        val hasOverlay = checkOverlayPermission()

        if (hasOverlay && !FloatXWindowRegistry.serviceRunning.value) {
            viewModelScope.launch {
                prefsRepo.setSidebarEnabled(true)
                FloatXOverlayService.start(appContext)
            }
        }

        val session = FloatXWindowRegistry.openOrFocusWindow(
            app = app,
            preset = config.defaultPreset,
            accentArgb = config.accent.argb,
            freeformLaunched = alsoOpenSystemFreeform
        )

        if (alsoOpenSystemFreeform) {
            val result = FreeformWindowLauncher.launchInFreeformWindow(
                context = appContext,
                packageName = app.packageName,
                xDp = session.xDp,
                yDp = session.yDp,
                widthDp = session.widthDp,
                heightDp = session.heightDp
            )
            when (result) {
                is FreeformLaunchResult.FreeformSuccess -> {
                    FloatXWindowRegistry.postStatus("${app.label} launched in Floating Window")
                }

                is FreeformLaunchResult.StandardFallback -> {
                    FloatXWindowRegistry.postStatus("${app.label} opened (enable Freeform in Developer Options for system pop-out)")
                }

                is FreeformLaunchResult.Failed -> {
                    FloatXWindowRegistry.postStatus(result.reason)
                }
            }
        } else {
            FloatXWindowRegistry.postStatus("Added ${app.label} floating window")
        }
    }

    fun relaunchSessionInFreeform(session: FloatingWindowSession) {
        val result = FreeformWindowLauncher.launchInFreeformWindow(
            context = appContext,
            packageName = session.packageName,
            xDp = session.xDp,
            yDp = session.yDp,
            widthDp = session.widthDp,
            heightDp = session.heightDp
        )
        when (result) {
            is FreeformLaunchResult.FreeformSuccess ->
                FloatXWindowRegistry.postStatus("${session.appLabel} focused in Freeform (${session.widthDp.toInt()}×${session.heightDp.toInt()} dp)")
            is FreeformLaunchResult.StandardFallback ->
                FloatXWindowRegistry.postStatus("Opened ${session.appLabel}")
            is FreeformLaunchResult.Failed ->
                FloatXWindowRegistry.postStatus(result.reason)
        }
    }

    fun focusWindow(windowId: String) = FloatXWindowRegistry.focusWindow(windowId)
    fun moveWindow(windowId: String, xDp: Float, yDp: Float) = FloatXWindowRegistry.moveWindow(windowId, xDp, yDp)
    fun resizeWindow(windowId: String, wDp: Float, hDp: Float) = FloatXWindowRegistry.resizeWindow(windowId, wDp, hDp)
    fun applyWindowPreset(windowId: String, preset: WindowSizePreset) = FloatXWindowRegistry.applyPreset(windowId, preset)
    fun setWindowOpacity(windowId: String, opacity: Float) = FloatXWindowRegistry.setWindowOpacity(windowId, opacity)
    fun setWindowMinimized(windowId: String, minimized: Boolean) = FloatXWindowRegistry.setMinimized(windowId, minimized)
    fun setBoundsEditorVisible(windowId: String, visible: Boolean) = FloatXWindowRegistry.setBoundsEditorVisible(windowId, visible)
    fun closeWindow(windowId: String) = FloatXWindowRegistry.closeWindow(windowId)
    fun cascadeAllWindows() = FloatXWindowRegistry.cascadeAll()
    fun minimizeAllWindows() = FloatXWindowRegistry.minimizeAll()
    fun closeAllWindows() = FloatXWindowRegistry.closeAll()

    fun togglePinApp(packageName: String) {
        viewModelScope.launch { prefsRepo.togglePinnedPackage(packageName) }
    }

    fun setSidebarEdge(edge: SidebarEdge) {
        viewModelScope.launch { prefsRepo.setEdge(edge) }
    }

    fun setVerticalBias(bias: Float) {
        viewModelScope.launch { prefsRepo.setVerticalBias(bias) }
    }

    fun setHandleHeightDp(heightDp: Int) {
        viewModelScope.launch { prefsRepo.setHandleHeightDp(heightDp) }
    }

    fun setHandleWidthDp(widthDp: Int) {
        viewModelScope.launch { prefsRepo.setHandleWidthDp(widthDp) }
    }

    fun setOpacity(opacity: Float) {
        viewModelScope.launch { prefsRepo.setOpacity(opacity) }
    }

    fun setAutoHide(autoHide: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoHide(autoHide) }
    }

    fun setBlurEffect(blurEffect: Boolean) {
        viewModelScope.launch { prefsRepo.setBlurEffect(blurEffect) }
    }

    fun setShowFloatingBubbles(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowFloatingBubbles(show) }
    }

    fun setAccent(accent: SidebarAccent) {
        viewModelScope.launch { prefsRepo.setAccent(accent) }
    }

    fun setDefaultPreset(preset: WindowSizePreset) {
        viewModelScope.launch { prefsRepo.setDefaultPreset(preset) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun cycleThemeMode() {
        val next = when (uiState.value.config.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    fun openOverlayPermissionSettings() = FreeformWindowLauncher.openOverlayPermissionSettings(appContext)
    fun openDeveloperOptions() = FreeformWindowLauncher.openDeveloperOptions(appContext)
    fun openAppDetailsSettings() = FreeformWindowLauncher.openAppDetailsSettings(appContext)
    fun clearStatusMessage() = FloatXWindowRegistry.postStatus(null)

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(appContext)
        } else {
            true
        }
    }

    private fun startRamMonitor() {
        viewModelScope.launch {
            while (isActive) {
                _ramUsageMb.value = measureAppRamMb()
                delay(4000)
            }
        }
    }

    private fun measureAppRamMb(): Float {
        val runtime = Runtime.getRuntime()
        val dalvikUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val nativeUsed = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L)
        return (dalvikUsed + nativeUsed) / (1024f * 1024f)
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
