package com.tk.toolkit.tv.launcher.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private class Armed(var value: Boolean = false)

/**
 * Swallows OK/Enter until a fresh press starts inside this content. A dialog opened by a long
 * press would otherwise receive the still-held key's repeats and release, and click its first item.
 */
@Composable
fun Modifier.ignoreHeldSelectKey(): Modifier {
    val armed = remember { Armed() }
    return onPreviewKeyEvent { event ->
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    armed.value = true
                }
                !armed.value
            }
            else -> false
        }
    }
}

/**
 * Requests focus, retrying for a few frames while the target (e.g. a lazy grid item or dialog
 * content) is still being laid out.
 */
suspend fun FocusRequester.requestFocusWhenReady(maxFrames: Int = 10) {
    repeat(maxFrames) {
        val granted = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)
        if (granted) return
        withFrameNanos { }
    }
}
