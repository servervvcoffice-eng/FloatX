package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.SidebarAccent
import com.example.data.SidebarConfig
import com.example.data.SidebarEdge
import com.example.data.ThemeMode
import com.example.data.WindowSizePreset
import com.example.ui.components.FrostedCard
import com.example.ui.components.SegmentedPillControl
import kotlin.math.roundToInt

@Composable
fun CustomizeScreen(
    config: SidebarConfig,
    isServiceRunning: Boolean,
    onToggleService: (Boolean) -> Unit,
    onSetEdge: (SidebarEdge) -> Unit,
    onSetVerticalBias: (Float) -> Unit,
    onSetHandleHeight: (Int) -> Unit,
    onSetHandleWidth: (Int) -> Unit,
    onSetOpacity: (Float) -> Unit,
    onSetAutoHide: (Boolean) -> Unit,
    onSetBlurEffect: (Boolean) -> Unit,
    onSetShowFloatingBubbles: (Boolean) -> Unit,
    onSetAccent: (SidebarAccent) -> Unit,
    onSetDefaultPreset: (WindowSizePreset) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local 120Hz slider state so live preview updates smoothly while committing to DataStore on drag end
    var draftHeight by remember(config.handleHeightDp) { mutableFloatStateOf(config.handleHeightDp.toFloat()) }
    var draftWidth by remember(config.handleWidthDp) { mutableFloatStateOf(config.handleWidthDp.toFloat()) }
    var draftBias by remember(config.verticalBias) { mutableFloatStateOf(config.verticalBias) }
    var draftOpacity by remember(config.opacity) { mutableFloatStateOf(config.opacity) }

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
            item {
                Column {
                    Text(
                        text = "Sidebar Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Customize edge handle geometry, glass opacity, and theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Handle Geometry Preview Card
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_workspace_backdrop_1795440868693),
                            contentDescription = "Sidebar preview canvas",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.36f))
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${config.edge.label} · ${draftHeight.roundToInt()}×${draftWidth.roundToInt()} dp",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Opacity ${(draftOpacity * 100).roundToInt()}% · ${config.accent.label} Accent",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val align = if (config.edge == SidebarEdge.LEFT) Alignment.TopStart else Alignment.TopEnd
                        val yDp = ((150f - (draftHeight * 0.7f)) * draftBias)
                            .coerceIn(10f, 96f)
                        Box(
                            modifier = Modifier
                                .align(align)
                                .offset(y = yDp.dp)
                                .padding(horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(draftWidth.dp)
                                    .height((draftHeight * 0.7f).dp)
                                    .alpha(draftOpacity)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(config.accent.color)
                                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }

            // Placement & Visibility
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Edge Placement & Behavior",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        SegmentedPillControl(
                            items = SidebarEdge.entries,
                            selectedItem = config.edge,
                            labelProvider = { it.label },
                            onSelect = onSetEdge,
                            testTagPrefix = "sidebar_edge"
                        )

                        TogglePreferenceRow(
                            title = "Show Floating Sidebar",
                            subtitle = "Keep the edge handle active over all apps",
                            checked = isServiceRunning,
                            onCheckedChange = onToggleService,
                            testTag = "customize_sidebar_toggle"
                        )

                        TogglePreferenceRow(
                            title = "Floating App Bubbles",
                            subtitle = "Show compact edge orb to reopen active apps (long-press to resize)",
                            checked = config.showFloatingBubbles,
                            onCheckedChange = onSetShowFloatingBubbles,
                            testTag = "customize_bubbles_toggle"
                        )

                        TogglePreferenceRow(
                            title = "Auto-Dim Handle When Idle",
                            subtitle = "Subtly fades handle when not touched",
                            checked = config.autoHide,
                            onCheckedChange = onSetAutoHide,
                            testTag = "customize_autohide_toggle"
                        )

                        TogglePreferenceRow(
                            title = "Frosted Glass Effect",
                            subtitle = "Translucent iOS-style floating panels",
                            checked = config.blurEffect,
                            onCheckedChange = onSetBlurEffect,
                            testTag = "customize_blur_toggle"
                        )
                    }
                }
            }

            // Geometry Sliders
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Handle Dimensions & Opacity",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        LabeledSlider(
                            label = "Handle Length",
                            valueText = "${draftHeight.roundToInt()} dp",
                            value = draftHeight,
                            valueRange = 48f..140f,
                            onValueChange = { draftHeight = it },
                            onValueChangeFinished = { onSetHandleHeight(draftHeight.roundToInt()) }
                        )

                        LabeledSlider(
                            label = "Handle Thickness",
                            valueText = "${draftWidth.roundToInt()} dp",
                            value = draftWidth,
                            valueRange = 4f..10f,
                            onValueChange = { draftWidth = it },
                            onValueChangeFinished = { onSetHandleWidth(draftWidth.roundToInt()) }
                        )

                        LabeledSlider(
                            label = "Vertical Position",
                            valueText = "${(draftBias * 100).roundToInt()}%",
                            value = draftBias,
                            valueRange = 0.15f..0.85f,
                            onValueChange = { draftBias = it },
                            onValueChangeFinished = { onSetVerticalBias(draftBias) }
                        )

                        LabeledSlider(
                            label = "Panel Transparency",
                            valueText = "${(draftOpacity * 100).roundToInt()}%",
                            value = draftOpacity,
                            valueRange = 0.35f..1.0f,
                            onValueChange = { draftOpacity = it },
                            onValueChangeFinished = { onSetOpacity(draftOpacity) }
                        )
                    }
                }
            }

            // Default Floating Window Size & Theme
            item {
                FrostedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Default Window Size",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SegmentedPillControl(
                                items = WindowSizePreset.entries,
                                selectedItem = config.defaultPreset,
                                labelProvider = { it.label },
                                onSelect = onSetDefaultPreset,
                                testTagPrefix = "default_preset"
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Handle Accent Tint",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SidebarAccent.entries.forEach { swatch ->
                                    val selected = swatch == config.accent
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { onSetAccent(swatch) }
                                            .padding(4.dp)
                                            .testTag("accent_${swatch.name.lowercase()}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(swatch.color)
                                                .border(
                                                    width = if (selected) 2.5.dp else 1.dp,
                                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected ${swatch.label}",
                                                    tint = if (swatch == SidebarAccent.ICE_WHITE) Color(0xFF0F172A)
                                                    else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = swatch.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Appearance Theme",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SegmentedPillControl(
                                items = ThemeMode.entries,
                                selectedItem = config.themeMode,
                                labelProvider = { it.label },
                                onSelect = onSetThemeMode,
                                testTagPrefix = "theme_mode"
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TogglePreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange
        )
    }
}
