package com.glassous.dsha.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** DeepSeek 品牌蓝，界面强调色统一从这里取。 */
internal val BrandBlue = Color(0xFF4D6BFE)
internal val BrandBlueLight = Color(0xFFB4C4FF)
internal val BrandBlueDeep = Color(0xFF0C2B8A)

/** 用于空态图标、FAB 点缀等位置的品牌渐变。 */
internal val BrandGradient = Brush.linearGradient(
    listOf(Color(0xFF4D6BFE), Color(0xFF7C8CFF)),
)

internal val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE3FF),
    onPrimaryContainer = BrandBlueDeep,
    inversePrimary = BrandBlueLight,
    secondary = Color(0xFF5C5F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E1EC),
    onSecondaryContainer = Color(0xFF191A23),
    tertiary = Color(0xFF00696E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF6FF6FF),
    onTertiaryContainer = Color(0xFF002022),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF0F7),
    surfaceContainerHighest = Color(0xFFE9EAF2),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = Color(0xFF001159),
    primaryContainer = Color(0xFF27357F),
    onPrimaryContainer = Color(0xFFDEE3FF),
    inversePrimary = BrandBlue,
    secondary = Color(0xFFC4C5D9),
    onSecondary = Color(0xFF2E303F),
    secondaryContainer = Color(0xFF444655),
    onSecondaryContainer = Color(0xFFE1E1EC),
    tertiary = Color(0xFF4CD9E0),
    onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF004F53),
    onTertiaryContainer = Color(0xFF6FF6FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF181A1F),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3033),
    scrim = Color(0xFF000000),
)
