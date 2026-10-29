package com.nexus.gamebooster.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App corner-radius scale (Phase 7 design tokens). Slightly smaller than the Material
 * defaults for a restrained, technical look; applied through MaterialTheme so every
 * Card / Button / dialog picks it up consistently. Do not hardcode radii in screens —
 * use MaterialTheme.shapes instead.
 *
 * v1.2.1 note: Material3's [Shapes] contract requires [CornerBasedShape] roles, so
 * the kit's chamfered silhouette ([NgbChamferShape]) can NOT ride the Material
 * theme — it is applied at the kit call sites instead (NgbButton, NgbPanel,
 * SectionCard, StatusChip, the seg controls, and the screen-level cards). These
 * rounded roles keep dialogs / menus / pickers consistent with the same radius
 * ladder the chamfer cuts use (6 / 8 / 12 / 16 / 20).
 */
val NgbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
