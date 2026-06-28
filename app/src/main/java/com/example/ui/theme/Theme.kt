package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberBlue,
    secondary = CyberGreen,
    tertiary = SunsetOrange,
    background = CosmicBlack,
    surface = CosmicSlate,
    onPrimary = CosmicBlack,
    onSecondary = CosmicBlack,
    onBackground = PureWhite,
    onSurface = PureWhite,
    error = GlowRed
)

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    secondary = DarkNavy,
    tertiary = SunsetOrange,
    background = SoftIce,
    surface = PureWhite,
    onPrimary = PureWhite,
    onSecondary = PureWhite,
    onBackground = DeepNavy,
    onSurface = DeepNavy,
    error = GlowRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to gorgeous dark mode for monitoring dashboard
    dynamicColor: Boolean = false, // Keep our beautiful custom palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
