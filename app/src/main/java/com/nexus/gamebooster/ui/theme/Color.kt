package com.nexus.gamebooster.ui.theme

import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// Neon gaming palette (Phase 8 restyle — "neon showcase" look, mirrors the web
// prototype): deep indigo-navy surfaces on an almost-black navy background,
// neon-green primary, neon-violet secondary and neon-cyan tertiary accents.
// DARK_NAVY theme mode. All roles below are the SINGLE source of color truth —
// screens never hardcode colors, they read MaterialTheme.colorScheme.
// -----------------------------------------------------------------------------
val NgbBackground = Color(0xFF070C22)
val NgbSurface = Color(0xFF0C1330)
val NgbSurfaceVariant = Color(0xFF121B40)
val NgbPrimary = Color(0xFF2CE880)
val NgbOnPrimary = Color(0xFF05130B)
val NgbSecondary = Color(0xFF9D6BFF)
val NgbTertiary = Color(0xFF3AC2F8)
val NgbError = Color(0xFFFF5C7A)
val NgbOnBackground = Color(0xFFE8ECFF)
val NgbOutline = Color(0xFF2A3A78)
val NgbTextMuted = Color(0xFF9AA6CC)

// Harmonized Material 3 "surface container" roles so default Card/Dialog/Snackbar
// containers stay in-palette instead of falling back to the grey baseline theme.
val NgbSurfaceContainerLowest = Color(0xFF0A0F28)
val NgbSurfaceContainerLow = Color(0xFF0C1330)
val NgbSurfaceContainer = Color(0xFF101838)
val NgbSurfaceContainerHigh = Color(0xFF131C44)
val NgbSurfaceContainerHighest = Color(0xFF172250)
val NgbSurfaceDim = Color(0xFF070C22)
val NgbSurfaceBright = Color(0xFF1A2650)
val NgbInverseSurface = Color(0xFF172250)
val NgbInverseOnSurface = Color(0xFFE8ECFF)

// Phase 8 container roles: the neon-tinted containers drive the selected-profile
// card (primaryContainer) and the active bottom-navigation pill (secondaryContainer).
val NgbPrimaryContainer = Color(0xFF0E3A28)
val NgbOnPrimaryContainer = Color(0xFF9CFFCE)
val NgbOnSecondary = Color(0xFF160A33)
val NgbSecondaryContainer = Color(0xFF251A5C)
val NgbOnSecondaryContainer = Color(0xFFD8C8FF)
val NgbOnTertiary = Color(0xFF052539)
val NgbTertiaryContainer = Color(0xFF0E3450)
val NgbOnTertiaryContainer = Color(0xFFA8E4FF)

// AMOLED_BLACK variant: pure black background/surfaces, same neon accents.
// Text colors stay shared with the navy palette for identical contrast behavior.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0A0A0E)
val AmoledSurfaceVariant = Color(0xFF101018)
val AmoledSurfaceContainerLowest = Color(0xFF000000)
val AmoledSurfaceContainerLow = Color(0xFF060609)
val AmoledSurfaceContainer = Color(0xFF0A0A0E)
val AmoledSurfaceContainerHigh = Color(0xFF101018)
val AmoledSurfaceContainerHighest = Color(0xFF16161F)
val AmoledSurfaceDim = Color(0xFF000000)
val AmoledSurfaceBright = Color(0xFF1A1A24)
val AmoledInverseSurface = Color(0xFF16161F)
val AmoledInverseOnSurface = Color(0xFFE8ECFF)
val AmoledOutline = Color(0xFF2A3459)
