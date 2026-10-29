package com.nexus.gamebooster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.nexus.gamebooster.data.ThemeMode

/**
 * NexusGameBooster is dark-first: only the two dark variants below exist.
 *  - DARK_NAVY: the Phase 8 neon-showcase palette — deep indigo-navy surfaces,
 *    neon-green primary, violet secondary, cyan tertiary.
 *  - AMOLED_BLACK: pure #000000 background, same neon accents.
 * Dynamic color (Material You) is intentionally disabled to keep the brand look
 * consistent. There is no light theme. The chosen [ThemeMode] is persisted in
 * DataStore and applied by NgbApp. Shapes ([NgbShapes]) and type ([NgbTypography])
 * are shared by both dark variants.
 */
private val NgbColorScheme = darkColorScheme(
    primary = NgbPrimary,
    onPrimary = NgbOnPrimary,
    primaryContainer = NgbPrimaryContainer,
    onPrimaryContainer = NgbOnPrimaryContainer,
    secondary = NgbSecondary,
    onSecondary = NgbOnSecondary,
    secondaryContainer = NgbSecondaryContainer,
    onSecondaryContainer = NgbOnSecondaryContainer,
    tertiary = NgbTertiary,
    onTertiary = NgbOnTertiary,
    tertiaryContainer = NgbTertiaryContainer,
    onTertiaryContainer = NgbOnTertiaryContainer,
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
    primaryContainer = NgbPrimaryContainer,
    onPrimaryContainer = NgbOnPrimaryContainer,
    secondary = NgbSecondary,
    onSecondary = NgbOnSecondary,
    secondaryContainer = NgbSecondaryContainer,
    onSecondaryContainer = NgbOnSecondaryContainer,
    tertiary = NgbTertiary,
    onTertiary = NgbOnTertiary,
    tertiaryContainer = NgbTertiaryContainer,
    onTertiaryContainer = NgbOnTertiaryContainer,
    error = NgbError,
    background = AmoledBackground,
    onBackground = NgbOnBackground,
    surface = AmoledSurface,
    onSurface = NgbOnBackground,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = NgbTextMuted,
    outline = AmoledOutline,
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
        // v1.2.2: the arcade type kit (from the redesign reference) — Vazirmatn as
        // the default family on every Material style (NgbFonts.kt); HUD/tag fonts
        // are read directly by components.
        typography = ngbTypographyWithFonts(NgbTypography),
        shapes = NgbShapes,
        content = content,
    )
}
