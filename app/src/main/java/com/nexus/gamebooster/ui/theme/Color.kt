package com.nexus.gamebooster.ui.theme

import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// Neon gaming palette — Phase 11.4 "NEON ARENA v1" retune. The values below are
// the EXACT tokens extracted from the user-supplied design mockups (the app
// theme artboard + the button/component kit): near-black page #05060D, card
// base #0B1030 with the #131A42→#242A52 container ramp, neon-green primary
// #27F58A (bright tip #3DFF9A), neon-violet #A78BFA, neon-cyan #22D3EE,
// neon-red #FF4D6D and amber #FFB020 for connecting/soon states.
// DARK_NAVY theme mode. All roles below are the SINGLE source of color truth —
// screens never hardcode colors, they read MaterialTheme.colorScheme.
// -----------------------------------------------------------------------------
val NgbBackground = Color(0xFF05060D)
val NgbSurface = Color(0xFF0B1030)
val NgbSurfaceVariant = Color(0xFF131A42)
val NgbPrimary = Color(0xFF27F58A)
val NgbOnPrimary = Color(0xFF03150C)
val NgbSecondary = Color(0xFFA78BFA)
val NgbTertiary = Color(0xFF22D3EE)
val NgbError = Color(0xFFFF4D6D)
val NgbOnBackground = Color(0xFFE8ECFF)
val NgbOutline = Color(0xFF262D5A)
val NgbTextMuted = Color(0xFFA3ACD8)

// Kit accent tokens not covered by Material roles (buttons/chips read these
// directly — they are part of the same single source of color truth).
val NgbPrimaryBright = Color(0xFF3DFF9A)
val NgbPrimaryDeep = Color(0xFF0FB566)
val NgbWarning = Color(0xFFFFB020)
val NgbWarningSoft = Color(0xFFFFCF7D)
val NgbErrorSoft = Color(0xFFFF93A8)
val NgbOnRedCta = Color(0xFF1C0309)
val NgbVioletDeep = Color(0xFF6D5BF0)

// Harmonized Material 3 "surface container" roles so default Card/Dialog/Snackbar
// containers stay in-palette instead of falling back to the grey baseline theme.
// The ramp mirrors the mockup card gradients (#131A42 → #242A52).
val NgbSurfaceContainerLowest = Color(0xFF080C1E)
val NgbSurfaceContainerLow = Color(0xFF0B1030)
val NgbSurfaceContainer = Color(0xFF131A42)
val NgbSurfaceContainerHigh = Color(0xFF1A2043)
val NgbSurfaceContainerHighest = Color(0xFF242A52)
val NgbSurfaceDim = Color(0xFF05060D)
val NgbSurfaceBright = Color(0xFF242A52)
val NgbInverseSurface = Color(0xFF242A52)
val NgbInverseOnSurface = Color(0xFFE8ECFF)

// Phase 8 container roles: the neon-tinted containers drive the selected-profile
// card (primaryContainer) and the active bottom-navigation pill (secondaryContainer).
val NgbPrimaryContainer = Color(0xFF0D2B1D)
val NgbOnPrimaryContainer = Color(0xFF9BFFCB)
val NgbOnSecondary = Color(0xFF12092E)
val NgbSecondaryContainer = Color(0xFF281E5F)
val NgbOnSecondaryContainer = Color(0xFFC9BDFF)
val NgbOnTertiary = Color(0xFF052539)
val NgbTertiaryContainer = Color(0xFF0A2B3C)
val NgbOnTertiaryContainer = Color(0xFF7BE7FF)

// AMOLED_BLACK variant: pure black background/surfaces, same neon accents.
// Text colors stay shared with the navy palette for identical contrast behavior.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF05060D)
val AmoledSurfaceVariant = Color(0xFF10121F)
val AmoledSurfaceContainerLowest = Color(0xFF000000)
val AmoledSurfaceContainerLow = Color(0xFF05060D)
val AmoledSurfaceContainer = Color(0xFF0B0D1A)
val AmoledSurfaceContainerHigh = Color(0xFF10121F)
val AmoledSurfaceContainerHighest = Color(0xFF161829)
val AmoledSurfaceDim = Color(0xFF000000)
val AmoledSurfaceBright = Color(0xFF161829)
val AmoledInverseSurface = Color(0xFF161829)
val AmoledInverseOnSurface = Color(0xFFE8ECFF)
val AmoledOutline = Color(0xFF262D5A)
