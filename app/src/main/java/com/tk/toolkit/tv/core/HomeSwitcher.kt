package com.tk.toolkit.tv.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Which home screen the Home button opens: Toolkit, or the TV's own (Google TV or Android TV).
 *
 * Toolkit takes Home over through its accessibility service, so switching is turning that service
 * on or off. Doing it from the app needs WRITE_SECURE_SETTINGS, granted once over adb:
 *   adb shell pm grant com.tk.toolkit.tv android.permission.WRITE_SECURE_SETTINGS
 * Without it, switching opens Accessibility settings to do it by hand. Other accessibility
 * services the TV has on are left alone.
 */
object HomeSwitcher {

    /** The TV's own home screens, and what to call them. */
    private val StockHomes = linkedMapOf(
        "com.google.android.apps.tv.launcherx" to "Google TV",
        "com.google.android.tvlauncher" to "Android TV",
        "com.google.android.leanbacklauncher" to "Android TV",
    )

    /** The TV's own home screen's package, if it has one we know. */
    fun stockPackage(context: Context): String? {
        val homes = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        ).map { it.activityInfo.packageName }
        return StockHomes.keys.firstOrNull { it in homes }
    }

    /** "Google TV" or "Android TV". */
    fun stockName(context: Context): String = StockHomes[stockPackage(context)] ?: "TV Home"

    fun isStock(packageName: CharSequence?): Boolean = packageName?.toString() in StockHomes.keys

    /** Whether Home opens Toolkit right now. */
    fun isToolkitHome(context: Context): Boolean =
        enabledServices(context).any { ComponentName.unflattenFromString(it) == service(context) }

    /** Makes Home open Toolkit. False if it had to send the person to settings instead. */
    fun useToolkit(context: Context): Boolean {
        if (!canSwitch(context)) return sendToSettings(context, "Turn on Toolkit here, so Home opens it")
        val services = enabledServices(context)
        if (services.none { ComponentName.unflattenFromString(it) == service(context) }) {
            write(context, services + service(context).flattenToString())
        }
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        return true
    }

    /** Hands Home back to the TV's own home screen, and goes there. */
    fun useStock(context: Context): Boolean {
        if (!canSwitch(context)) return sendToSettings(context, "Turn off Toolkit here, so Home opens ${stockName(context)}")
        write(context, enabledServices(context).filter { ComponentName.unflattenFromString(it) != service(context) })
        val stock = stockPackage(context) ?: return true
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(stock)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }

    private fun service(context: Context) = ComponentName(context, ToolkitAccessibilityService::class.java)

    private fun canSwitch(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun enabledServices(context: Context): List<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.filter { it.isNotBlank() }.orEmpty()

    private fun write(context: Context, services: List<String>) {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":"))
    }

    private fun sendToSettings(context: Context, message: String): Boolean {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return false
    }
}
