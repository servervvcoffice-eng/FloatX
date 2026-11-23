package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
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

    private fun contrastTextOn(bgColor: Int): Int {
        return if (ColorUtils.calculateLuminance(bgColor) > 0.52) {
            Color.parseColor("#0F172A")
        } else {
            Color.WHITE
        }
    }

    private fun readableAccentText(accentArgb: Int, dark: Boolean): Int {
        val lum = ColorUtils.calculateLuminance(accentArgb)
        return when {
            !dark && lum > 0.6 -> Color.parseColor("#3A3D45")
            dark && lum < 0.15 -> Color.parseColor("#D1D5DB")
            else -> accentArgb
        }
    }

    private fun frostedPanelBg(dark: Boolean, opacity: Float, radiusDp: Float = 14f): GradientDrawable {
        // Refined minimal gray high-contrast surface so background icons never bleed through
        val alpha = (opacity.coerceIn(0.84f, 1f) * 248).roundToInt().coerceIn(216, 252)
        val fill = if (dark) Color.argb(alpha, 36, 39, 46) else Color.argb(alpha, 236, 238, 242)
        val stroke = if (dark) Color.argb(72, 255, 255, 255) else Color.argb(46, 15, 23, 42)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
    }

    private fun pillBg(
        colorInt: Int,
        radiusDp: Float = 99f,
        strokeColor: Int = Color.TRANSPARENT
    ): GradientDrawable {
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
    fun createBackdropScrimView(onOutsideTap: () -> Unit): View {
        return View(context).apply {
            setBackgroundColor(Color.argb(14, 0, 0, 0))
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onOutsideTap()
                    true
                } else {
                    false
                }
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
            // Generous 32dp invisible hit zone so edge handle opens reliably without triggering Back gesture
            val touchW = dp(32f)
            val touchH = dp((config.handleHeightDp + 32).toFloat())
            layoutParams = FrameLayout.LayoutParams(touchW, touchH)
            alpha = if (config.autoHide) (config.opacity * 0.72f).coerceIn(0.42f, 0.92f) else config.opacity
        }

        // Prevent Android 10-16 / HyperOS 3 edge Back gesture from hijacking the sidebar handle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val applyExclusion = { view: View ->
                if (view.width > 0 && view.height > 0) {
                    view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height))
                }
            }
            container.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                applyExclusion(v)
            }
            container.post { applyExclusion(container) }
        }

        val bar = View(context).apply {
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
            // Crisp smaller border radius (4dp) on the gray edge handle bar
            background = pillBg(
                colorInt = config.accent.argb,
                radiusDp = 4f,
                strokeColor = Color.argb(95, 255, 255, 255)
            )
            elevation = dp(4f).toFloat()
        }

        container.addView(bar)

        var downRawX = 0f
        var downRawY = 0f
        var lastRawY = 0f
        var draggedVertically = false
        var openedInGesture = false

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawY = event.rawY
                    draggedVertically = false
                    openedInGesture = false
                    v.animate().alpha(1f).scaleY(1.06f).setDuration(100).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (openedInGesture) return@setOnTouchListener true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val inwardSwipe = if (config.edge == SidebarEdge.LEFT) dx > dp(12f) else dx < -dp(12f)

                    if (inwardSwipe && !draggedVertically) {
                        openedInGesture = true
                        v.animate().scaleY(1f).setDuration(90).start()
                        onExpand()
                        return@setOnTouchListener true
                    }

                    // Only start vertical reposition if clearly dragging straight up/down
                    if (abs(dy) > dp(18f) && abs(dy) > abs(dx) * 1.4f) {
                        draggedVertically = true
                    }

                    if (draggedVertically) {
                        val stepY = (event.rawY - lastRawY).roundToInt()
                        lastRawY = event.rawY
                        onDragVerticalDelta(stepY)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val restAlpha = if (config.autoHide) {
                        (config.opacity * 0.72f).coerceIn(0.42f, 0.92f)
                    } else {
                        config.opacity
                    }
                    v.animate().alpha(restAlpha).scaleY(1f).setDuration(160).start()
                    if (draggedVertically) {
                        onDragFinished()
                    } else if (!openedInGesture && event.actionMasked == MotionEvent.ACTION_UP) {
                        // Reliable single-tap open so users never have to fight edge swipe gestures
                        onExpand()
                    }
                    true
                }

                else -> false
            }
        }

        return container
    }

    @SuppressLint("ClickableViewAccessibility")
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
        val primaryText = if (dark) Color.parseColor("#F3F4F6") else Color.parseColor("#181B20")
        val secondaryText = if (dark) Color.parseColor("#A1A7B3") else Color.parseColor("#5B616E")
        val brandAccent = readableAccentText(config.accent.argb, dark)
        val buttonBg = if (ColorUtils.calculateLuminance(config.accent.argb) > 0.72) {
            Color.parseColor("#6B7280")
        } else {
            config.accent.argb
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Sleek smaller border radius (14dp) + refined gray frosted background
            background = frostedPanelBg(dark, config.opacity, 14f)
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
            elevation = dp(14f).toFloat()
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    onCollapse()
                    true
                } else {
                    false
                }
            }
        }

        // Header
        val headerBtn = TextView(context).apply {
            text = "FloatX"
            setTextColor(brandAccent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding(dp(4f), dp(4f), dp(4f), dp(8f))
            setOnClickListener { onCollapse() }
        }
        root.addView(headerBtn)

        // Active Floating Windows strip
        if (activeWindows.isNotEmpty()) {
            val activeLabel = TextView(context).apply {
                text = "${activeWindows.size} Open"
                setTextColor(Color.parseColor("#30D158"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                typeface = Typeface.DEFAULT_BOLD
                isSingleLine = true
                gravity = Gravity.CENTER
                setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                background = pillBg(Color.argb(42, 48, 209, 88), 10f)
            }
            root.addView(
                activeLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6f) }
            )

            activeWindows.take(3).forEach { win ->
                val badge = createAppIconCell(
                    label = win.appLabel,
                    packageName = win.packageName,
                    textColor = primaryText,
                    highlightRing = Color.parseColor("#30D158")
                ) {
                    onWindowClick(win)
                }
                root.addView(badge)
            }

            val divider = View(context).apply {
                setBackgroundColor(if (dark) Color.argb(50, 255, 255, 255) else Color.argb(36, 15, 23, 42))
            }
            root.addView(
                divider,
                LinearLayout.LayoutParams(dp(40f), dp(1f)).apply {
                    topMargin = dp(4f)
                    bottomMargin = dp(8f)
                }
            )
        }

        // Scrollable installed apps strip
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val appsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val displayApps = apps.take(28)
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
                val ringColor = if (app.isPinned) brandAccent else Color.TRANSPARENT
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
                dp(300f)
            )
        )

        // Bottom controls
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8f), 0, 0)
        }

        val studioBtn = TextView(context).apply {
            text = "Apps"
            setTextColor(contrastTextOn(buttonBg))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            gravity = Gravity.CENTER
            background = pillBg(buttonBg, 14f)
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
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
            isSingleLine = true
            gravity = Gravity.CENTER
            background = pillBg(
                if (dark) Color.argb(44, 255, 255, 255) else Color.argb(26, 15, 23, 42),
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
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, dp(3f), 0, 0)
        }

        cell.addView(iconHolder)
        cell.addView(
            labelView,
            LinearLayout.LayoutParams(dp(58f), LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        return cell
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingBubbleView(
        session: FloatingWindowSession,
        config: SidebarConfig,
        onTapLaunchFreeform: () -> Unit,
        onLongPressResize: () -> Unit,
        onDragDelta: (Int, Int) -> Unit,
        onDragEnd: () -> Unit
    ): View {
        val dark = isDark(config)
        val size = dp(48f)
        val ringColor = if (ColorUtils.calculateLuminance(session.accentArgb) > 0.72) {
            Color.parseColor("#007AFF")
        } else {
            session.accentArgb
        }

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) Color.argb(245, 22, 27, 40) else Color.argb(248, 255, 255, 255))
                setStroke(dp(2f), ringColor)
            }
            elevation = dp(8f).toFloat()
            setPadding(dp(7f), dp(7f), dp(7f), dp(7f))
        }

        val iconView = ImageView(context).apply {
            val bmp = appsRepository.getAppIconBitmap(session.packageName, 84)
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
        var downTime = 0L
        var moved = false
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    downTime = event.eventTime
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
                    val heldDuration = event.eventTime - downTime
                    when {
                        moved -> onDragEnd()
                        heldDuration >= longPressTimeout -> onLongPressResize()
                        else -> onTapLaunchFreeform()
                    }
                    true
                }

                else -> false
            }
        }

        return container
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createBoundsResizerView(
        session: FloatingWindowSession,
        config: SidebarConfig,
        onFocus: () -> Unit,
        onDragDelta: (Int, Int) -> Unit,
        onResizeDelta: (Int, Int) -> Unit,
        onDismissEditor: () -> Unit,
        onCloseSession: () -> Unit,
        onApplyAndLaunchFreeform: () -> Unit,
        onSelectPreset: (WindowSizePreset) -> Unit
    ): View {
        val dark = isDark(config)
        val primaryText = if (dark) Color.parseColor("#F5F7FB") else Color.parseColor("#0F172A")
        val secondaryText = if (dark) Color.parseColor("#94A0B8") else Color.parseColor("#5B6577")
        val surfaceAlt = if (dark) Color.parseColor("#242C3E") else Color.parseColor("#E6ECF7")
        val ctaBg = if (ColorUtils.calculateLuminance(session.accentArgb) > 0.65) {
            Color.parseColor("#007AFF")
        } else {
            session.accentArgb
        }
        val ctaText = contrastTextOn(ctaBg)

        val root = FrameLayout(context).apply {
            background = frostedPanelBg(dark, 0.96f, 22f)
            elevation = dp(16f).toFloat()
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
                setColor(if (dark) Color.parseColor("#1D2332") else Color.parseColor("#EFF3FA"))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        titleBar.addView(
            titleTv,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        // Minimize editor (Amber), Apply & Launch (Green), Close window (Red)
        titleBar.addView(createTrafficDot(Color.parseColor("#FF9F0A"), "Minimize to Bubble") { onDismissEditor() })
        titleBar.addView(createTrafficDot(Color.parseColor("#30D158"), "Apply & Open") { onApplyAndLaunchFreeform() })
        titleBar.addView(createTrafficDot(Color.parseColor("#FF453A"), "Close Window") { onCloseSession() })

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

        // Clean Body
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14f), dp(14f), dp(14f), dp(18f))
        }

        val heroIcon = ImageView(context).apply {
            val bmp = appsRepository.getAppIconBitmap(session.packageName, 112)
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.sym_def_app_icon)
        }
        body.addView(
            heroIcon,
            LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { bottomMargin = dp(8f) }
        )

        val dimText = TextView(context).apply {
            tag = "dimensions_label"
            text = "${session.widthDp.roundToInt()} × ${session.heightDp.roundToInt()} dp · Drag corner to resize"
            setTextColor(secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }
        body.addView(
            dimText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12f) }
        )

        // High-contrast CTA button (never white-on-white!)
        val openFreeformBtn = TextView(context).apply {
            text = "Apply & Open Window"
            setTextColor(ctaText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            background = pillBg(ctaBg, 14f)
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            setOnClickListener { onApplyAndLaunchFreeform() }
        }
        body.addView(
            openFreeformBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) }
        )

        // Single-line non-wrapping Size Preset Chips Row
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        WindowSizePreset.entries.forEach { preset ->
            val chip = TextView(context).apply {
                text = preset.label
                setTextColor(primaryText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                background = pillBg(surfaceAlt, 10f)
                setPadding(dp(4f), dp(7f), dp(4f), dp(7f))
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

        // Bottom-Right Corner Resize Grip
        val resizeGrip = FrameLayout(context).apply {
            val gripSize = dp(36f)
            layoutParams = FrameLayout.LayoutParams(gripSize, gripSize, Gravity.BOTTOM or Gravity.END)
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val gripDot = View(context).apply {
            background = pillBg(
                colorInt = if (dark) Color.argb(180, 255, 255, 255) else Color.argb(130, 15, 23, 42),
                radiusDp = 6f
            )
        }
        resizeGrip.addView(
            gripDot,
            FrameLayout.LayoutParams(dp(11f), dp(11f), Gravity.BOTTOM or Gravity.END)
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
