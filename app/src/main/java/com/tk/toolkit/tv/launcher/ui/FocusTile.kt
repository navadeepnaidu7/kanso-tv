package com.tk.toolkit.tv.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tk.toolkit.tv.ui.theme.Dimens
import com.tk.toolkit.tv.ui.theme.Motion
import com.tk.toolkit.tv.ui.theme.ToolkitColors
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.delay

internal val TileShape = RoundedCornerShape(Dimens.TileCorner)

/**
 * A 16:9 tile that lifts on focus: it grows on a spring, casts a soft shadow, picks up a faint
 * sheen, and shows [label] underneath. All of that animates through layer properties, so focus
 * changes never recompose or redraw the tile's content.
 */
@Composable
internal fun FocusTile(
    label: String,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isMoving: Boolean = false,
    dimmed: Boolean = false,
    tileModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift = animateFloatAsState(if (focused) 1f else 0f, Motion.focus, label = "lift")
    // The outline breathes, slowly, once focus has settled on the tile. Updated every fourth frame
    // (about 12 times a second): plenty for something this slow, and every redraw is costly on a TV,
    // whose compositor scales the whole screen up to the panel each time.
    val breath = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(focused && !isMoving) {
        breath.floatValue = 1f
        if (!focused || isMoving) return@LaunchedEffect
        delay(Motion.BreathDelayMillis)
        val start = withFrameNanos { it }
        while (true) {
            repeat(3) { withFrameNanos { } }
            withFrameNanos { now ->
                val t = (now - start) / 1e9f
                // Starts at full brightness, so it eases in without a jump.
                breath.floatValue = 1f - Motion.BreathDepth * (1f - cos(2f * PI.toFloat() * t / Motion.BreathSeconds)) / 2f
            }
        }
    }
    val press = animateFloatAsState(if (pressed && focused) 1f else 0f, Motion.press, label = "press")
    val dim = animateFloatAsState(if (dimmed) 0.3f else 1f, Motion.fade, label = "dim")
    val targetScale = if (isMoving) Motion.MovingScale else Motion.FocusScale
    val view = LocalView.current
    // Read only in layer blocks, so it isn't state.
    val tileHeight = remember { FloatArray(1) }

    Column(
        modifier.graphicsLayer { alpha = dim.value },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            onClick = {
                UiSounds.click(view)
                onClick()
            },
            onLongClick = onLongClick?.let { longClick ->
                {
                    UiSounds.click(view)
                    longClick()
                }
            },
            interactionSource = interaction,
            modifier = tileModifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onSizeChanged { tileHeight[0] = it.height.toFloat() }
                .graphicsLayer {
                    val l = lift.value
                    val scale = (1f + (targetScale - 1f) * l) * (1f - (1f - Motion.PressScale) * press.value)
                    scaleX = scale
                    scaleY = scale
                    shape = TileShape
                    shadowElevation = Dimens.FocusElevation.toPx() * l.coerceIn(0f, 1f)
                }
                // Outside the card's own clip, so the edge line isn't cut in half by it.
                .focusEdge(alpha = { lift.value.coerceIn(0f, 1f) * breath.floatValue }, moving = isMoving)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) {
                        UiSounds.focusMoved(view)
                        onFocused()
                    }
                },
            shape = CardDefaults.shape(TileShape),
            // Scale is ours (spring-driven, above), so the card's own tween is off.
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
            border = CardDefaults.border(focusedBorder = Border.None),
            colors = CardDefaults.colors(
                containerColor = ToolkitColors.Surface,
                focusedContainerColor = ToolkitColors.Surface,
                pressedContainerColor = ToolkitColors.Surface,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = lift.value.coerceIn(0f, 1f) }
                        .sheen(),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = ToolkitColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.LabelHeight)
                .padding(top = 10.dp)
                .graphicsLayer {
                    val l = lift.value.coerceIn(0f, 1f)
                    alpha = l
                    // Rides just under the grown tile's bottom edge.
                    translationY = tileHeight[0] * (targetScale - 1f) / 2f * l
                },
        )
    }
}

private val EdgeWidth = 2.dp

/**
 * The focused tile's outline: a fine line on its very edge, bright where light would catch it at
 * the top and softer toward the bottom, so it reads as part of the tile rather than a frame around
 * it. In the accent color while the tile is being moved. Drawn in the tile's layer, so it grows
 * with the tile, and fading it only redraws the tile.
 */
private fun Modifier.focusEdge(alpha: () -> Float, moving: Boolean) = drawWithCache {
    val brush = if (moving) SolidColor(ToolkitColors.Accent)
    else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.45f)))
    val stroke = Stroke(width = EdgeWidth.toPx())
    val radius = CornerRadius(Dimens.TileCorner.toPx())
    onDrawWithContent {
        drawContent()
        val a = alpha()
        if (a > 0f) drawRoundRect(brush, cornerRadius = radius, style = stroke, alpha = a)
    }
}

/** Faint light from the top, so the focused tile reads as raised. */
private fun Modifier.sheen() = drawWithCache {
    val gloss = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.14f),
        0.45f to Color.White.copy(alpha = 0.03f),
        0.6f to Color.Transparent,
        start = Offset(size.width * 0.2f, 0f),
        end = Offset(size.width * 0.55f, size.height),
    )
    val radius = CornerRadius(Dimens.TileCorner.toPx())
    onDrawWithContent {
        drawRoundRect(gloss, cornerRadius = radius)
    }
}
