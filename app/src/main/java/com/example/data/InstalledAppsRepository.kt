package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    // Memory-bounded LRU icon cache (~3 MB max) for low RAM footprint
    private val iconCache = object : LruCache<String, Bitmap>(3 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    suspend fun loadInstalledApps(pinnedPackages: Set<String>): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(launcherIntent, 0)
            }

            val ownPackage = appContext.packageName
            val seen = HashSet<String>(resolved.size)
            val result = ArrayList<InstalledAppInfo>(resolved.size)

            for (info in resolved) {
                val activityInfo = info.activityInfo ?: continue
                val pkg = activityInfo.packageName ?: continue
                if (pkg == ownPackage || !seen.add(pkg)) continue

                val label = runCatching { info.loadLabel(packageManager)?.toString() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: pkg

                val isSystem = (activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                result.add(
                    InstalledAppInfo(
                        packageName = pkg,
                        label = label,
                        activityName = activityInfo.name,
                        isSystemApp = isSystem,
                        isPinned = pkg in pinnedPackages
                    )
                )
            }

            result.sortedWith(
                compareByDescending<InstalledAppInfo> { it.isPinned }
                    .thenBy { it.label.lowercase() }
            )
        }

    fun getAppIconBitmap(packageName: String, sizePx: Int = 96): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return null
        val bitmap = drawableToScaledBitmap(drawable, sizePx)
        iconCache.put(packageName, bitmap)
        return bitmap
    }

    fun getAppIconDrawable(packageName: String): Drawable? {
        return runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    fun clearLowMemoryCache() {
        iconCache.evictAll()
    }

    private fun drawableToScaledBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
