package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FloatingWindowSession
import com.example.data.WindowSizePreset
import com.example.ui.FloatXUiState
import com.example.ui.components.AppIconImage
import com.example.ui.components.FrostedCard
import com.example.ui.theme.WindowCloseRed
import com.example.ui.theme.WindowFreeformGreen
import com.example.ui.theme.WindowMinimizeAmber
import kotlin.math.roundToInt

@Composable
fun WindowsScreen(
    uiState: FloatXUiState,
    getIconBitmap: (String) -> Bitmap?,
    onBrowseApps: () -> Unit,
    onCascadeAll: () -> Unit,
    onMinimizeAll: () -> Unit,
    onCloseAll: () -> Unit,
    onLaunchFreeform: (FloatingWindowSession) -> Unit,
    onToggleMinimize: (String, Boolean) -> Unit,
    onApplyPreset: (String, WindowSizePreset) -> Unit,
    onChangeOpacity: (String, Float) -> Unit,
    onCloseWindow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Active Windows",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.activeWindows.size} of 6 simultaneous floating slots",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onBrowseApps,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("windows_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "New")
                    }
                }
            }

            // Multi-window Batch Action Bar
            if (uiState.activeWindows.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onCascadeAll,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cascade_windows_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.tile_windows_action),
                                maxLines = 1
                            )
                        }

                        FilledTonalButton(
                            onClick = onMinimizeAll,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("minimize_all_windows_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Minimize,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Minimize",
                                maxLines = 1
                            )
                        }

                        Surface(
                            onClick = onCloseAll,
                            shape = RoundedCornerShape(14.dp),
                            color = WindowCloseRed.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, WindowCloseRed.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("close_all_windows_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close All",
                                    tint = WindowCloseRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Close",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WindowCloseRed
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.activeWindows.isEmpty()) {
                item {
                    FrostedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.empty_windows_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.empty_windows_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onBrowseApps,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("empty_windows_browse_button")
                            ) {
                                Text(text = "Choose Apps to Float")
                            }
                        }
                    }
                }
            } else {
                items(
                    items = uiState.activeWindows.sortedByDescending { it.zOrder },
                    key = { it.id }
                ) { session ->
                    FloatingWindowControlCard(
                        session = session,
                        getIconBitmap = getIconBitmap,
                        onLaunchFreeform = { onLaunchFreeform(session) },
                        onToggleMinimize = { onToggleMinimize(session.id, !session.isMinimized) },
                        onApplyPreset = { preset -> onApplyPreset(session.id, preset) },
                        onChangeOpacity = { opacity -> onChangeOpacity(session.id, opacity) },
                        onClose = { onCloseWindow(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingWindowControlCard(
    session: FloatingWindowSession,
    getIconBitmap: (String) -> Bitmap?,
    onLaunchFreeform: () -> Unit,
    onToggleMinimize: () -> Unit,
    onApplyPreset: (WindowSizePreset) -> Unit,
    onChangeOpacity: (Float) -> Unit,
    onClose: () -> Unit
) {
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIconImage(
                    packageName = session.packageName,
                    contentDescription = session.appLabel,
                    getBitmap = getIconBitmap,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 13.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (session.isMinimized) WindowMinimizeAmber else WindowFreeformGreen
                                )
                        )
                        Text(
                            text = if (session.isMinimized) "Minimized Bubble"
                            else "${session.widthDp.roundToInt()} × ${session.heightDp.roundToInt()} dp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Minimize / Restore Button
                IconButton(
                    onClick = onToggleMinimize,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("window_minimize_${session.packageName}")
                ) {
                    Icon(
                        imageVector = if (session.isMinimized) Icons.Rounded.OpenInFull else Icons.Rounded.Minimize,
                        contentDescription = if (session.isMinimized) "Restore Window" else "Minimize Window",
                        tint = WindowMinimizeAmber
                    )
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("window_close_${session.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Window",
                        tint = WindowCloseRed
                    )
                }
            }

            // Size Preset Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WindowSizePreset.entries.forEach { preset ->
                    val isMatching = session.widthDp.roundToInt() == preset.widthDp &&
                        session.heightDp.roundToInt() == preset.heightDp
                    Surface(
                        onClick = { onApplyPreset(preset) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMatching) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isMatching) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isMatching) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                        )
                    }
                }
            }

            // Window Opacity Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Window Opacity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(session.opacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Slider(
                    value = session.opacity,
                    onValueChange = onChangeOpacity,
                    valueRange = 0.45f..1.0f
                )
            }

            // Freeform Launch Primary Button
            Button(
                onClick = onLaunchFreeform,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("window_freeform_launch_${session.packageName}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Open in System Freeform Bounds")
            }
        }
    }
}
