package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.data.FloatingWindowSession
import com.example.data.InstalledAppInfo
import com.example.data.InstalledAppsRepository
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import com.example.data.ThemeMode
import com.example.data.WindowSizePreset
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayViewFactory(
    private val context: Context,
    private val appsRepository: InstalledAppsRepository
) {

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).roundToInt()

    private fun isDark(config: SidebarConfig): Boolean = when (config.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            uiMode == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun frostedPanelBg(dark: Boolean, opacity: Float, radiusDp: Float = 24f): GradientDrawable {
        val alpha = (opacity.coerceIn(0.45f, 1f) * 238).roundToInt()
        val fill = if (dark) Color.argb(alpha, 18, 22, 32) else Color.argb(alpha, 250, 252, 255)
        val stroke = if (dark) Color.argb(60, 255, 255, 255) else Color.argb(38, 15, 23, 42)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun pillBg(colorInt: Int, radiusDp: Float = 99f, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(colorInt)
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1f).coerceAtLeast(1), strokeColor)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createEdgeHandleView(
        config: SidebarConfig,
        onExpand: () -> Unit,
        onDragVerticalDelta: (Int) -> Unit,
        onDragFinished: () -> Unit
    ): View {
        val container = FrameLayout(context).apply {
            val touchW = dp(24f)
            val touchH = dp((config.handleHeightDp + 24).toFloat())
            layoutParams = FrameLayout.LayoutParams(touchW, touchH)
            alpha = if (config.autoHide) (config.opacity * 0.65f).coerceIn(0.32f, 0.9f) else config.opacity
        }

        val pill = View(context).apply {
            val w = dp(config.handleWidthDp.toFloat()).coerceAtLeast(dp(4f))
            val h = dp(config.handleHeightDp.toFloat())
            val gravity = if (config.edge == SidebarEdge.LEFT) {
                Gravity.START or Gravity.CENTER_VERTICAL
            } else {
                Gravity.END or Gravity.CENTER_VERTICAL
            }
            layoutParams = FrameLayout.LayoutParams(w, h, gravity).apply {
                if (config.edge == SidebarEdge.LEFT) marginStart = dp(2f) else marginEnd = dp(2f)
            }
            background = pillBg(
                colorInt = config.accent.argb,
                radiusDp = 99f,
                strokeColor = Color.argb(95, 255, 255, 255)
            )
            elevation = dp(4f).toFloat()
        }

        container.addView(pill)

        var downRawX = 0f
        var downRawY = 0f
        var lastRawY = 0f
        var draggedVertically = false

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawY = event.rawY
                    draggedVertically = false
                    v.animate().alpha(1f).scaleY(1.08f).setDuration(120).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val inwardSwipe = if (config.edge == SidebarEdge.LEFT) dx > dp(24f) else dx < -dp(24f)
                    if (inwardSwipe && !draggedVertically) {
                        v.animate().scaleY(1f).setDuration(100).start()
                        onExpand()
                        return@setOnTouchListener true
                    }
                    if (abs(dy) > dp(10f)) {
                        draggedVertically = true
                        val stepY = (event.rawY - lastRawY).roundToInt()
                        lastRawY = event.rawY
                        onDragVerticalDelta(stepY)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val restAlpha = if (config.autoHide) {
                        (config.opacity * 0.65f).coerceIn(0.32f, 0.9f)
                    } else {
                        config.opacity
                    }
                    v.animate().alpha(restAlpha).scaleY(1f).setDuration(180).start()
                    if (draggedVertically) {
                        onDragFinished()
                    } else if (abs(event.rawX - downRawX) < dp(16f) && abs(event.rawY - downRawY) < dp(16f)) {
                        onExpand()
                    }
                    true
                }

                else -> false
            }
        }

        return container
    }

    fun createSidebarPanelView(
        config: SidebarConfig,
        apps: List<InstalledAppInfo>,
        activeWindows: List<FloatingWindowSession>,
        onAppClick: (InstalledAppInfo) -> Unit,
        onWindowClick: (FloatingWindowSession) -> Unit,
        onOpenStudio: () -> Unit,
        onCollapse: () -> Unit
    ): View {
        val dark = isDark(config)
        val primaryText = if (dark) Color.parseColor("#F5F7FB") else Color.parseColor("#0F172A")
        val secondaryText = if (dark) Color.parseColor("#94A0B8") else Color.parseColor("#5B6577")

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = frostedPanelBg(dark, config.opacity, 28f)
            setPadding(dp(8f), dp(12f), dp(8f), dp(12f))
            elevation = dp(12f).toFloat()
        }

        // Top collapse pill button
        val headerBtn = TextView(context).apply {
            text = "FloatX"
            setTextColor(config.accent.argb)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(4f), dp(6f), dp(6f))
            setOnClickListener { onCollapse() }
        }
        root.addView(headerBtn)

        // Active windows indicator badge row if windows exist
        if (activeWindows.isNotEmpty()) {
            val activeLabel = TextView(context).apply {
                text = "${activeWindows.size} Open"
                setTextColor(Color.parseColor("#30D158"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                background = pillBg(Color.argb(38, 48, 209, 88), 10f)
            }
            root.addView(
                activeLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8f) }
            )

            activeWindows.take(3).forEach { win ->
                val badge = createAppIconCell(
                    label = win.appLabel,
                    packageName = win.packageName,
                    textColor = primaryText,
                    highlightRing = if (win.isMinimized) Color.parseColor("#FF9F0A") else Color.parseColor("#30D158")
                ) {
                    onWindowClick(win)
                }
                root.addView(badge)
            }

            val divider = View(context).apply {
                setBackgroundColor(if (dark) Color.argb(45, 255, 255, 255) else Color.argb(32, 15, 23, 42))
            }
            root.addView(
                divider,
                LinearLayout.LayoutParams(dp(36f), dp(1f)).apply {
                    topMargin = dp(4f)
                    bottomMargin = dp(8f)
                }
            )
        }

        // Scrollable apps strip (pinned first, up to 24 quick apps for instant low-RAM rendering)
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val appsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val displayApps = apps.take(24)
        if (displayApps.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "No apps"
                setTextColor(secondaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(dp(4f), dp(12f), dp(4f), dp(12f))
            }
            appsColumn.addView(emptyTv)
        } else {
            displayApps.forEach { app ->
                val ringColor = if (app.isPinned) config.accent.argb else Color.TRANSPARENT
                appsColumn.addView(
                    createAppIconCell(
                        label = app.label,
                        packageName = app.packageName,
                        textColor = primaryText,
                        highlightRing = ringColor
                    ) {
                        onAppClick(app)
                    }
                )
            }
        }
        scrollView.addView(appsColumn)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(290f)
            )
        )

        // Bottom controls: Hub & Hide
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }

        val studioBtn = TextView(context).apply {
            text = "Apps"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillBg(config.accent.argb, 14f)
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
            setOnClickListener { onOpenStudio() }
        }
        actionsRow.addView(
            studioBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6f) }
        )

        val hideBtn = TextView(context).apply {
            text = "Hide"
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            background = pillBg(
                if (dark) Color.argb(38, 255, 255, 255) else Color.argb(24, 15, 23, 42),
                12f
            )
            setPadding(dp(8f), dp(5f), dp(8f), dp(5f))
            setOnClickListener { onCollapse() }
        }
        actionsRow.addView(
            hideBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(actionsRow)
        return root
    }

    private fun createAppIconCell(
        label: String,
        packageName: String,
        textColor: Int,
        highlightRing: Int,
        onClick: () -> Unit
    ): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4f), dp(5f), dp(4f), dp(6f))
            setOnClickListener { onClick() }
        }

        val iconHolder = FrameLayout(context).apply {
            val size = dp(42f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            if (highlightRing != Color.TRANSPARENT) {
                background = pillBg(Color.TRANSPARENT, 14f, highlightRing)
                setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            }
        }

        val iconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            val bmp = appsRepository.getAppIconBitmap(packageName, 84)
            if (bmp != null) {
                setImageBitmap(bmp)
            } else {
                setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
        iconHolder.addView(
            iconView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val labelView = TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(3f), 0, 0)
        }

        cell.addView(iconHolder)
        cell.addView(
            labelView,
            LinearLayout.LayoutParams(dp(56f), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return cell
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createMinimizedBubbleView(
        session: FloatingWindowSession,
        config: SidebarConfig,
        onTapRestore: () -> Unit,
        onDragDelta: (Int, Int) -> Unit,
        onDragEnd: () -> Unit
    ): View {
        val dark = isDark(config)
        val size = dp(56f)
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) Color.argb(235, 22, 27, 40) else Color.argb(242, 255, 255, 255))
                setStroke(dp(2f), session.accentArgb)
            }
            elevation = dp(10f).toFloat()
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }

        val iconView = ImageView(context).apply {
            val bmp = appsRepository.getAppIconBitmap(session.packageName, 96)
            if (bmp != null) {
                setImageBitmap(bmp)
            } else {
                setImageResource(android.R.drawable.sym_def_app_icon)
            }
        }
        container.addView(
            iconView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f
        var moved = false

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    moved = false
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX).roundToInt()
                    val dy = (event.rawY - lastY).roundToInt()
                    if (abs(event.rawX - downX) > dp(6f) || abs(event.rawY - downY) > dp(6f)) {
                        moved = true
                    }
                    if (moved) {
                        lastX = event.rawX
                        lastY = event.rawY
                        onDragDelta(dx, dy)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!moved) onTapRestore() else onDragEnd()
                    true
                }

                else -> false
            }
        }

        return container
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingWindowView(
        session: FloatingWindowSession,
        config: SidebarConfig,
        onFocus: () -> Unit,
        onDragDelta: (Int, Int) -> Unit,
        onResizeDelta: (Int, Int) -> Unit,
        onMinimize: () -> Unit,
        onClose: () -> Unit,
        onLaunchFreeform: () -> Unit,
        onSelectPreset: (WindowSizePreset) -> Unit
    ): View {
        val dark = isDark(config)
        val primaryText = if (dark) Color.parseColor("#F5F7FB") else Color.parseColor("#0F172A")
        val secondaryText = if (dark) Color.parseColor("#94A0B8") else Color.parseColor("#5B6577")
        val surfaceAlt = if (dark) Color.argb(110, 30, 38, 54) else Color.argb(140, 232, 238, 248)

        val root = FrameLayout(context).apply {
            background = frostedPanelBg(dark, session.opacity, 22f)
            elevation = dp(14f).toFloat()
            setOnClickListener { onFocus() }
        }

        val contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Draggable iOS-inspired Titlebar
        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(10f), dp(10f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    dp(22f).toFloat(), dp(22f).toFloat(),
                    dp(22f).toFloat(), dp(22f).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setColor(if (dark) Color.argb(150, 28, 34, 48) else Color.argb(190, 240, 244, 252))
            }
        }

        val appIcon = ImageView(context).apply {
            val bmp = appsRepository.getAppIconBitmap(session.packageName, 72)
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.sym_def_app_icon)
        }
        titleBar.addView(appIcon, LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(8f) })

        val titleTv = TextView(context).apply {
            text = session.appLabel
            setTextColor(primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titleBar.addView(
            titleTv,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        // Traffic-light window action buttons (Minimize, Freeform, Close)
        titleBar.addView(createTrafficDot(Color.parseColor("#FF9F0A"), "Minimize") { onMinimize() })
        titleBar.addView(createTrafficDot(Color.parseColor("#30D158"), "Freeform") { onLaunchFreeform() })
        titleBar.addView(createTrafficDot(Color.parseColor("#FF453A"), "Close") { onClose() })

        // Drag listener on Titlebar
        var lastX = 0f
        var lastY = 0f
        titleBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onFocus()
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - lastX).roundToInt()
                    val dy = (event.rawY - lastY).roundToInt()
                    lastX = event.rawX
                    lastY = event.rawY
                    onDragDelta(dx, dy)
                    true
                }

                else -> true
            }
        }
        contentCol.addView(
            titleBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Window Body
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14f), dp(14f), dp(14f), dp(18f))
        }

        val heroIcon = ImageView(context).apply {
            val bmp = appsRepository.getAppIconBitmap(session.packageName, 120)
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.sym_def_app_icon)
        }
        body.addView(
            heroIcon,
            LinearLayout.LayoutParams(dp(48f), dp(48f)).apply { bottomMargin = dp(8f) }
        )

        val dimText = TextView(context).apply {
            tag = "dimensions_label"
            text = "${session.widthDp.roundToInt()} × ${session.heightDp.roundToInt()} dp · Freeform Ready"
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }
        body.addView(
            dimText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12f) }
        )

        // Primary action: Launch in real Android Freeform bounds
        val openFreeformBtn = TextView(context).apply {
            text = "Open in Freeform Window"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pillBg(session.accentArgb, 16f)
            setPadding(dp(14f), dp(10f), dp(14f), dp(10f))
            setOnClickListener { onLaunchFreeform() }
        }
        body.addView(
            openFreeformBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) }
        )

        // Size Preset Chips Row
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        WindowSizePreset.entries.forEach { preset ->
            val chip = TextView(context).apply {
                text = preset.label
                setTextColor(primaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                gravity = Gravity.CENTER
                background = pillBg(surfaceAlt, 12f)
                setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
                setOnClickListener { onSelectPreset(preset) }
            }
            presetRow.addView(
                chip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(3f)
                    marginEnd = dp(3f)
                }
            )
        }
        body.addView(
            presetRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        contentCol.addView(
            body,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            contentCol,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Bottom-Right Corner Resize Handle
        val resizeGrip = FrameLayout(context).apply {
            val gripSize = dp(32f)
            layoutParams = FrameLayout.LayoutParams(gripSize, gripSize, Gravity.BOTTOM or Gravity.END)
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val gripDot = View(context).apply {
            background = pillBg(
                colorInt = if (dark) Color.argb(140, 255, 255, 255) else Color.argb(110, 15, 23, 42),
                radiusDp = 6f
            )
        }
        resizeGrip.addView(
            gripDot,
            FrameLayout.LayoutParams(dp(10f), dp(10f), Gravity.BOTTOM or Gravity.END)
        )

        var rLastX = 0f
        var rLastY = 0f
        resizeGrip.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onFocus()
                    rLastX = event.rawX
                    rLastY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - rLastX).roundToInt()
                    val dy = (event.rawY - rLastY).roundToInt()
                    rLastX = event.rawX
                    rLastY = event.rawY
                    onResizeDelta(dx, dy)
                    true
                }

                else -> true
            }
        }
        root.addView(resizeGrip)

        return root
    }

    private fun createTrafficDot(color: Int, desc: String, onClick: () -> Unit): View {
        val hitTarget = FrameLayout(context).apply {
            contentDescription = desc
            val hit = dp(28f)
            layoutParams = LinearLayout.LayoutParams(hit, hit)
            setOnClickListener { onClick() }
        }
        val dot = View(context).apply {
            val d = dp(13f)
            layoutParams = FrameLayout.LayoutParams(d, d, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
        hitTarget.addView(dot)
        return hitTarget
    }
}
