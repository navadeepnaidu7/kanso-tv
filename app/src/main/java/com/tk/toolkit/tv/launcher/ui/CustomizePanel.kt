package com.tk.toolkit.tv.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tk.toolkit.tv.launcher.data.SortMode
import com.tk.toolkit.tv.launcher.data.WallpaperSource
import com.tk.toolkit.tv.launcher.weather.Condition
import com.tk.toolkit.tv.launcher.weather.PreviewTime
import com.tk.toolkit.tv.launcher.weather.WeatherPreview

private enum class Page(val title: String) {
    Main("Customize"),
    Wallpaper("Wallpaper"),
    Weather("Weather"),
    Time("Time of Day"),
    Sort("Sort Apps"),
}

/**
 * Home screen settings. A short list of what can be changed, each showing its current value;
 * choosing one opens its options as a page in the same panel, where picks apply at once (so the
 * wallpaper changes behind the barely-dimmed panel). Back steps out of a page, then closes.
 *
 * Weather and Time of Day only appear with the Weather wallpaper: they try it in other weather or
 * at another time of day (Live and Now are the real sky).
 */
@Composable
fun CustomizePanel(
    wallpaper: WallpaperSource,
    sort: SortMode,
    onDismiss: () -> Unit,
    onWallpaper: (WallpaperSource) -> Unit,
    onSort: (SortMode) -> Unit,
) {
    var page by remember { mutableStateOf(Page.Main) }
    // The row to land on when coming back to the main page.
    var cameFrom by remember { mutableStateOf(Page.Wallpaper) }

    SidePanel(
        onDismiss = onDismiss,
        dim = 0.15f,
        onBack = {
            if (page == Page.Main) {
                false
            } else {
                cameFrom = page
                page = Page.Main
                true
            }
        },
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                // Pages come in from the right and go back out to it, like stepping in and out.
                val into = targetState != Page.Main
                (fadeIn(tween(200, delayMillis = 60)) + slideInHorizontally(tween(260)) { w -> if (into) w / 8 else -w / 8 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(260)) { w -> if (into) -w / 8 else w / 8 })
            },
            label = "page",
        ) { shown ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                PanelTitle(shown.title)
                when (shown) {
                    Page.Main -> MainPage(wallpaper, sort, cameFrom) { page = it }
                    Page.Wallpaper -> Options(WallpaperSource.entries, wallpaper, { it.title }, onWallpaper)
                    Page.Weather -> Options(listOf<Condition?>(null) + Condition.entries, WeatherPreview.condition, { it?.label ?: "Live" }) {
                        WeatherPreview.condition = it
                    }
                    Page.Time -> Options(PreviewTime.entries, WeatherPreview.time, { it.label }) { WeatherPreview.time = it }
                    Page.Sort -> Options(SortMode.entries, sort, { it.title }, onSort)
                }
            }
        }
    }
}

@Composable
private fun MainPage(wallpaper: WallpaperSource, sort: SortMode, focusOn: Page, open: (Page) -> Unit) {
    val requesters = remember { Page.entries.associateWith { FocusRequester() } }
    val weather = wallpaper == WallpaperSource.Weather
    LaunchedEffect(Unit) {
        val target = if (!weather && (focusOn == Page.Weather || focusOn == Page.Time)) Page.Wallpaper else focusOn
        requesters.getValue(target).requestFocusWhenReady()
    }
    fun Modifier.focusFor(p: Page) = focusRequester(requesters.getValue(p))

    PanelItem("Wallpaper", { open(Page.Wallpaper) }, Modifier.focusFor(Page.Wallpaper), value = wallpaper.title, opens = true)
    if (weather) {
        PanelItem(
            "Weather",
            { open(Page.Weather) },
            Modifier.focusFor(Page.Weather),
            value = WeatherPreview.condition?.label ?: "Live",
            opens = true,
        )
        PanelItem("Time of Day", { open(Page.Time) }, Modifier.focusFor(Page.Time), value = WeatherPreview.time.label, opens = true)
    }
    PanelGap()
    PanelItem("Sort Apps", { open(Page.Sort) }, Modifier.focusFor(Page.Sort), value = sort.title, opens = true)
}

/** A page of options, the [selected] one checked and focused on arrival. Picks apply immediately. */
@Composable
private fun <T> Options(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocusWhenReady() }
    options.forEach { option ->
        val chosen = option == selected
        PanelItem(
            label(option),
            { onPick(option) },
            if (chosen) Modifier.focusRequester(focus) else Modifier,
            selected = chosen,
        )
    }
}
