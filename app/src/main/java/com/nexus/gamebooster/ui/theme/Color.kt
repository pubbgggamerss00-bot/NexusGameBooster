package com.nexus.gamebooster.ui.theme

import androidx.compose.ui.graphics.Color

// -----------------------------------------------------------------------------
// "ROCKET EDITION" palette — v1.2.0 retoken (Phase 18). The values below are the
// EXACT tokens from the v1.2.0 web prototype (src/app/globals.css, the design
// source of truth): forest-green ink page #04110A, deep green card base #0A2314,
// and the saturated accents — neon green #3DFF7C, lime #C9FF4A, porthole azure
// #56C9F0 — with mint-white primary text #ECFFF4 and the muted sage #8FB79D for
// secondary text. Role NAMES are unchanged from the NEON ARENA v2 palette, so
// screens read the exact same MaterialTheme roles as before — this release is a
// pure re-skin with zero functional changes.
// DARK_NAVY theme mode. All roles below are the SINGLE source of color truth —
// screens never hardcode colors, they read MaterialTheme.colorScheme.
// -----------------------------------------------------------------------------
val NgbBackground = Color(0xFF04110A)
val NgbSurface = Color(0xFF0A2314)
val NgbSurfaceVariant = Color(0xFF0F301C)
val NgbPrimary = Color(0xFF3DFF7C)
val NgbOnPrimary = Color(0xFF04110A)
val NgbSecondary = Color(0xFFC9FF4A)
val NgbTertiary = Color(0xFF56C9F0)
val NgbError = Color(0xFFFF4D6D)
val NgbOnBackground = Color(0xFFECFFF4)
val NgbOutline = Color(0xFF1E3A2A)
val NgbTextMuted = Color(0xFF8FB79D)

// Kit accent tokens not covered by Material roles (buttons/chips read these
// directly — they are part of the same single source of color truth).
val NgbPrimaryBright = Color(0xFF8CFFB0)
val NgbPrimaryDeep = Color(0xFF16A34A)
val NgbPink = Color(0xFFC9FF4A)
val NgbPurpleSoft = Color(0xFFD9FFB0)
val NgbWarning = Color(0xFFFFC46B)
val NgbWarningSoft = Color(0xFFFFDFA3)
val NgbErrorSoft = Color(0xFFFF93A8)
val NgbOnRedCta = Color(0xFF1C0309)
val NgbVioletDeep = Color(0xFF1F7A46)

// Harmonized Material 3 "surface container" roles so default Card/Dialog/Snackbar
// containers stay in-palette instead of falling back to the grey baseline theme.
// The ramp mirrors the prototype's green-tinted card family (#0A2314 and lighter).
val NgbSurfaceContainerLowest = Color(0xFF020805)
val NgbSurfaceContainerLow = Color(0xFF0A2314)
val NgbSurfaceContainer = Color(0xFF0F301C)
val NgbSurfaceContainerHigh = Color(0xFF163D24)
val NgbSurfaceContainerHighest = Color(0xFF1C4A2C)
val NgbSurfaceDim = Color(0xFF04110A)
val NgbSurfaceBright = Color(0xFF1C4A2C)
val NgbInverseSurface = Color(0xFF1C4A2C)
val NgbInverseOnSurface = Color(0xFFECFFF4)

// Phase 8 container roles: the neon-tinted containers drive the selected-profile
// card (primaryContainer) and the active bottom-navigation pill (secondaryContainer).
val NgbPrimaryContainer = Color(0xFF0B3A20)
val NgbOnPrimaryContainer = Color(0xFF8CFFB0)
val NgbOnSecondary = Color(0xFF131A03)
val NgbSecondaryContainer = Color(0xFF2E3A0D)
val NgbOnSecondaryContainer = Color(0xFFE4FF9E)
val NgbOnTertiary = Color(0xFF062B33)
val NgbTertiaryContainer = Color(0xFF0B3A44)
val NgbOnTertiaryContainer = Color(0xFFA8E7FA)

// AMOLED_BLACK variant: pure black background/surfaces, same rocket accents.
// Text colors stay shared with the navy palette for identical contrast behavior.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF050F09)
val AmoledSurfaceVariant = Color(0xFF0A1F12)
val AmoledSurfaceContainerLowest = Color(0xFF000000)
val AmoledSurfaceContainerLow = Color(0xFF050F09)
val AmoledSurfaceContainer = Color(0xFF0A1F12)
val AmoledSurfaceContainerHigh = Color(0xFF10301C)
val AmoledSurfaceContainerHighest = Color(0xFF14391F)
val AmoledSurfaceDim = Color(0xFF000000)
val AmoledSurfaceBright = Color(0xFF14391F)
val AmoledInverseSurface = Color(0xFF14391F)
val AmoledInverseOnSurface = Color(0xFFECFFF4)
val AmoledOutline = Color(0xFF1E3A2A)
