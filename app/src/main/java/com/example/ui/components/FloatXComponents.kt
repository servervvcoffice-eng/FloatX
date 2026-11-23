package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.FloatingWindowSession
import com.example.data.InstalledAppInfo
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import com.example.ui.theme.WindowCloseRed
import com.example.ui.theme.WindowFreeformGreen
import com.example.ui.theme.WindowMinimizeAmber
import kotlin.math.roundToInt

@Composable
fun FrostedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        content()
    }
}

@Composable
fun AppIconImage(
    packageName: String,
    contentDescription: String,
    getBitmap: (String) -> Bitmap?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    val imageBitmap = remember(packageName) {
        getBitmap(packageName)?.asImageBitmap()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun <T> SegmentedPillControl(
    items: List<T>,
    selectedItem: T,
    labelProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val selected = item == selectedItem
                val bgColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    label = "segment_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segment_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable { onSelect(item) }
                        .testTag("${testTagPrefix}_${labelProvider(item).lowercase().replace(" ", "_")}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveFloatingStage(
    config: SidebarConfig,
    activeWindows: List<FloatingWindowSession>,
    quickApps: List<InstalledAppInfo>,
    isSidebarExpanded: Boolean,
    getIconBitmap: (String) -> Bitmap?,
    onToggleSidebar: () -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onFocusWindow: (String) -> Unit,
    onMoveWindow: (String, Float, Float) -> Unit,
    onResizeWindow: (String, Float, Float) -> Unit,
    onMinimizeWindow: (String, Boolean) -> Unit,
    onCloseWindow: (String) -> Unit,
    onOpenFreeform: (FloatingWindowSession) -> Unit,
    onBrowseApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val stageWidthDp = maxWidth
            val stageHeightDp = maxHeight
            val stageWidthPx = with(density) { stageWidthDp.toPx() }
            val stageHeightPx = with(density) { stageHeightDp.toPx() }

            // Minimalist iOS-inspired silk glass backdrop
            Image(
                painter = painterResource(id = R.drawable.img_workspace_backdrop_1795440868693),
                contentDescription = "FloatX live workspace canvas",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Subtle adaptive scrim for crisp readability in Light & Dark modes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.54f)
                            )
                        )
                    )
            )

            // Top mini status pill
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        RoundedCornerShape(99.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (activeWindows.isNotEmpty()) WindowFreeformGreen
                            else config.accent.color
                        )
                )
                Text(
                    text = if (activeWindows.isEmpty()) "Live Workspace · Tap edge bar"
                    else "${activeWindows.size} Floating Window${if (activeWindows.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Empty workspace call-to-action
            if (activeWindows.isEmpty() && !isSidebarExpanded) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        onClick = onBrowseApps,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.testTag("stage_add_window_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Launch Floating Window",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Launch Floating Window",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Drag, resize, minimize & stack multiple apps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    )
                }
            }

            // Render simultaneous interactive floating windows & minimized bubbles
            val sorted = remember(activeWindows) { activeWindows.sortedBy { it.zOrder } }
            sorted.forEachIndexed { index, session ->
                key(session.id) {
                    val scaleFactor = 0.58f
                    val previewW = (session.widthDp * scaleFactor).coerceIn(138f, (stageWidthDp.value - 24f).coerceAtLeast(138f))
                    val previewH = (session.heightDp * scaleFactor).coerceIn(124f, (stageHeightDp.value - 36f).coerceAtLeast(124f))

                    var localX by remember(session.id, session.xDp) {
                        mutableFloatStateOf(
                            ((session.xDp * 0.6f) % (stageWidthDp.value - previewW).coerceAtLeast(12f))
                                .coerceIn(10f, (stageWidthDp.value - previewW - 10f).coerceAtLeast(10f))
                        )
                    }
                    var localY by remember(session.id, session.yDp) {
                        mutableFloatStateOf(
                            ((session.yDp * 0.55f) % (stageHeightDp.value - previewH).coerceAtLeast(18f))
                                .coerceIn(42f, (stageHeightDp.value - previewH - 12f).coerceAtLeast(42f))
                        )
                    }

                    if (session.isMinimized) {
                        // Minimized Floating Bubble inside stage
                        val bubbleX = (14f + (index * 54f)).coerceAtMost((stageWidthDp.value - 58f).coerceAtLeast(14f))
                        val bubbleY = (stageHeightDp.value - 62f).coerceAtLeast(48f)
                        Surface(
                            onClick = { onMinimizeWindow(session.id, false) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(2.dp, Color(session.accentArgb)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .offset(x = bubbleX.dp, y = bubbleY.dp)
                                .size(48.dp)
                                .zIndex(10f + index)
                                .testTag("stage_bubble_${session.packageName}")
                        ) {
                            Box(
                                modifier = Modifier.padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIconImage(
                                    packageName = session.packageName,
                                    contentDescription = "Restore ${session.appLabel}",
                                    getBitmap = getIconBitmap,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 99.dp
                                )
                            }
                        }
                    } else {
                        // Interactive Draggable & Resizable Floating Window Card
                        val animW by animateDpAsState(
                            targetValue = previewW.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "win_w"
                        )
                        val animH by animateDpAsState(
                            targetValue = previewH.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "win_h"
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = session.opacity.coerceIn(0.65f, 0.96f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = with(density) { localX.dp.roundToPx() },
                                        y = with(density) { localY.dp.roundToPx() }
                                    )
                                }
                                .size(width = animW, height = animH)
                                .zIndex(20f + index)
                                .pointerInput(session.id) {
                                    detectDragGestures(
                                        onDragStart = { onFocusWindow(session.id) },
                                        onDragEnd = {
                                            onMoveWindow(session.id, localX / 0.6f, localY / 0.55f)
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val dxDp = dragAmount.x / density.density
                                        val dyDp = dragAmount.y / density.density
                                        val maxX = (stageWidthPx / density.density - previewW - 6f).coerceAtLeast(6f)
                                        val maxY = (stageHeightPx / density.density - previewH - 6f).coerceAtLeast(36f)
                                        localX = (localX + dxDp).coerceIn(6f, maxX)
                                        localY = (localY + dyDp).coerceIn(36f, maxY)
                                    }
                                }
                                .testTag("stage_window_${session.packageName}")
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Minimal iOS Titlebar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIconImage(
                                            packageName = session.packageName,
                                            contentDescription = session.appLabel,
                                            getBitmap = getIconBitmap,
                                            modifier = Modifier.size(16.dp),
                                            cornerRadius = 5.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = session.appLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Minimize
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .clickable { onMinimizeWindow(session.id, true) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(WindowMinimizeAmber)
                                            )
                                        }
                                        // Freeform Pop-out
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .clickable { onOpenFreeform(session) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(WindowFreeformGreen)
                                            )
                                        }
                                        // Close
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .clickable { onCloseWindow(session.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(WindowCloseRed)
                                            )
                                        }
                                    }

                                    // Compact Window Body
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AppIconImage(
                                            packageName = session.packageName,
                                            contentDescription = session.appLabel,
                                            getBitmap = getIconBitmap,
                                            modifier = Modifier.size(32.dp),
                                            cornerRadius = 10.dp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "${session.widthDp.roundToInt()}×${session.heightDp.roundToInt()} dp",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            onClick = { onOpenFreeform(session) },
                                            shape = RoundedCornerShape(99.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                                    contentDescription = "Launch Freeform",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "Pop Out",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Live Corner Resize Handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(26.dp)
                                        .pointerInput(session.id, session.widthDp, session.heightDp) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val dw = (dragAmount.x / density.density) / scaleFactor
                                                val dh = (dragAmount.y / density.density) / scaleFactor
                                                onResizeWindow(
                                                    session.id,
                                                    session.widthDp + dw,
                                                    session.heightDp + dh
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(5.dp)
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Customizable Floating Edge Handle inside Stage
            val handleAlign = if (config.edge == SidebarEdge.LEFT) Alignment.TopStart else Alignment.TopEnd
            val handleTopDp = ((stageHeightDp.value - config.handleHeightDp.coerceAtMost(120)) * config.verticalBias)
                .coerceIn(16f, (stageHeightDp.value - 64f).coerceAtLeast(16f))

            Box(
                modifier = Modifier
                    .align(handleAlign)
                    .offset(y = handleTopDp.dp)
                    .zIndex(50f)
                    .clickable { onToggleSidebar() }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .testTag("stage_edge_handle")
            ) {
                Box(
                    modifier = Modifier
                        .width(config.handleWidthDp.dp)
                        .height((config.handleHeightDp * 0.85f).dp)
                        .alpha(config.opacity)
                        .clip(RoundedCornerShape(4.dp))
                        .background(config.accent.color)
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                )
            }

            // Outside-tap collapse scrim inside preview stage when sidebar is open
            if (isSidebarExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(55f)
                        .background(Color.Black.copy(alpha = 0.08f))
                        .clickable { onToggleSidebar() }
                )
            }

            // Expandable Mini Floating Sidebar Preview Drawer inside Stage
            AnimatedVisibility(
                visible = isSidebarExpanded,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .align(if (config.edge == SidebarEdge.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .zIndex(60f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = config.opacity.coerceIn(0.84f, 0.96f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FloatX",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = config.accent.color
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickApps.take(4).forEach { app ->
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onToggleSidebar()
                                            onLaunchApp(app)
                                        }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIconImage(
                                        packageName = app.packageName,
                                        contentDescription = "Launch ${app.label}",
                                        getBitmap = getIconBitmap,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 10.dp
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onToggleSidebar() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Hide Sidebar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
