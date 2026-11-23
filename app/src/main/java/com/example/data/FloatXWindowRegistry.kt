package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object FloatXWindowRegistry {

    private const val MAX_CONCURRENT_WINDOWS = 6

    private val _windows = MutableStateFlow<List<FloatingWindowSession>>(emptyList())
    val windows: StateFlow<List<FloatingWindowSession>> = _windows.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _sidebarExpanded = MutableStateFlow(false)
    val sidebarExpanded: StateFlow<Boolean> = _sidebarExpanded.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
        if (!running) {
            _sidebarExpanded.value = false
        }
    }

    fun setSidebarExpanded(expanded: Boolean) {
        _sidebarExpanded.value = expanded
    }

    fun toggleSidebarExpanded() {
        _sidebarExpanded.update { !it }
    }

    fun postStatus(message: String?) {
        _statusBanner.value = message
    }

    fun openOrFocusWindow(
        app: InstalledAppInfo,
        preset: WindowSizePreset,
        accentArgb: Int,
        freeformLaunched: Boolean
    ): FloatingWindowSession {
        val existing = _windows.value.firstOrNull { it.packageName == app.packageName }
        if (existing != null) {
            val updated = existing.copy(
                isMinimized = false,
                zOrder = System.nanoTime(),
                freeformLaunched = freeformLaunched || existing.freeformLaunched
            )
            _windows.update { list ->
                list.map { if (it.id == existing.id) updated else it }
            }
            return updated
        }

        val currentCount = _windows.value.size
        val cascadeStep = (currentCount % 4)
        val session = FloatingWindowSession(
            id = UUID.randomUUID().toString(),
            packageName = app.packageName,
            appLabel = app.label,
            xDp = 20f + cascadeStep * 22f,
            yDp = 72f + cascadeStep * 34f,
            widthDp = preset.widthDp.toFloat(),
            heightDp = preset.heightDp.toFloat(),
            isMinimized = false,
            opacity = 0.96f,
            zOrder = System.nanoTime(),
            freeformLaunched = freeformLaunched,
            accentArgb = accentArgb
        )

        _windows.update { list ->
            val trimmed = if (list.size >= MAX_CONCURRENT_WINDOWS) {
                list.sortedBy { it.zOrder }.drop(1)
            } else {
                list
            }
            trimmed + session
        }
        return session
    }

    fun focusWindow(windowId: String) {
        _windows.update { list ->
            list.map {
                if (it.id == windowId) it.copy(zOrder = System.nanoTime(), isMinimized = false)
                else it
            }
        }
    }

    fun moveWindow(windowId: String, newXDp: Float, newYDp: Float) {
        _windows.update { list ->
            list.map {
                if (it.id == windowId) it.copy(xDp = newXDp, yDp = newYDp) else it
            }
        }
    }

    fun resizeWindow(windowId: String, newWidthDp: Float, newHeightDp: Float) {
        val clampedW = newWidthDp.coerceIn(210f, 440f)
        val clampedH = newHeightDp.coerceIn(210f, 640f)
        _windows.update { list ->
            list.map {
                if (it.id == windowId) it.copy(widthDp = clampedW, heightDp = clampedH) else it
            }
        }
    }

    fun applyPreset(windowId: String, preset: WindowSizePreset) {
        resizeWindow(windowId, preset.widthDp.toFloat(), preset.heightDp.toFloat())
    }

    fun setWindowOpacity(windowId: String, opacity: Float) {
        val clamped = opacity.coerceIn(0.45f, 1.0f)
        _windows.update { list ->
            list.map {
                if (it.id == windowId) it.copy(opacity = clamped) else it
            }
        }
    }

    fun setMinimized(windowId: String, minimized: Boolean) {
        _windows.update { list ->
            list.map {
                if (it.id == windowId) {
                    it.copy(
                        isMinimized = minimized,
                        showBoundsEditor = if (minimized) false else it.showBoundsEditor,
                        zOrder = if (!minimized) System.nanoTime() else it.zOrder
                    )
                } else {
                    it
                }
            }
        }
    }

    fun setBoundsEditorVisible(windowId: String, visible: Boolean) {
        _windows.update { list ->
            list.map {
                if (it.id == windowId) {
                    it.copy(
                        showBoundsEditor = visible,
                        isMinimized = if (visible) false else it.isMinimized,
                        zOrder = if (visible) System.nanoTime() else it.zOrder
                    )
                } else {
                    if (visible) it.copy(showBoundsEditor = false) else it
                }
            }
        }
    }

    fun closeWindow(windowId: String) {
        _windows.update { list -> list.filterNot { it.id == windowId } }
    }

    fun minimizeAll() {
        _windows.update { list -> list.map { it.copy(isMinimized = true, showBoundsEditor = false) } }
    }

    fun cascadeAll() {
        _windows.update { list ->
            list.mapIndexed { index, session ->
                val step = index % 4
                session.copy(
                    isMinimized = false,
                    showBoundsEditor = false,
                    xDp = 18f + step * 22f,
                    yDp = 64f + step * 34f,
                    zOrder = System.nanoTime() + index
                )
            }
        }
    }

    fun closeAll() {
        _windows.value = emptyList()
    }
}
