package com.navadeep.kanso.tv.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Reads launchable apps from the package manager. */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    /** Loaded apps by package, reused while the package hasn't been updated. */
    private val cache = HashMap<String, LauncherApp>()

    /** Loads in parallel: reading banners and icons out of each APK is the slow part. */
    suspend fun loadApps(): List<LauncherApp> = coroutineScope {
        val leanback = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val regular = query(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        // Prefer the leanback entry point when an app has both.
        val entries = (leanback.map { it to true } + regular.map { it to false }).filter { (info, _) ->
            val pkg = info.activityInfo.packageName
            pkg != context.packageName && seen.add(pkg)
        }
        val loaded = entries.map { (info, isLeanback) ->
            async(Dispatchers.IO) {
                val pkg = info.activityInfo.packageName
                val packageInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
                val updated = packageInfo?.lastUpdateTime ?: 0L
                val installed = packageInfo?.firstInstallTime ?: 0L
                val cached = synchronized(cache) { cache[pkg] }
                if (cached != null && cached.lastUpdateTime == updated && cached.isLeanback == isLeanback) {
                    cached
                } else {
                    runCatching { toLauncherApp(info, isLeanback, updated, installed) }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
        synchronized(cache) {
            cache.clear()
            loaded.forEach { cache[it.packageName] = it }
        }
        loaded
    }

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun toLauncherApp(
        info: ResolveInfo,
        isLeanback: Boolean,
        lastUpdateTime: Long,
        installTime: Long,
    ): LauncherApp {
        val activity = info.activityInfo
        val appInfo = activity.applicationInfo
        val banner = activity.loadBanner(pm) ?: appInfo.loadBanner(pm)
        val iconBitmap = info.loadIcon(pm).toSafeBitmap(ICON_PX, ICON_PX)
        return LauncherApp(
            packageName = activity.packageName,
            component = ComponentName(activity.packageName, activity.name),
            label = info.loadLabel(pm).toString().trim(),
            isLeanback = isLeanback,
            isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isUpdatedSystemApp = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            category = AppCategorizer.categorize(appInfo),
            banner = banner?.toSafeBitmap(BANNER_W, BANNER_H)?.asImageBitmap(),
            icon = iconBitmap.asImageBitmap(),
            accent = iconBitmap.dominantColor(),
            lastUpdateTime = lastUpdateTime,
            installTime = installTime,
        )
    }

    /** Rasterizes at exactly [w]x[h], roughly the on-screen size, to keep drawing cheap. */
    private fun Drawable.toSafeBitmap(w: Int, h: Int): Bitmap = toBitmap(w, h)

    /** Average of the icon's opaque pixels, weighted toward saturated ones. */
    private fun Bitmap.dominantColor(): Color {
        val small = scale(24, 24, filter = true)
        var r = 0f; var g = 0f; var b = 0f; var total = 0f
        val hsv = FloatArray(3)
        for (x in 0 until small.width) for (y in 0 until small.height) {
            val px = small.getPixel(x, y)
            if (android.graphics.Color.alpha(px) < 200) continue
            android.graphics.Color.colorToHSV(px, hsv)
            val weight = 0.05f + hsv[1] * hsv[2]
            r += android.graphics.Color.red(px) * weight
            g += android.graphics.Color.green(px) * weight
            b += android.graphics.Color.blue(px) * weight
            total += weight
        }
        if (total == 0f) return Color(0xFF3A3F4B)
        return Color(r / total / 255f, g / total / 255f, b / total / 255f)
    }

    fun launchIntent(app: LauncherApp): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private companion object {
        // Tiles are ~154dp wide at 2x density; sized for the focused (scaled-up) state.
        const val ICON_PX = 128
        const val BANNER_W = 352
        const val BANNER_H = 198
    }
}
