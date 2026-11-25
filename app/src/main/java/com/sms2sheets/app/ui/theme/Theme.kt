package com.sms2sheets.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StatusColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val successAccent: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val errorAccent: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

private val LightStatusColors = StatusColors(
    successContainer = Color(0xFFD2F5DD),
    onSuccessContainer = Color(0xFF073B1D),
    successAccent = Color(0xFF0F7B43),
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF690005),
    errorAccent = Color(0xFFB3261E),
    infoContainer = Color(0xFF1E293B),
    onInfoContainer = Color(0xFFF8FAFC)
)

private val DarkStatusColors = StatusColors(
    successContainer = Color(0xFF0E4427),
    onSuccessContainer = Color(0xFFD6FBE2),
    successAccent = Color(0xFF6EE7A0),
    errorContainer = Color(0xFF5C1517),
    onErrorContainer = Color(0xFFFFDAD6),
    errorAccent = Color(0xFFFF8A80),
    infoContainer = Color(0xFFE2E8F0),
    onInfoContainer = Color(0xFF0F172A)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7B43),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F3D6),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF2D5B70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEAF8),
    onSecondaryContainer = Color(0xFF072533),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF121714),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121714),
    surfaceVariant = Color(0xFFE7EDE8),
    onSurfaceVariant = Color(0xFF424B44),
    surfaceContainerHighest = Color(0xFFE3EAE4),
    outline = Color(0xFF707B72),
    outlineVariant = Color(0xFFC2CBC3),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF690005),
    inverseSurface = Color(0xFF1E2420),
    inverseOnSurface = Color(0xFFF5F7F5),
    inversePrimary = Color(0xFF7AD89C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EE7A0),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF0D512C),
    onPrimaryContainer = Color(0xFFD6FBE2),
    secondary = Color(0xFF9ACCE2),
    onSecondary = Color(0xFF063447),
    secondaryContainer = Color(0xFF1F4B5E),
    onSecondaryContainer = Color(0xFFD6F0FC),
    background = Color(0xFF101412),
    onBackground = Color(0xFFEAF0EC),
    surface = Color(0xFF161B18),
    onSurface = Color(0xFFEAF0EC),
    surfaceVariant = Color(0xFF232A26),
    onSurfaceVariant = Color(0xFFB9C4BC),
    surfaceContainerHighest = Color(0xFF2B332E),
    outline = Color(0xFF839087),
    outlineVariant = Color(0xFF3A443D),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5A0B0D),
    errorContainer = Color(0xFF5C1517),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6ECE8),
    inverseOnSurface = Color(0xFF141916),
    inversePrimary = Color(0xFF0F7B43)
)

@Composable
fun SmsToSheetsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
