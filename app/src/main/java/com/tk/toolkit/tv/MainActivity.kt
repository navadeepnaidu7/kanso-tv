package com.tk.toolkit.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.tk.toolkit.tv.core.ToolkitApp
import com.tk.toolkit.tv.launcher.LauncherViewModel
import com.tk.toolkit.tv.launcher.ui.UiSounds

class MainActivity : ComponentActivity() {

    private val launcherViewModel: LauncherViewModel by viewModels()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = launcherViewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent { ToolkitApp() }
        // The backdrop covers the whole window; drawing the window background under it is wasted GPU time.
        window.setBackgroundDrawable(null)
    }

    // Remembered so focus changes can play the matching navigation tick (see UiSounds).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        UiSounds.onKey(event)
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        launcherViewModel.refresh()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
