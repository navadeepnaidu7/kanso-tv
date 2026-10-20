package com.navadeep.kanso.tv.launcher.data

import android.content.pm.ApplicationInfo

/** Kinds of app, in the order the suggested sort lists them. */
enum class AppCategory {
    Watch,
    Music,
    Games,
    Other,
    Utilities,
    System,
}

/** How All Apps is ordered. */
enum class SortMode(val title: String, val description: String) {
    Suggested("Suggested", "Streaming and media first, then apps, utilities and system"),
    Name("Name", "A to Z"),
    Recent("Recently installed", "Newest first"),
    Custom("Custom", "Your own order. Move apps from their menu"),
}

/**
 * Best guess at an app's kind. Few TV apps declare a category, so known packages and package-name
 * hints come first.
 */
object AppCategorizer {

    private val Known = buildMap {
        listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar", "com.disney.disneyplus", "com.crunchyroll.crunchyroid", "com.sonyliv",
            "com.graymatrix.did", "com.jio.media.jiotvplus", "com.jio.jioplay.tv", "com.stremio.one",
            "com.nuvio.tv", "org.videolan.vlc", "com.google.android.videos", "com.android.tv",
            "com.sonypicturescore", "com.wbd.stream",
            "com.plexapp.android", "org.jellyfin.androidtv", "org.xbmc.kodi", "com.mxtech.videoplayer.ad",
            "com.realtek.tvmediaplay",
        ).forEach { put(it, AppCategory.Watch) }
        listOf(
            "com.google.android.youtube.tvmusic", "com.spotify.tv.android", "com.jio.media.jiobeats",
            "com.amazon.music.tv", "com.gaana",
        ).forEach { put(it, AppCategory.Music) }
        listOf("com.google.android.play.games").forEach { put(it, AppCategory.Games) }
        listOf(
            "com.android.vending", "com.tcl.browser", "flar2.homebutton", "com.cxinventor.file.explorer",
            "com.tailscale.ipn", "org.localsend.localsend_app",
        ).forEach { put(it, AppCategory.Utilities) }
        listOf(
            "com.android.tv.settings", "com.android.camera2", "com.android.gallery3d",
        ).forEach { put(it, AppCategory.System) }
    }

    /** Vendor packages that are TV plumbing (timers, eco settings, help, promos). */
    private val SystemPrefixes = listOf("com.sony.dtv.", "com.sony.qrmt", "com.mediatek.", "com.realtek.")

    private val Hints = listOf(
        AppCategory.Watch to listOf("video", "movie", "cinema", "stream", "anime", "flix", "iptv", "player"),
        AppCategory.Music to listOf("music", "radio", "podcast", "audio"),
        AppCategory.Games to listOf("game"),
        AppCategory.Utilities to listOf(
            "file", "browser", "vpn", "explorer", "manager", "cleaner", "remote", "keyboard", "speedtest",
            "downloader", "send", "share", "tools", "util",
        ),
    )

    @Suppress("DEPRECATION") // FLAG_IS_GAME still marks older games that predate CATEGORY_GAME.
    fun categorize(info: ApplicationInfo): AppCategory {
        val pkg = info.packageName
        Known[pkg]?.let { return it }
        when (info.category) {
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.Watch
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.Music
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.Games
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.Utilities
        }
        if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) return AppCategory.Games
        if (SystemPrefixes.any { pkg.startsWith(it) }) return AppCategory.System
        val lower = pkg.lowercase()
        Hints.firstOrNull { (_, words) -> words.any { it in lower } }?.let { return it.first }
        val preinstalled = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
        return if (preinstalled) AppCategory.System else AppCategory.Other
    }
}
