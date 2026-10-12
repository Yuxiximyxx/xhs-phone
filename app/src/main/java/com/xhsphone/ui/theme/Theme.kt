package com.xhsphone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF2442),
    onPrimary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF2442),
    onPrimary = Color.White,
)

@Composable
fun XhsPhoneTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
