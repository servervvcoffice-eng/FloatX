package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.FloatXPreferencesRepository
import com.example.data.FloatXWindowRegistry
import com.example.data.FloatingWindowSession
import com.example.data.InstalledAppInfo
import com.example.data.InstalledAppsRepository
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatXOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "floatx_overlay_engine"
        private const val NOTIFICATION_ID = 7401

        const val ACTION_START = "com.floatx.window.ACTION_START"
        const val ACTION_STOP = "com.floatx.window.ACTION_STOP"
        const val ACTION_TOGGLE_SIDEBAR = "com.floatx.window.ACTION_TOGGLE_SIDEBAR"
        const val ACTION_CLOSE_ALL = "com.floatx.window.ACTION_CLOSE_ALL"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatXOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatXOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var prefsRepository: FloatXPreferencesRepository
    private lateinit var appsRepository: InstalledAppsRepository
    private lateinit var viewFactory: OverlayViewFactory

    private var currentConfig: SidebarConfig = SidebarConfig(enabled = true)
    private var lastLoadedPinnedSet: Set<String>? = null
    private var installedApps: List<InstalledAppInfo> = emptyList()
    private var seenEnabledTrue = false

    private var edgeHandleView: View? = null
    private var edgeHandleParams: WindowManager.LayoutParams? = null
    private var sidebarScrimView: View? = null
    private var sidebarPanelView: View? = null

    private enum class OverlayMode {
        BUBBLE,
        BOUNDS_EDITOR
    }

    private data class AttachedWindowHolder(
        val view: View,
        val params: WindowManager.LayoutParams,
        val mode: OverlayMode,
        val zOrder: Long,
        val accentArgb: Int
    )

    private val attachedWindows = LinkedHashMap<String, AttachedWindowHolder>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefsRepository = FloatXPreferencesRepository(applicationContext)
        appsRepository = InstalledAppsRepository(applicationContext)
        viewFactory = OverlayViewFactory(applicationContext, appsRepository)

        createNotificationChannel()
        startForegroundSafely()
        FloatXWindowRegistry.setServiceRunning(true)

        observePreferences()
        observeSidebarExpansion()
        observeFloatingWindows()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                FloatXWindowRegistry.setSidebarExpanded(false)
                FloatXWindowRegistry.closeAll()
                serviceScope.launch { prefsRepository.setSidebarEnabled(false) }
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_SIDEBAR -> {
                FloatXWindowRegistry.toggleSidebarExpanded()
            }

            ACTION_CLOSE_ALL -> {
                FloatXWindowRegistry.closeAll()
            }

            else -> {
                // Ensure preference stays synced to true on explicit start to avoid stale-false race
                serviceScope.launch { prefsRepository.setSidebarEnabled(true) }
                if (edgeHandleView == null) {
                    attachOrUpdateEdgeHandle()
                }
            }
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-clamp & re-theme overlays cleanly on rotation or Light/Dark mode change
        if (edgeHandleView != null) {
            attachOrUpdateEdgeHandle()
        }
        if (FloatXWindowRegistry.sidebarExpanded.value) {
            showSidebarPanel()
        }
        // Rebuild bubbles/editor with updated screen dimensions and theme
        attachedWindows.values.forEach { removeViewSafe(it.view) }
        attachedWindows.clear()
        syncWindowOverlays(FloatXWindowRegistry.windows.value)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLowMemory() {
        super.onLowMemory()
        appsRepository.clearLowMemoryCache()
    }

    override fun onDestroy() {
        removeViewSafe(edgeHandleView)
        edgeHandleView = null
        edgeHandleParams = null
        removeViewSafe(sidebarScrimView)
        sidebarScrimView = null
        removeViewSafe(sidebarPanelView)
        sidebarPanelView = null
        attachedWindows.values.forEach { removeViewSafe(it.view) }
        attachedWindows.clear()
        FloatXWindowRegistry.setSidebarExpanded(false)
        FloatXWindowRegistry.setServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatXOverlayService::class.java).apply { action = ACTION_TOGGLE_SIDEBAR },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val closeAllIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatXOverlayService::class.java).apply { action = ACTION_CLOSE_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, FloatXOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openCount = FloatXWindowRegistry.windows.value.size
        val contentText = if (openCount == 0) {
            "Edge sidebar active · Low-RAM mode"
        } else {
            "$openCount floating app${if (openCount > 1) "s" else ""} ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_action_toggle_sidebar), toggleIntent)
            .addAction(0, getString(R.string.notification_action_close_all), closeAllIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching { nm?.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun observePreferences() {
        serviceScope.launch {
            prefsRepository.configFlow.collectLatest { config ->
                if (config.enabled) {
                    seenEnabledTrue = true
                } else if (seenEnabledTrue) {
                    // Only stop when user actually transitioned enabled from true -> false
                    stopSelf()
                    return@collectLatest
                }

                val prev = currentConfig
                currentConfig = config

                // Avoid re-querying PackageManager on slider drags — only reload if pinned apps changed or list empty
                if (installedApps.isEmpty() || lastLoadedPinnedSet != config.pinnedPackages) {
                    lastLoadedPinnedSet = config.pinnedPackages
                    installedApps = appsRepository.loadInstalledApps(config.pinnedPackages)
                }

                val onlyVerticalChanged = edgeHandleView != null &&
                    edgeHandleParams != null &&
                    prev.edge == config.edge &&
                    prev.handleHeightDp == config.handleHeightDp &&
                    prev.handleWidthDp == config.handleWidthDp &&
                    prev.opacity == config.opacity &&
                    prev.autoHide == config.autoHide &&
                    prev.accent == config.accent &&
                    prev.themeMode == config.themeMode

                if (onlyVerticalChanged) {
                    val params = edgeHandleParams!!
                    val screenH = resources.displayMetrics.heightPixels
                    params.y = (screenH * config.verticalBias).roundToInt()
                        .coerceIn(dpToPx(64f), (screenH - dpToPx(140f)).coerceAtLeast(dpToPx(64f)))
                    updateViewLayoutSafe(edgeHandleView, params)
                } else {
                    attachOrUpdateEdgeHandle()
                }

                // Rebuild bubbles cleanly if theme, edge, or showFloatingBubbles changed
                if (prev.themeMode != config.themeMode ||
                    prev.edge != config.edge ||
                    prev.showFloatingBubbles != config.showFloatingBubbles ||
                    prev.accent != config.accent
                ) {
                    attachedWindows.values.forEach { removeViewSafe(it.view) }
                    attachedWindows.clear()
                }
                syncWindowOverlays(FloatXWindowRegistry.windows.value)

                if (FloatXWindowRegistry.sidebarExpanded.value) {
                    showSidebarPanel()
                }
            }
        }
    }

    private fun observeSidebarExpansion() {
        serviceScope.launch {
            FloatXWindowRegistry.sidebarExpanded.collectLatest { expanded ->
                if (expanded) {
                    if (installedApps.isEmpty()) {
                        installedApps = appsRepository.loadInstalledApps(currentConfig.pinnedPackages)
                    }
                    showSidebarPanel()
                } else {
                    hideSidebarPanel()
                }
            }
        }
    }

    private fun observeFloatingWindows() {
        serviceScope.launch {
            FloatXWindowRegistry.windows.collectLatest { sessions ->
                syncWindowOverlays(sessions)
                refreshNotification()
                if (FloatXWindowRegistry.sidebarExpanded.value) {
                    showSidebarPanel()
                }
            }
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    private fun pxToDp(px: Int): Float =
        px / resources.displayMetrics.density

    private fun attachOrUpdateEdgeHandle() {
        removeViewSafe(edgeHandleView)
        edgeHandleView = null
        edgeHandleParams = null

        val screenH = resources.displayMetrics.heightPixels
        val yPos = (screenH * currentConfig.verticalBias).roundToInt()
            .coerceIn(dpToPx(64f), (screenH - dpToPx(140f)).coerceAtLeast(dpToPx(64f)))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (currentConfig.edge == SidebarEdge.LEFT) Gravity.START else Gravity.END) or Gravity.TOP
            x = 0
            y = yPos
        }

        val handleView = viewFactory.createEdgeHandleView(
            config = currentConfig,
            onExpand = { FloatXWindowRegistry.toggleSidebarExpanded() },
            onDragVerticalDelta = { dy ->
                val maxH = (screenH - dpToPx(120f)).coerceAtLeast(dpToPx(64f))
                params.y = (params.y + dy).coerceIn(dpToPx(48f), maxH)
                updateViewLayoutSafe(edgeHandleView, params)
            },
            onDragFinished = {
                val newBias = (params.y.toFloat() / screenH.coerceAtLeast(1)).coerceIn(0.15f, 0.85f)
                serviceScope.launch { prefsRepository.setVerticalBias(newBias) }
            }
        )

        edgeHandleParams = params
        edgeHandleView = handleView
        addViewSafe(handleView, params)
    }

    private fun showSidebarPanel() {
        // Cancel any running animations & remove old panel/scrim cleanly
        sidebarPanelView?.animate()?.cancel()
        removeViewSafe(sidebarPanelView)
        sidebarPanelView = null
        removeViewSafe(sidebarScrimView)
        sidebarScrimView = null

        // Transparent full-screen backdrop so tapping anywhere outside immediately collapses the sidebar
        val scrimParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        val scrim = viewFactory.createBackdropScrimView {
            // Immediately remove scrim to prevent blocking subsequent touches while collapsing
            removeViewSafe(sidebarScrimView)
            sidebarScrimView = null
            FloatXWindowRegistry.setSidebarExpanded(false)
        }
        sidebarScrimView = scrim
        addViewSafe(scrim, scrimParams)

        val screenH = resources.displayMetrics.heightPixels
        val panelWidth = dpToPx(84f)
        val panelEstHeight = dpToPx(380f).coerceAtMost((screenH - dpToPx(96f)).coerceAtLeast(dpToPx(240f)))
        val yPos = (screenH * currentConfig.verticalBias - panelEstHeight / 2f).roundToInt()
            .coerceIn(dpToPx(40f), (screenH - panelEstHeight - dpToPx(40f)).coerceAtLeast(dpToPx(40f)))

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (currentConfig.edge == SidebarEdge.LEFT) Gravity.START else Gravity.END) or Gravity.TOP
            x = dpToPx(10f)
            y = yPos
        }

        val panel = viewFactory.createSidebarPanelView(
            config = currentConfig,
            apps = installedApps,
            activeWindows = FloatXWindowRegistry.windows.value,
            onAppClick = { app ->
                FloatXWindowRegistry.setSidebarExpanded(false)
                val session = FloatXWindowRegistry.openOrFocusWindow(
                    app = app,
                    preset = currentConfig.defaultPreset,
                    accentArgb = currentConfig.accent.argb,
                    freeformLaunched = false
                )
                FreeformWindowLauncher.launchInFreeformWindow(
                    context = this,
                    packageName = app.packageName,
                    xDp = session.xDp,
                    yDp = session.yDp,
                    widthDp = session.widthDp,
                    heightDp = session.heightDp
                )
            },
            onWindowClick = { win ->
                FloatXWindowRegistry.setSidebarExpanded(false)
                FloatXWindowRegistry.focusWindow(win.id)
                FreeformWindowLauncher.launchInFreeformWindow(
                    context = this,
                    packageName = win.packageName,
                    xDp = win.xDp,
                    yDp = win.yDp,
                    widthDp = win.widthDp,
                    heightDp = win.heightDp
                )
            },
            onOpenStudio = {
                FloatXWindowRegistry.setSidebarExpanded(false)
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            },
            onCollapse = {
                FloatXWindowRegistry.setSidebarExpanded(false)
            }
        )

        panel.alpha = 0f
        panel.scaleX = 0.9f
        panel.scaleY = 0.94f
        sidebarPanelView = panel
        addViewSafe(panel, params)
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
    }

    private fun hideSidebarPanel() {
        removeViewSafe(sidebarScrimView)
        sidebarScrimView = null

        val current = sidebarPanelView ?: return
        sidebarPanelView = null
        current.animate().cancel()
        current.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.94f)
            .setDuration(110)
            .withEndAction { removeViewSafe(current) }
            .start()
    }

    private fun syncWindowOverlays(sessions: List<FloatingWindowSession>) {
        val visibleSessions = sessions.filter { session ->
            session.showBoundsEditor || session.isMinimized || currentConfig.showFloatingBubbles
        }
        val activeIds = visibleSessions.map { it.id }.toSet()

        val toRemove = attachedWindows.keys.filter { it !in activeIds }
        for (id in toRemove) {
            attachedWindows.remove(id)?.let { holder ->
                removeViewSafe(holder.view)
            }
        }

        val sortedSessions = visibleSessions.sortedBy { it.zOrder }
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        sortedSessions.forEachIndexed { index, session ->
            val targetMode = if (session.showBoundsEditor) {
                OverlayMode.BOUNDS_EDITOR
            } else {
                OverlayMode.BUBBLE
            }

            val existing = attachedWindows[session.id]
            val needsRebuild = existing == null ||
                existing.mode != targetMode ||
                existing.accentArgb != currentConfig.accent.argb

            if (needsRebuild) {
                existing?.let { removeViewSafe(it.view) }
                val holder = attachWindowSession(session, targetMode, index, screenW, screenH)
                if (holder != null) {
                    attachedWindows[session.id] = holder
                }
            } else if (existing != null && targetMode == OverlayMode.BOUNDS_EDITOR) {
                val params = existing.params
                val targetX = dpToPx(session.xDp).coerceIn(0, (screenW - dpToPx(120f)).coerceAtLeast(0))
                val targetY = dpToPx(session.yDp).coerceIn(dpToPx(24f), (screenH - dpToPx(120f)).coerceAtLeast(dpToPx(24f)))
                val targetW = dpToPx(session.widthDp).coerceIn(dpToPx(236f), (screenW - dpToPx(16f)).coerceAtLeast(dpToPx(236f)))
                val targetH = dpToPx(session.heightDp).coerceIn(dpToPx(230f), (screenH - dpToPx(64f)).coerceAtLeast(dpToPx(230f)))

                var changed = false
                if (params.x != targetX) { params.x = targetX; changed = true }
                if (params.y != targetY) { params.y = targetY; changed = true }
                if (params.width != targetW) { params.width = targetW; changed = true }
                if (params.height != targetH) { params.height = targetH; changed = true }

                existing.view.findViewWithTag<TextView>("dimensions_label")?.text =
                    "${session.widthDp.roundToInt()} × ${session.heightDp.roundToInt()} dp · Drag corner to resize"

                if (existing.zOrder != session.zOrder) {
                    removeViewSafe(existing.view)
                    addViewSafe(existing.view, params)
                    attachedWindows[session.id] = existing.copy(zOrder = session.zOrder)
                } else if (changed) {
                    updateViewLayoutSafe(existing.view, params)
                }
            }
        }
    }

    private fun attachWindowSession(
        session: FloatingWindowSession,
        mode: OverlayMode,
        bubbleIndex: Int,
        screenW: Int,
        screenH: Int
    ): AttachedWindowHolder? {
        return if (mode == OverlayMode.BUBBLE) {
            val bubbleSize = dpToPx(48f)
            val dockOnLeft = currentConfig.edge == SidebarEdge.RIGHT
            val defaultX = if (dockOnLeft) dpToPx(8f) else (screenW - bubbleSize - dpToPx(8f)).coerceAtLeast(0)
            val defaultY = (dpToPx(110f) + bubbleIndex * dpToPx(58f))
                .coerceIn(dpToPx(48f), (screenH - bubbleSize - dpToPx(80f)).coerceAtLeast(dpToPx(48f)))

            val params = WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = defaultX
                y = defaultY
            }

            val view = viewFactory.createFloatingBubbleView(
                session = session.copy(accentArgb = currentConfig.accent.argb),
                config = currentConfig,
                onTapLaunchFreeform = {
                    val latest = FloatXWindowRegistry.windows.value.firstOrNull { it.id == session.id } ?: session
                    FloatXWindowRegistry.setMinimized(latest.id, false)
                    FreeformWindowLauncher.launchInFreeformWindow(
                        context = this,
                        packageName = latest.packageName,
                        xDp = latest.xDp,
                        yDp = latest.yDp,
                        widthDp = latest.widthDp,
                        heightDp = latest.heightDp
                    )
                },
                onLongPressResize = {
                    FloatXWindowRegistry.setBoundsEditorVisible(session.id, true)
                },
                onDragDelta = { dx, dy ->
                    params.x = (params.x + dx).coerceIn(0, (screenW - bubbleSize).coerceAtLeast(0))
                    params.y = (params.y + dy).coerceIn(dpToPx(32f), (screenH - bubbleSize - dpToPx(24f)).coerceAtLeast(dpToPx(32f)))
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                },
                onDragEnd = {
                    val snapLeft = params.x < (screenW / 2)
                    params.x = if (snapLeft) dpToPx(8f) else (screenW - bubbleSize - dpToPx(8f)).coerceAtLeast(0)
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                }
            )

            view.scaleX = 0.7f
            view.scaleY = 0.7f
            view.alpha = 0f
            addViewSafe(view, params)
            view.animate().scaleX(1f).scaleY(1f).alpha(0.95f).setDuration(150).start()
            AttachedWindowHolder(view, params, OverlayMode.BUBBLE, session.zOrder, currentConfig.accent.argb)
        } else {
            val xPx = dpToPx(session.xDp).coerceIn(0, (screenW - dpToPx(120f)).coerceAtLeast(0))
            val yPx = dpToPx(session.yDp).coerceIn(dpToPx(28f), (screenH - dpToPx(140f)).coerceAtLeast(dpToPx(28f)))
            val widthPx = dpToPx(session.widthDp).coerceIn(dpToPx(236f), (screenW - dpToPx(16f)).coerceAtLeast(dpToPx(236f)))
            val heightPx = dpToPx(session.heightDp).coerceIn(dpToPx(230f), (screenH - dpToPx(64f)).coerceAtLeast(dpToPx(230f)))

            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = xPx
                y = yPx
            }

            val view = viewFactory.createBoundsResizerView(
                session = session.copy(accentArgb = currentConfig.accent.argb),
                config = currentConfig,
                onFocus = { FloatXWindowRegistry.focusWindow(session.id) },
                onDragDelta = { dx, dy ->
                    params.x = (params.x + dx).coerceIn(0, (screenW - dpToPx(80f)).coerceAtLeast(0))
                    params.y = (params.y + dy).coerceIn(dpToPx(24f), (screenH - dpToPx(80f)).coerceAtLeast(dpToPx(24f)))
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                    FloatXWindowRegistry.moveWindow(session.id, pxToDp(params.x), pxToDp(params.y))
                },
                onResizeDelta = { dw, dh ->
                    val newW = (params.width + dw).coerceIn(dpToPx(236f), (screenW - dpToPx(16f)).coerceAtLeast(dpToPx(236f)))
                    val newH = (params.height + dh).coerceIn(dpToPx(230f), (screenH - dpToPx(64f)).coerceAtLeast(dpToPx(230f)))
                    params.width = newW
                    params.height = newH
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                    FloatXWindowRegistry.resizeWindow(session.id, pxToDp(newW), pxToDp(newH))
                },
                onDismissEditor = {
                    FloatXWindowRegistry.setBoundsEditorVisible(session.id, false)
                },
                onCloseSession = {
                    FloatXWindowRegistry.closeWindow(session.id)
                },
                onApplyAndLaunchFreeform = {
                    FloatXWindowRegistry.setBoundsEditorVisible(session.id, false)
                    FreeformWindowLauncher.launchInFreeformWindow(
                        context = this,
                        packageName = session.packageName,
                        xDp = pxToDp(params.x),
                        yDp = pxToDp(params.y),
                        widthDp = pxToDp(params.width),
                        heightDp = pxToDp(params.height)
                    )
                },
                onSelectPreset = { preset ->
                    FloatXWindowRegistry.applyPreset(session.id, preset)
                }
            )

            view.scaleX = 0.92f
            view.scaleY = 0.92f
            view.alpha = 0f
            addViewSafe(view, params)
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(170).start()
            AttachedWindowHolder(view, params, OverlayMode.BOUNDS_EDITOR, session.zOrder, currentConfig.accent.argb)
        }
    }

    private fun addViewSafe(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.addView(view, params) }
    }

    private fun updateViewLayoutSafe(view: View?, params: WindowManager.LayoutParams) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun removeViewSafe(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
    }
}
