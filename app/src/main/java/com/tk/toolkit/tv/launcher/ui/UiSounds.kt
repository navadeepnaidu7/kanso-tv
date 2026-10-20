package com.tk.toolkit.tv.launcher.ui

import android.os.SystemClock
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView

/**
 * The system's own navigation ticks and select click, as native Android TV screens play them.
 * Compose doesn't play them by itself, so focusable things here call in. View.playSoundEffect
 * honours the TV's "System sounds" setting, so turning that off silences the launcher too.
 */
object UiSounds {

    private var lastKeyCode = 0
    private var lastKeyTime = 0L

    /** Feed every key press, so a focus change can tell which way it moved and whether the user moved it. */
    fun onKey(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastKeyCode = event.keyCode
            lastKeyTime = event.eventTime
        }
    }

    /** The tick for the arrow that just moved focus. Silent when the app moved focus itself. */
    fun focusMoved(view: View) {
        if (SystemClock.uptimeMillis() - lastKeyTime > RECENT_KEY_MILLIS) return
        direction(lastKeyCode)?.let(view::playSoundEffect)
    }

    /** The tick for moving an item by [delta] places in a grid of [columns] (or a single row). */
    fun moved(view: View, delta: Int, columns: Int?) {
        val effect = when {
            columns != null && delta == -columns -> SoundEffectConstants.NAVIGATION_UP
            columns != null && delta == columns -> SoundEffectConstants.NAVIGATION_DOWN
            delta < 0 -> SoundEffectConstants.NAVIGATION_LEFT
            else -> SoundEffectConstants.NAVIGATION_RIGHT
        }
        view.playSoundEffect(effect)
    }

    fun click(view: View) = view.playSoundEffect(SoundEffectConstants.CLICK)

    private fun direction(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> SoundEffectConstants.NAVIGATION_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> SoundEffectConstants.NAVIGATION_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> SoundEffectConstants.NAVIGATION_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> SoundEffectConstants.NAVIGATION_RIGHT
        else -> null
    }

    private const val RECENT_KEY_MILLIS = 300L
}

/** Plays the navigation tick when this element gains focus from an arrow press. */
@Composable
fun Modifier.navigationSound(): Modifier {
    val view = LocalView.current
    return onFocusChanged { if (it.isFocused) UiSounds.focusMoved(view) }
}

/** Records key presses inside a separate window (dialogs and panels), which the activity doesn't see. */
fun Modifier.recordKeysForSounds(): Modifier = onPreviewKeyEvent {
    UiSounds.onKey(it.nativeKeyEvent)
    false
}

/** [action] with the system click before it. */
@Composable
fun withClickSound(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        UiSounds.click(view)
        action()
    }
}
