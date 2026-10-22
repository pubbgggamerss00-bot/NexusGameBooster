package com.nexus.gamebooster.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App corner-radius scale (Phase 7 design tokens). Slightly smaller than the Material
 * defaults for a restrained, technical look; applied through MaterialTheme so every
 * Card / Button / dialog picks it up consistently. Do not hardcode radii in screens —
 * use MaterialTheme.shapes instead.
 */
val NgbShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
