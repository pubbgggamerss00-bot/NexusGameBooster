package com.nexus.gamebooster.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * "ROCKET EDITION" kit geometry (v1.2.1 re-skin from the HTML prototype's
 * `.panel` / `.gbtn` / `.obtn` clip-path): the signature cut-corner silhouette
 * with TWO OPPOSITE corners cut — top-start and bottom-end.
 *
 * The prototype's clip-path is
 *   polygon(cut 0, 100% 0, 100% calc(100% - cut), calc(100% - cut) 100%, 0 100%, 0 cut)
 * which cuts the top-left and bottom-right corners in the mockup's own (LTR)
 * coordinates. Because every screen of this app is laid out in Persian first
 * (RTL), the cuts mirror with the layout direction: in RTL they land on
 * top-right / bottom-left, exactly as a mirrored design board would. LTR
 * layouts keep the mockup's original corners.
 *
 * No blur, no bitmap — one Path per outline request, cached by Compose.
 */
class NgbChamferShape(private val cut: Dp) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
        val c = with(density) { cut.toPx() }
        val w = size.width
        val h = size.height
        // Corner cut amounts. LTR: top-left + bottom-right (the mockup's own
        // corners). RTL: mirrored — top-right + bottom-left.
        val tl = if (layoutDirection == LayoutDirection.Rtl) 0f else c
        val tr = if (layoutDirection == LayoutDirection.Rtl) c else 0f
        val br = tl
        val bl = tr
        val path = Path().apply {
            moveTo(tl, 0f)
            lineTo(w - tr, 0f)
            if (tr > 0f) lineTo(w, tr)
            lineTo(w, h - br)
            if (br > 0f) lineTo(w - br, h)
            lineTo(bl, h)
            if (bl > 0f) lineTo(0f, h - bl)
            lineTo(0f, tl)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Draws the kit's two small L-shaped corner ticks (`.tk`/`.cn` in the mockup
 * CSS) inside the chamfered element: one at top-end, one at bottom-start —
 * the same opposite-corner rhythm as the shape itself. Ticks mirror with the
 * layout direction exactly like [NgbChamferShape].
 */
fun DrawScope.ngbCornerTicks(
    color: Color,
    tick: Dp = 8.dp,
    inset: Dp = 6.dp,
    stroke: Dp = 2.dp,
) {
    val rtl = layoutDirection == LayoutDirection.Rtl
    val t = tick.toPx()
    val i = inset.toPx()
    val s = stroke.toPx()
    // Top-end tick: horizontal + vertical segment forming an "L" opening inward.
    val topEndX = if (rtl) i else size.width - i
    val botStartX = if (rtl) size.width - i else i
    // horizontal segment
    drawLine(
        color = color,
        start = Offset(topEndX - if (rtl) t else 0f, i),
        end = Offset(topEndX, i),
        strokeWidth = s,
    )
    drawLine(
        color = color,
        start = Offset(topEndX, i),
        end = Offset(topEndX, i + t),
        strokeWidth = s,
    )
    // bottom-start tick
    drawLine(
        color = color,
        start = Offset(botStartX, size.height - i),
        end = Offset(botStartX + if (rtl) 0f else t, size.height - i),
        strokeWidth = s,
    )
    drawLine(
        color = color,
        start = Offset(botStartX, size.height - i),
        end = Offset(botStartX, size.height - i - t),
        strokeWidth = s,
    )
}
