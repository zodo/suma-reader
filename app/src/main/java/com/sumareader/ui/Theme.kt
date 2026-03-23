package com.sumareader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TransitBlue = Color(0xFF1565C0)
private val TransitLightBlue = Color(0xFF59CFF6)

private val LightColors = lightColorScheme(
    primary = TransitBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF545F71),
    secondaryContainer = Color(0xFFD8E3F8),
    surface = Color(0xFFFAFAFA),
)

private val DarkColors = darkColorScheme(
    primary = TransitLightBlue,
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF004A80),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DB),
    secondaryContainer = Color(0xFF3C4758),
)

@Composable
fun SumaReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
