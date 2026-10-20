package com.tk.toolkit.tv.core

import androidx.compose.runtime.Composable
import com.tk.toolkit.tv.launcher.ui.HomeScreen
import com.tk.toolkit.tv.ui.theme.ToolkitTheme

/**
 * App shell. The launcher home is the root screen; future tools get their own
 * screens here and are reached from the home header.
 */
@Composable
fun ToolkitApp() {
    ToolkitTheme {
        // No background here: the home screen's backdrop covers the whole screen.
        HomeScreen()
    }
}
