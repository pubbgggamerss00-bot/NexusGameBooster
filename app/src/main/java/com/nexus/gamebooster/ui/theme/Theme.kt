package com.nexus.gamebooster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.nexus.gamebooster.data.ThemeMode

/**
 * NexusGameBooster is dark-first: only the two dark variants below exist.
 *  - DARK_NAVY: the original neon-on-dark gaming palette (#070B14 surfaces).
 *  - AMOLED_BLACK: pure #000000 background, same neon-green #00FF9D primary.
 * Dynamic color (Material You) is intentionally disabled to keep the brand look
 * consistent. There is no light theme. The chosen [ThemeMode] is persisted in
 * DataStore and applied by NgbApp. Shapes ([NgbShapes]) and type ([NgbTypography])
 * are shared by both dark variants.
 */
private val NgbColorScheme = darkColorScheme(
    primary = NgbPrimary,
    onPrimary = NgbOnPrimary,
    secondary = NgbSecondary,
    error = NgbError,
    background = NgbBackground,
    onBackground = NgbOnBackground,
    surface = NgbSurface,
    onSurface = NgbOnBackground,
    surfaceVariant = NgbSurfaceVariant,
    onSurfaceVariant = NgbTextMuted,
    outline = NgbOutline,
    surfaceContainerLowest = NgbSurfaceContainerLowest,
    surfaceContainerLow = NgbSurfaceContainerLow,
    surfaceContainer = NgbSurfaceContainer,
    surfaceContainerHigh = NgbSurfaceContainerHigh,
    surfaceContainerHighest = NgbSurfaceContainerHighest,
    surfaceDim = NgbSurfaceDim,
    surfaceBright = NgbSurfaceBright,
    inverseSurface = NgbInverseSurface,
    inverseOnSurface = NgbInverseOnSurface,
)

private val AmoledColorScheme = darkColorScheme(
    primary = NgbPrimary,
    onPrimary = NgbOnPrimary,
    secondary = NgbSecondary,
    error = NgbError,
    background = AmoledBackground,
    onBackground = NgbOnBackground,
    surface = AmoledSurface,
    onSurface = NgbOnBackground,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = NgbTextMuted,
    outline = NgbOutline,
    surfaceContainerLowest = AmoledSurfaceContainerLowest,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest,
    surfaceDim = AmoledSurfaceDim,
    surfaceBright = AmoledSurfaceBright,
    inverseSurface = AmoledInverseSurface,
    inverseOnSurface = AmoledInverseOnSurface,
)

@Composable
fun NexusBoosterTheme(
    themeMode: ThemeMode = ThemeMode.DARK_NAVY,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = when (themeMode) {
            ThemeMode.DARK_NAVY -> NgbColorScheme
            ThemeMode.AMOLED_BLACK -> AmoledColorScheme
        },
        typography = NgbTypography,
        shapes = NgbShapes,
        content = content,
    )
}
