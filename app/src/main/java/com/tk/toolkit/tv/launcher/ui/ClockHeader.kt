package com.tk.toolkit.tv.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tk.toolkit.tv.ui.theme.ToolkitColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class ClockText(val time: String, val meridiem: String?, val date: String)

/** The time, large and quiet, with the date as one line under it; customize, audio and settings on the right. */
@Composable
fun ClockHeader(
    onOpenCustomize: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** The wallpaper's title and credit, shown under the date while [captionAlpha] > 0. */
    caption: String? = null,
    captionAlpha: () -> Float = { 0f },
) {
    val context = LocalContext.current
    val clock by produceState(clockText(DateFormat.is24HourFormat(context))) {
        while (true) {
            value = clockText(DateFormat.is24HourFormat(context))
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    clock.time,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    color = ToolkitColors.OnSurface,
                )
                clock.meridiem?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        color = ToolkitColors.OnSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 6.dp, bottom = 9.dp),
                    )
                }
            }
            Text(
                clock.date,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                color = ToolkitColors.OnSurface.copy(alpha = 0.7f),
            )
            caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ToolkitColors.OnSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .graphicsLayer { alpha = captionAlpha() },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        HeaderButton(Icons.Outlined.Tune, "Customize", onOpenCustomize)
        Spacer(Modifier.width(12.dp))
        HeaderButton(Icons.Outlined.Speaker, "Audio output", onOpenAudioOutput)
        Spacer(Modifier.width(12.dp))
        HeaderButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = withClickSound(onClick),
        modifier = Modifier
            .navigationSound()
            .size(44.dp),
        colors = IconButtonDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.18f),
            contentColor = ToolkitColors.OnSurface.copy(alpha = 0.8f),
            focusedContainerColor = ToolkitColors.OnSurface,
            focusedContentColor = ToolkitColors.Background,
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

private fun clockText(is24Hour: Boolean): ClockText {
    val now = LocalDateTime.now()
    return ClockText(
        time = now.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm")),
        meridiem = if (is24Hour) null else now.format(DateTimeFormatter.ofPattern("a")).uppercase(),
        date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
    )
}
