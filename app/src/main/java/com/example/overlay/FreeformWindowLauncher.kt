package com.example.overlay

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlin.math.roundToInt

sealed class FreeformLaunchResult {
    data class FreeformSuccess(val bounds: Rect) : FreeformLaunchResult()
    data object StandardFallback : FreeformLaunchResult()
    data class Failed(val reason: String) : FreeformLaunchResult()
}

data class DeviceEnvironmentInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val isHyperOsOrMiui: Boolean,
    val hyperOsLabel: String
)

object FreeformWindowLauncher {

    // Android WindowConfiguration.WINDOWING_MODE_FREEFORM = 5
    private const val WINDOWING_MODE_FREEFORM = 5

    fun getDeviceEnvironment(): DeviceEnvironmentInfo {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val miuiProp = readSystemProperty("ro.miui.ui.version.name")
        val hyperOsProp = readSystemProperty("ro.mi.os.version.name")
        val isXiaomi = manufacturer.equals("Xiaomi", ignoreCase = true) ||
            manufacturer.equals("Redmi", ignoreCase = true) ||
            manufacturer.equals("POCO", ignoreCase = true) ||
            miuiProp.isNotEmpty() ||
            hyperOsProp.isNotEmpty()

        val osLabel = when {
            hyperOsProp.isNotEmpty() -> "HyperOS $hyperOsProp"
            miuiProp.isNotEmpty() -> "HyperOS / MIUI $miuiProp"
            isXiaomi -> "Xiaomi HyperOS"
            else -> "Android ${Build.VERSION.RELEASE}"
        }

        return DeviceEnvironmentInfo(
            androidVersion = Build.VERSION.RELEASE ?: "16",
            apiLevel = Build.VERSION.SDK_INT,
            manufacturer = manufacturer.ifBlank { "Android" },
            isHyperOsOrMiui = isXiaomi,
            hyperOsLabel = osLabel
        )
    }

    fun launchInFreeformWindow(
        context: Context,
        packageName: String,
        xDp: Float,
        yDp: Float,
        widthDp: Float,
        heightDp: Float
    ): FreeformLaunchResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return FreeformLaunchResult.Failed("App cannot be launched directly")

        val density = context.resources.displayMetrics.density
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels

        val left = (xDp * density).roundToInt().coerceIn(16, (screenW - 240).coerceAtLeast(16))
        val top = (yDp * density).roundToInt().coerceIn(48, (screenH - 300).coerceAtLeast(48))
        val right = (left + (widthDp * density).roundToInt()).coerceAtMost(screenW - 16)
        val bottom = (top + (heightDp * density).roundToInt()).coerceAtMost(screenH - 48)
        val bounds = Rect(left, top, right.coerceAtLeast(left + 200), bottom.coerceAtLeast(top + 220))

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        )

        // Attempt Android Freeform + HyperOS 3 freeform launch via ActivityOptions
        val freeformSucceeded = runCatching {
            val options = ActivityOptions.makeBasic()
            options.launchBounds = bounds

            // Set WINDOWING_MODE_FREEFORM via hidden ActivityOptions setter when permitted
            runCatching {
                val method = ActivityOptions::class.java.getMethod(
                    "setLaunchWindowingMode",
                    Int::class.javaPrimitiveType
                )
                method.invoke(options, WINDOWING_MODE_FREEFORM)
            }

            // Android 14/15/16 background activity start allowance where applicable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching {
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
            }

            val bundle = options.toBundle()
            // Standard & HyperOS/MIUI freeform windowing extras
            bundle.putInt("android.activity.windowingMode", WINDOWING_MODE_FREEFORM)
            bundle.putParcelable("android:activity.launchBounds", bounds)

            context.startActivity(launchIntent, bundle)
            true
        }.getOrDefault(false)

        if (freeformSucceeded) {
            return FreeformLaunchResult.FreeformSuccess(bounds)
        }

        // Clean fallback if OEM blocks freeform flags
        return runCatching {
            val fallbackIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: return FreeformLaunchResult.Failed("Launch intent unavailable")
            context.startActivity(fallbackIntent)
            FreeformLaunchResult.StandardFallback
        }.getOrElse { err ->
            FreeformLaunchResult.Failed(err.localizedMessage ?: "Unable to open app")
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!started) {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun readSystemProperty(key: String): String {
        return runCatching {
            val propClass = Class.forName("android.os.SystemProperties")
            val getMethod = propClass.getMethod("get", String::class.java, String::class.java)
            getMethod.invoke(null, key, "") as? String ?: ""
        }.getOrDefault("")
    }
}
