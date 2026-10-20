package com.navadeep.kanso.tv.core

import androidx.compose.runtime.Composable
import com.navadeep.kanso.tv.launcher.ui.HomeScreen
import com.navadeep.kanso.tv.ui.theme.KansoTheme

/**
 * App shell. The launcher home is the root screen; future tools get their own
 * screens here and are reached from the home header.
 */
@Composable
fun KansoApp() {
    KansoTheme {
        // No background here: the home screen's backdrop covers the whole screen.
        HomeScreen()
    }
}
