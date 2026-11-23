package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var prefsRepository: FloatXPreferencesRepository
    private lateinit var appsRepository: InstalledAppsRepository
    private lateinit var viewFactory: OverlayViewFactory

    private var currentConfig: SidebarConfig = SidebarConfig(enabled = true)
    private var installedApps: List<InstalledAppInfo> = emptyList()

    private var edgeHandleView: View? = null
    private var edgeHandleParams: WindowManager.LayoutParams? = null
    private var sidebarPanelView: View? = null

    private data class AttachedWindowHolder(
        val view: View,
        val params: WindowManager.LayoutParams,
        val isMinimized: Boolean,
        val zOrder: Long
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
                if (edgeHandleView == null) {
                    attachOrUpdateEdgeHandle()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLowMemory() {
        super.onLowMemory()
        appsRepository.clearLowMemoryCache()
    }

    override fun onDestroy() {
        removeViewSafe(edgeHandleView)
        edgeHandleView = null
        removeViewSafe(sidebarPanelView)
        sidebarPanelView = null
        attachedWindows.values.forEach { removeViewSafe(it.view) }
        attachedWindows.clear()
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
            "$openCount floating window${if (openCount > 1) "s" else ""} active"
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
                currentConfig = config
                installedApps = appsRepository.loadInstalledApps(config.pinnedPackages)
                if (!config.enabled) {
                    stopSelf()
                    return@collectLatest
                }
                attachOrUpdateEdgeHandle()
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
        removeViewSafe(sidebarPanelView)
        sidebarPanelView = null

        val screenH = resources.displayMetrics.heightPixels
        val panelWidth = dpToPx(80f)
        val yPos = (screenH * currentConfig.verticalBias - dpToPx(170f)).roundToInt()
            .coerceIn(dpToPx(44f), (screenH - dpToPx(420f)).coerceAtLeast(dpToPx(44f)))

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (currentConfig.edge == SidebarEdge.LEFT) Gravity.START else Gravity.END) or Gravity.TOP
            x = dpToPx(12f)
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
                val result = FreeformWindowLauncher.launchInFreeformWindow(
                    context = this,
                    packageName = app.packageName,
                    xDp = session.xDp,
                    yDp = session.yDp,
                    widthDp = session.widthDp,
                    heightDp = session.heightDp
                )
                when (result) {
                    is FreeformLaunchResult.FreeformSuccess ->
                        FloatXWindowRegistry.postStatus("Launched ${app.label} in Freeform")
                    is FreeformLaunchResult.StandardFallback ->
                        FloatXWindowRegistry.postStatus("Opened ${app.label} (Freeform fallback)")
                    is FreeformLaunchResult.Failed ->
                        FloatXWindowRegistry.postStatus(result.reason)
                }
            },
            onWindowClick = { win ->
                FloatXWindowRegistry.setSidebarExpanded(false)
                FloatXWindowRegistry.focusWindow(win.id)
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
        panel.scaleX = 0.88f
        panel.scaleY = 0.92f
        sidebarPanelView = panel
        addViewSafe(panel, params)
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
    }

    private fun hideSidebarPanel() {
        val current = sidebarPanelView ?: return
        sidebarPanelView = null
        current.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.92f)
            .setDuration(130)
            .withEndAction { removeViewSafe(current) }
            .start()
    }

    private fun syncWindowOverlays(sessions: List<FloatingWindowSession>) {
        val activeIds = sessions.map { it.id }.toSet()

        // Remove closed windows
        val toRemove = attachedWindows.keys.filter { it !in activeIds }
        for (id in toRemove) {
            attachedWindows.remove(id)?.let { holder ->
                removeViewSafe(holder.view)
            }
        }

        val sortedSessions = sessions.sortedBy { it.zOrder }
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        for (session in sortedSessions) {
            val existing = attachedWindows[session.id]
            val needsRebuild = existing == null || existing.isMinimized != session.isMinimized

            if (needsRebuild) {
                existing?.let { removeViewSafe(it.view) }
                val holder = attachWindowSession(session, screenW, screenH)
                if (holder != null) {
                    attachedWindows[session.id] = holder
                }
            } else if (existing != null) {
                // Update coordinates, size, and opacity smoothly without recreating view
                val params = existing.params
                val targetX = dpToPx(session.xDp).coerceIn(0, (screenW - dpToPx(56f)).coerceAtLeast(0))
                val targetY = dpToPx(session.yDp).coerceIn(dpToPx(24f), (screenH - dpToPx(72f)).coerceAtLeast(dpToPx(24f)))

                var changed = false
                if (params.x != targetX) {
                    params.x = targetX
                    changed = true
                }
                if (params.y != targetY) {
                    params.y = targetY
                    changed = true
                }
                if (!session.isMinimized) {
                    val targetW = dpToPx(session.widthDp).coerceIn(dpToPx(210f), (screenW - dpToPx(16f)).coerceAtLeast(dpToPx(210f)))
                    val targetH = dpToPx(session.heightDp).coerceIn(dpToPx(210f), (screenH - dpToPx(64f)).coerceAtLeast(dpToPx(210f)))
                    if (params.width != targetW) {
                        params.width = targetW
                        changed = true
                    }
                    if (params.height != targetH) {
                        params.height = targetH
                        changed = true
                    }
                    existing.view.findViewWithTag<TextView>("dimensions_label")?.text =
                        "${session.widthDp.roundToInt()} × ${session.heightDp.roundToInt()} dp · Freeform Ready"
                }
                existing.view.alpha = session.opacity

                if (existing.zOrder != session.zOrder) {
                    // Re-attach at top of WindowManager z-order
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
        screenW: Int,
        screenH: Int
    ): AttachedWindowHolder? {
        val xPx = dpToPx(session.xDp).coerceIn(0, (screenW - dpToPx(64f)).coerceAtLeast(0))
        val yPx = dpToPx(session.yDp).coerceIn(dpToPx(24f), (screenH - dpToPx(80f)).coerceAtLeast(dpToPx(24f)))

        return if (session.isMinimized) {
            val bubbleSize = dpToPx(56f)
            val params = WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = xPx
                y = yPx
            }

            val view = viewFactory.createMinimizedBubbleView(
                session = session,
                config = currentConfig,
                onTapRestore = { FloatXWindowRegistry.setMinimized(session.id, false) },
                onDragDelta = { dx, dy ->
                    params.x = (params.x + dx).coerceIn(0, (screenW - bubbleSize).coerceAtLeast(0))
                    params.y = (params.y + dy).coerceIn(dpToPx(28f), (screenH - bubbleSize - dpToPx(24f)).coerceAtLeast(dpToPx(28f)))
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                },
                onDragEnd = {
                    val snapLeft = params.x < (screenW / 2)
                    params.x = if (snapLeft) dpToPx(10f) else (screenW - bubbleSize - dpToPx(10f)).coerceAtLeast(0)
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                    FloatXWindowRegistry.moveWindow(session.id, pxToDp(params.x), pxToDp(params.y))
                }
            )

            view.scaleX = 0.7f
            view.scaleY = 0.7f
            view.alpha = 0f
            addViewSafe(view, params)
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(180).start()
            AttachedWindowHolder(view, params, isMinimized = true, zOrder = session.zOrder)
        } else {
            val widthPx = dpToPx(session.widthDp).coerceIn(dpToPx(210f), (screenW - dpToPx(16f)).coerceAtLeast(dpToPx(210f)))
            val heightPx = dpToPx(session.heightDp).coerceIn(dpToPx(210f), (screenH - dpToPx(64f)).coerceAtLeast(dpToPx(210f)))

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

            val view = viewFactory.createFloatingWindowView(
                session = session,
                config = currentConfig,
                onFocus = { FloatXWindowRegistry.focusWindow(session.id) },
                onDragDelta = { dx, dy ->
                    params.x = (params.x + dx).coerceIn(0, (screenW - dpToPx(80f)).coerceAtLeast(0))
                    params.y = (params.y + dy).coerceIn(dpToPx(24f), (screenH - dpToPx(80f)).coerceAtLeast(dpToPx(24f)))
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                    FloatXWindowRegistry.moveWindow(session.id, pxToDp(params.x), pxToDp(params.y))
                },
                onResizeDelta = { dw, dh ->
                    val newW = (params.width + dw).coerceIn(dpToPx(210f), (screenW - dpToPx(16f)).coerceAtLeast(dpToPx(210f)))
                    val newH = (params.height + dh).coerceIn(dpToPx(210f), (screenH - dpToPx(64f)).coerceAtLeast(dpToPx(210f)))
                    params.width = newW
                    params.height = newH
                    updateViewLayoutSafe(attachedWindows[session.id]?.view, params)
                    FloatXWindowRegistry.resizeWindow(session.id, pxToDp(newW), pxToDp(newH))
                },
                onMinimize = { FloatXWindowRegistry.setMinimized(session.id, true) },
                onClose = { FloatXWindowRegistry.closeWindow(session.id) },
                onLaunchFreeform = {
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
            view.animate().scaleX(1f).scaleY(1f).alpha(session.opacity).setDuration(190).start()
            AttachedWindowHolder(view, params, isMinimized = false, zOrder = session.zOrder)
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
