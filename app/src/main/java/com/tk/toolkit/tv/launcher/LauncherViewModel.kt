package com.tk.toolkit.tv.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tk.toolkit.tv.launcher.data.AppRepository
import com.tk.toolkit.tv.launcher.data.LauncherApp
import com.tk.toolkit.tv.launcher.data.LauncherPrefs
import com.tk.toolkit.tv.launcher.data.SortMode
import com.tk.toolkit.tv.launcher.data.WallpaperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LauncherState(
    /** The dock, in the user's order. */
    val favorites: List<LauncherApp> = emptyList(),
    /** Every visible app, favorites included, in [sortMode] order. */
    val apps: List<LauncherApp> = emptyList(),
    val sortMode: SortMode = SortMode.Suggested,
    val wallpaperSource: WallpaperSource = WallpaperSource.Art,
    /** Apps the user hid, shown under "Hidden apps", alphabetically. */
    val hiddenApps: List<LauncherApp> = emptyList(),
    val loaded: Boolean = false,
    /** App being repositioned, if any: in the dock, or in All Apps when [movingInGrid]. */
    val movingKey: String? = null,
    val movingInGrid: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    private val prefs = LauncherPrefs(application)

    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    /** Every launchable app from the last refresh. */
    private var allApps: List<LauncherApp> = emptyList()

    private var refreshJob: Job? = null
    private val refreshDispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val all = withContext(refreshDispatcher) { repository.loadApps() }
            allApps = all
            _state.update { current ->
                // Don't reshuffle under the user while they're moving something.
                if (current.movingKey != null) current else build(all).copy(loaded = true)
            }
        }
    }

    private fun rebuild() = _state.update { current ->
        build(allApps).copy(loaded = current.loaded, movingKey = current.movingKey, movingInGrid = current.movingInGrid)
    }

    private fun build(all: List<LauncherApp>): LauncherState {
        val hidden = prefs.hiddenPackages()
        val byPackage = all.associateBy { it.packageName }
        val (hiddenApps, visible) = all.partition { it.packageName in hidden }
        val sortMode = prefs.sortMode()
        return LauncherState(
            favorites = favoritePackages(all).mapNotNull { byPackage[it] }.filter { it.packageName !in hidden },
            apps = visible.sortedWith(sortMode.comparator()),
            sortMode = sortMode,
            wallpaperSource = prefs.wallpaperSource(),
            hiddenApps = hiddenApps.sortedBy { it.label.lowercase() },
        )
    }

    /** Saved favorites; the first time, popular installed streaming apps. */
    private fun favoritePackages(apps: List<LauncherApp>): List<String> {
        prefs.favorites()?.let { return it }
        if (apps.isEmpty()) return emptyList()
        val installed = apps.mapTo(HashSet()) { it.packageName }
        val seed = DefaultFavorites.filter { it in installed }.take(DEFAULT_FAVORITE_COUNT)
        prefs.saveFavorites(seed)
        return seed
    }

    fun launchIntent(app: LauncherApp) = repository.launchIntent(app)

    fun setFavorite(packageName: String, favorite: Boolean) {
        val current = prefs.favorites().orEmpty() - packageName
        prefs.saveFavorites(if (favorite) current + packageName else current)
        rebuild()
    }

    fun setSortMode(mode: SortMode) {
        prefs.setSortMode(mode)
        rebuild()
    }

    fun setWallpaperSource(source: WallpaperSource) {
        prefs.setWallpaperSource(source)
        _state.update { it.copy(wallpaperSource = source) }
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        prefs.setHidden(packageName, hidden)
        rebuild()
    }

    /** Starts moving an app within the dock, or within All Apps when [inGrid]. */
    fun startMove(packageName: String, inGrid: Boolean) =
        _state.update { it.copy(movingKey = packageName, movingInGrid = inGrid) }

    /** Shifts the moving app by [delta] positions, clamped to its section. */
    fun moveBy(delta: Int) = _state.update { current ->
        if (current.movingInGrid) {
            current.copy(apps = current.apps.shifted(current.movingKey, delta))
        } else {
            current.copy(favorites = current.favorites.shifted(current.movingKey, delta))
        }
    }

    private fun List<LauncherApp>.shifted(packageName: String?, delta: Int): List<LauncherApp> {
        val from = indexOfFirst { it.packageName == packageName }
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, lastIndex)
        return if (to == from) this else toMutableList().apply { add(to, removeAt(from)) }
    }

    fun endMove() {
        val current = _state.value
        _state.update { it.copy(movingKey = null, movingInGrid = false) }
        if (current.movingInGrid) {
            // Whatever order it was in, a hand-placed app makes it the user's own.
            val shown = current.apps.map { it.packageName }
            prefs.saveCustomOrder(shown + prefs.customOrder().filter { it !in shown })
            prefs.setSortMode(SortMode.Custom)
            _state.update { it.copy(sortMode = SortMode.Custom) }
        } else {
            val shown = current.favorites.map { it.packageName }
            // Favorites that aren't shown right now (hidden or uninstalled) keep their place after the rest.
            prefs.saveFavorites(shown + prefs.favorites().orEmpty().filter { it !in shown })
        }
    }

    private fun SortMode.comparator(): Comparator<LauncherApp> {
        val byName = compareBy<LauncherApp> { it.label.lowercase() }
        return when (this) {
            SortMode.Suggested -> compareBy<LauncherApp> { it.category.ordinal }.then(byName)
            SortMode.Name -> byName
            SortMode.Recent -> compareByDescending<LauncherApp> { it.installTime }.then(byName)
            SortMode.Custom -> {
                // Saved order first; apps installed since go at the end, in suggested order.
                val rank = prefs.customOrder().withIndex().associate { (i, pkg) -> pkg to i }
                compareBy<LauncherApp> { rank[it.packageName] ?: Int.MAX_VALUE }
                    .then(SortMode.Suggested.comparator())
            }
        }
    }

    private companion object {
        const val DEFAULT_FAVORITE_COUNT = 5

        /** Installed ones become the first favorites, in this order, until the user picks their own. */
        val DefaultFavorites = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.disney.disneyplus",
            "com.crunchyroll.crunchyroid",
            "com.spotify.tv.android",
            "com.google.android.youtube.tvmusic",
        )
    }
}
