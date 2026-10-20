package com.navadeep.kanso.tv.launcher.data

import android.content.Context
import androidx.core.content.edit

/** Persists the user's launcher customizations: favorites and their order, hidden apps, and how All Apps is sorted. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Favorite packages in display order, or null until favorites have been set up the first time. */
    fun favorites(): List<String>? =
        prefs.getString(KEY_FAVORITES, null)?.split('\n')?.filter { it.isNotBlank() }

    fun saveFavorites(packages: List<String>) {
        prefs.edit { putString(KEY_FAVORITES, packages.joinToString("\n")) }
    }

    fun hiddenPackages(): Set<String> = prefs.getStringSet(KEY_HIDDEN, null).orEmpty()

    fun setHidden(packageName: String, hidden: Boolean) {
        val updated = hiddenPackages().toMutableSet().apply { if (hidden) add(packageName) else remove(packageName) }
        prefs.edit { putStringSet(KEY_HIDDEN, updated) }
    }

    fun sortMode(): SortMode =
        SortMode.entries.find { it.name == prefs.getString(KEY_SORT, null) } ?: SortMode.Suggested

    fun setSortMode(mode: SortMode) {
        prefs.edit { putString(KEY_SORT, mode.name) }
    }

    fun wallpaperSource(): WallpaperSource =
        WallpaperSource.entries.find { it.name == prefs.getString(KEY_WALLPAPER, null) } ?: WallpaperSource.Art

    fun setWallpaperSource(source: WallpaperSource) {
        prefs.edit { putString(KEY_WALLPAPER, source.name) }
    }

    /** All Apps in the user's own order, by package. */
    fun customOrder(): List<String> =
        prefs.getString(KEY_CUSTOM_ORDER, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun saveCustomOrder(packages: List<String>) {
        prefs.edit { putString(KEY_CUSTOM_ORDER, packages.joinToString("\n")) }
    }

    private companion object {
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_CUSTOM_ORDER = "custom_order"
        const val KEY_SORT = "sort"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
    }
}
