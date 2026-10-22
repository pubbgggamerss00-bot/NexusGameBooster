package com.nexus.gamebooster.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind

/**
 * Neon showcase helpers (Phase 8): the pieces that turn the palette into the
 * "glowing border" look from the showcase art — a sweep-gradient frame for cards
 * plus a soft outer glow for hero elements.
 *
 * Performance honesty: everything here is STATIC drawing. There is no animation
 * and no per-frame work — the glow is painted once whenever the content redraws,
 * so battery/interaction costs stay the same as before. [LocalNgbMotionEnabled]
 * therefore does not gate these visuals (reduced motion concerns MOVEMENT, not
 * static paint).
 */

/** The neon accent ramp used for gradient card frames: green → cyan → violet → green. */
private val NgbNeonGradientColors = listOf(NgbPrimary, NgbTertiary, NgbSecondary, NgbPrimary)

/**
 * Sweep-gradient brush for card borders. [alpha] keeps the frame restrained
 * (the showcase look, not a rave).
 */
fun ngbNeonBorderBrush(alpha: Float = 0.55f): Brush =
    Brush.sweepGradient(NgbNeonGradientColors.map { it.copy(alpha = alpha) })

/**
 * Faint outer halo around a rounded element: one soft extra stroke painted just
 * outside the bounds, centered on the element edge. Cheap (no blur filter) and
 * reads as a glow at small sizes.
 */
fun Modifier.ngbNeonHalo(
    cornerRadius: Dp,
    width: Dp = 6.dp,
    alpha: Float = 0.14f,
): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        brush = ngbNeonBorderBrush(alpha),
        topLeft = Offset(-w / 2f, -w / 2f),
        size = Size(size.width + w, size.height + w),
        cornerRadius = CornerRadius((cornerRadius + width / 2).toPx()),
        style = Stroke(width = w),
    )
}

/**
 * Soft outer glow behind the element in a SINGLE accent color — the boost ring's
 * signature look. Real Gaussian blur is only supported by the hardware canvas on
 * API 28+; below that the blur filter is ignored by the framework, which would
 * leave a SOLID blob — so API 26/27 devices get the cheap layered-halo fallback
 * instead. No animation anywhere.
 */
fun Modifier.neonGlow(
    glowColor: Color,
    shape: Shape,
    blurRadius: Dp = 14.dp,
    glowAlpha: Float = 0.40f,
): Modifier = drawBehind {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val outline = shape.createOutline(size, layoutDirection, this)
        val paint = Paint().apply {
            isAntiAlias = true
            setColor(glowColor.copy(alpha = glowAlpha).toArgb())
            maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        drawIntoCanvas { canvas ->
            when (val o = outline) {
                is Outline.Rounded -> {
                    val r = o.roundRect
                    val rect = RectF(r.left, r.top, r.right, r.bottom)
                    val radii = floatArrayOf(
                        r.topLeftCornerRadius.x, r.topLeftCornerRadius.y,
                        r.topRightCornerRadius.x, r.topRightCornerRadius.y,
                        r.bottomRightCornerRadius.x, r.bottomRightCornerRadius.y,
                        r.bottomLeftCornerRadius.x, r.bottomLeftCornerRadius.y,
                    )
                    val path = android.graphics.Path().apply {
                        addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
                    }
                    canvas.nativeCanvas.drawPath(path, paint)
                }
                // Outline.Generic / Outline.Ellipse / Outline.Rectangle are not used
                // by any glow call site (ring + stadium buttons are Outline.Rounded);
                // drawing nothing there is the safe fallback.
                else -> Unit
            }
        }
    } else {
        // Pre-API 28 fallback: three concentric strokes of decreasing alpha.
        val base = blurRadius.toPx()
        for (i in 1..3) {
            val w = base * (4 - i) * 0.6f
            drawRoundRect(
                color = glowColor.copy(alpha = glowAlpha * 0.22f / i),
                topLeft = Offset(-w / 2f, -w / 2f),
                size = Size(size.width + w, size.height + w),
                cornerRadius = CornerRadius((size.minDimension / 2f + w / 2f).coerceAtMost(28.dp.toPx())),
                style = Stroke(width = w),
            )
        }
    }
}
