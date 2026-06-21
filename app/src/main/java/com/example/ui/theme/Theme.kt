package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ObsidianColorScheme = darkColorScheme(
    primary = BrandRedOrange,
    secondary = NeonViolet,
    tertiary = AcousticTeal,
    background = ObsidianDark,
    surface = SurfaceCarbon,
    onBackground = OnBackgroundWhite,
    onSurface = OnBackgroundWhite,
    outline = DarkGreyOutline
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = ObsidianColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            // Set light status bar icons for deep dark background
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
