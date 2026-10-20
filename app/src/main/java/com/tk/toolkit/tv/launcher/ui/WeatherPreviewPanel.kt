package com.tk.toolkit.tv.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tk.toolkit.tv.launcher.weather.Condition
import com.tk.toolkit.tv.launcher.weather.PreviewTime
import com.tk.toolkit.tv.launcher.weather.WeatherPreview

/**
 * Shows the weather wallpaper in any weather and at any time of day. Picks apply at once, to the
 * sky beside the panel; Live goes back to the real weather.
 */
@Composable
fun WeatherPreviewPanel(onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }

    // Barely dimmed: the sky behind is what's being previewed.
    SidePanel(onDismiss = onDismiss, dim = 0.08f) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            PanelTitle("Preview weather", "Changes show right away. Live is the real sky.")
            PanelSection("Weather")
            PanelItem(
                "Live",
                { WeatherPreview.condition = null },
                if (WeatherPreview.condition == null) Modifier.focusRequester(first) else Modifier,
                icon = Icons.Outlined.Sensors,
                subtitle = "The weather outside right now",
                selected = WeatherPreview.condition == null,
            )
            Condition.entries.forEach { condition ->
                val current = WeatherPreview.condition == condition
                PanelItem(
                    condition.label,
                    { WeatherPreview.condition = condition },
                    if (current) Modifier.focusRequester(first) else Modifier,
                    icon = condition.icon(),
                    selected = current,
                )
            }
            PanelSection("Time of day")
            PreviewTime.entries.forEach { time ->
                PanelItem(
                    if (time == PreviewTime.Now) "Now" else time.label,
                    { WeatherPreview.time = time },
                    icon = time.icon(),
                    selected = WeatherPreview.time == time,
                )
            }
        }
    }
}

private fun Condition.icon() = when (this) {
    Condition.Clear -> Icons.Outlined.WbSunny
    Condition.MostlyClear -> Icons.Outlined.LightMode
    Condition.PartlyCloudy -> Icons.Outlined.FilterDrama
    Condition.MostlyCloudy -> Icons.Outlined.CloudQueue
    Condition.Overcast -> Icons.Outlined.Cloud
    Condition.Fog -> Icons.Outlined.Dehaze
    Condition.Drizzle -> Icons.Outlined.WaterDrop
    Condition.Rain -> Icons.Outlined.Umbrella
    Condition.HeavyRain -> Icons.Outlined.Umbrella
    Condition.Snow -> Icons.Outlined.AcUnit
    Condition.Thunderstorm -> Icons.Outlined.Thunderstorm
}

private fun PreviewTime.icon() = when (this) {
    PreviewTime.Now -> Icons.Outlined.Schedule
    PreviewTime.Dawn, PreviewTime.Sunset -> Icons.Outlined.WbTwilight
    PreviewTime.Morning, PreviewTime.Midday, PreviewTime.Afternoon -> Icons.Outlined.WbSunny
    PreviewTime.Dusk -> Icons.Outlined.DarkMode
    PreviewTime.Night -> Icons.Outlined.Bedtime
}
