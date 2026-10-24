package com.nexus.gamebooster.ui.theme

import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// Neon gaming palette — Phase 11.5 "NEON ARENA v2" retune. The values below are
// the EXACT tokens extracted from the user-supplied NexusGameBooster-Neon-Theme
// mockup (the definitive neon artboard): near-black purple-tinted page #05050C,
// translucent violet card base #0F0C1E, and the four saturated neon accents —
// green #00FF9D, cyan #00F0FF, purple #BF00FF, pink #FF2D95 — with white
// primary text and the muted lavender-grey #A0A0C0 for secondary text.
// DARK_NAVY theme mode. All roles below are the SINGLE source of color truth —
// screens never hardcode colors, they read MaterialTheme.colorScheme.
// -----------------------------------------------------------------------------
val NgbBackground = Color(0xFF05050C)
val NgbSurface = Color(0xFF0F0C1E)
val NgbSurfaceVariant = Color(0xFF171129)
val NgbPrimary = Color(0xFF00FF9D)
val NgbOnPrimary = Color(0xFF03150C)
val NgbSecondary = Color(0xFFBF00FF)
val NgbTertiary = Color(0xFF00F0FF)
val NgbError = Color(0xFFFF4D6D)
val NgbOnBackground = Color(0xFFFFFFFF)
val NgbOutline = Color(0xFF2A1F4D)
val NgbTextMuted = Color(0xFFA0A0C0)

// Kit accent tokens not covered by Material roles (buttons/chips read these
// directly — they are part of the same single source of color truth).
val NgbPrimaryBright = Color(0xFF66FFC2)
val NgbPrimaryDeep = Color(0xFF00B368)
val NgbPink = Color(0xFFFF2D95)
val NgbPurpleSoft = Color(0xFFE0A0FF)
val NgbWarning = Color(0xFFFFB020)
val NgbWarningSoft = Color(0xFFFFCF7D)
val NgbErrorSoft = Color(0xFFFF93A8)
val NgbOnRedCta = Color(0xFF1C0309)
val NgbVioletDeep = Color(0xFF8A2BE2)

// Harmonized Material 3 "surface container" roles so default Card/Dialog/Snackbar
// containers stay in-palette instead of falling back to the grey baseline theme.
// The ramp mirrors the mockup's violet-tinted card family (#0F0C1E and lighter).
val NgbSurfaceContainerLowest = Color(0xFF070512)
val NgbSurfaceContainerLow = Color(0xFF0F0C1E)
val NgbSurfaceContainer = Color(0xFF171129)
val NgbSurfaceContainerHigh = Color(0xFF1F1740)
val NgbSurfaceContainerHighest = Color(0xFF2A1F55)
val NgbSurfaceDim = Color(0xFF05050C)
val NgbSurfaceBright = Color(0xFF2A1F55)
val NgbInverseSurface = Color(0xFF2A1F55)
val NgbInverseOnSurface = Color(0xFFFFFFFF)

// Phase 8 container roles: the neon-tinted containers drive the selected-profile
// card (primaryContainer) and the active bottom-navigation pill (secondaryContainer).
val NgbPrimaryContainer = Color(0xFF062B1C)
val NgbOnPrimaryContainer = Color(0xFF8DFFC4)
val NgbOnSecondary = Color(0xFF1F0033)
val NgbSecondaryContainer = Color(0xFF31094A)
val NgbOnSecondaryContainer = Color(0xFFE0A0FF)
val NgbOnTertiary = Color(0xFF032329)
val NgbTertiaryContainer = Color(0xFF06303A)
val NgbOnTertiaryContainer = Color(0xFF8DF6FF)

// AMOLED_BLACK variant: pure black background/surfaces, same neon accents.
// Text colors stay shared with the navy palette for identical contrast behavior.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF050409)
val AmoledSurfaceVariant = Color(0xFF0B0817)
val AmoledSurfaceContainerLowest = Color(0xFF000000)
val AmoledSurfaceContainerLow = Color(0xFF050409)
val AmoledSurfaceContainer = Color(0xFF0B0817)
val AmoledSurfaceContainerHigh = Color(0xFF120D22)
val AmoledSurfaceContainerHighest = Color(0xFF191233)
val AmoledSurfaceDim = Color(0xFF000000)
val AmoledSurfaceBright = Color(0xFF191233)
val AmoledInverseSurface = Color(0xFF191233)
val AmoledInverseOnSurface = Color(0xFFFFFFFF)
val AmoledOutline = Color(0xFF2A1F4D)
