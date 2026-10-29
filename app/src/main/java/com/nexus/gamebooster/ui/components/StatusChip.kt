package com.nexus.gamebooster.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.monitoring.UnavailableReasons
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbErrorSoft
import com.nexus.gamebooster.ui.theme.NgbPrimaryBright
import com.nexus.gamebooster.ui.theme.NgbWarningSoft
import com.nexus.gamebooster.ui.theme.neonGlow
import com.nexus.gamebooster.ui.theme.NgbPrimary

/**
 * Semantic chip variants used across status rows.
 *
 *  - OK: reading succeeded / action applied.
 *  - OK_MUTED: success WITHOUT new work (e.g. an already-applied boost action) — the
 *    same green family as OK, muted so "nothing was done this time" stays visible.
 *  - OK_GLOW: the kit's ".st-on" chip — active/connected, glowing dot + border.
 *  - PENDING: the kit's ".st-con" chip — work in progress / handshake (amber, blinking dot).
 *  - ALERT: the kit's ".st-off" chip — disconnected / needs attention (soft red).
 *  - OFFLINE: source not connected (reserved for potential future live sources).
 *  - UNAVAILABLE: reading genuinely failed right now (transient — a retry can help).
 *  - UNSUPPORTED: the API is permanently unavailable on this OS version (a muted,
 *    neutral style — different from the warning-style UNAVAILABLE).
 *  - PLANNED: feature is planned for a later phase (never a fake value).
 */
enum class StatusChipVariant { OK, OK_MUTED, OK_GLOW, PENDING, ALERT, OFFLINE, UNAVAILABLE, UNSUPPORTED, PLANNED }

private data class ChipStyle(val container: Color, val content: Color, val border: Color, val blink: Boolean, val glow: Boolean)

/**
 * Small chip with a colored dot + label, restyled to the kit: the v1.2.1
 * "ROCKET EDITION" re-skin gives every variant the mockup `.chip` silhouette
 * (6 dp two-corner chamfer, 1 dp tinted border), and the kit's active/pending
 * chips keep the glowing/blinking dot (both animations respect
 * [LocalNgbMotionEnabled] — reduced motion renders the same chips fully static).
 */
@Composable
fun StatusChip(
    text: String,
    variant: StatusChipVariant,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalNgbMotionEnabled.current
    val style = when (variant) {
        StatusChipVariant.OK -> ChipStyle(scheme.primary.copy(alpha = 0.14f), scheme.primary, scheme.primary.copy(alpha = 0.55f), blink = false, glow = false)
        StatusChipVariant.OK_MUTED -> ChipStyle(scheme.primary.copy(alpha = 0.07f), scheme.primary.copy(alpha = 0.55f), scheme.primary.copy(alpha = 0.30f), blink = false, glow = false)
        StatusChipVariant.OK_GLOW -> ChipStyle(NgbPrimary.copy(alpha = 0.12f), NgbPrimaryBright, NgbPrimary.copy(alpha = 0.55f), blink = true, glow = true)
        StatusChipVariant.PENDING -> ChipStyle(Color(0xFFFFC46B).copy(alpha = 0.12f), NgbWarningSoft, Color(0xFFFFC46B).copy(alpha = 0.50f), blink = true, glow = false)
        StatusChipVariant.ALERT -> ChipStyle(Color(0xFFFF4D6D).copy(alpha = 0.12f), NgbErrorSoft, Color(0xFFFF4D6D).copy(alpha = 0.50f), blink = false, glow = false)
        StatusChipVariant.OFFLINE -> ChipStyle(scheme.secondary.copy(alpha = 0.16f), scheme.secondary, scheme.secondary.copy(alpha = 0.50f), blink = false, glow = false)
        StatusChipVariant.UNAVAILABLE -> ChipStyle(scheme.error.copy(alpha = 0.12f), scheme.error, scheme.error.copy(alpha = 0.50f), blink = false, glow = false)
        StatusChipVariant.UNSUPPORTED -> ChipStyle(scheme.onSurface.copy(alpha = 0.08f), scheme.onSurfaceVariant, scheme.onSurfaceVariant.copy(alpha = 0.40f), blink = false, glow = false)
        StatusChipVariant.PLANNED -> ChipStyle(scheme.onSurface.copy(alpha = 0.08f), scheme.onSurfaceVariant, scheme.onSurfaceVariant.copy(alpha = 0.40f), blink = false, glow = false)
    }

    // Kit blink keyframes (opacity 1 → .3 → 1): 1.6 s for the active green dot,
    // 0.7 s for the pending amber dot.
    val dotAlpha: Float
    if (motion && style.blink) {
        val t = rememberInfiniteTransition(label = "ngbChipBlink")
        val a by t.animateFloat(
            initialValue = 1f, targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                tween(if (variant == StatusChipVariant.OK_GLOW) 800 else 350),
                RepeatMode.Reverse,
            ),
            label = "ngbChipBlinkAlpha",
        )
        dotAlpha = a
    } else {
        dotAlpha = 1f
    }

    // Mockup `.chip` clip-path: 6px two-corner chamfer (was a rounded pill).
    val chipShape = NgbChamferShape(6.dp)
    val base = if (style.glow && motion) {
        modifier.neonGlow(
            glowColor = NgbPrimary,
            shape = chipShape,
            blurRadius = 14.dp,
            glowAlpha = 0.28f,
        )
    } else {
        modifier
    }

    Surface(
        modifier = base,
        shape = chipShape,
        color = style.container,
        contentColor = style.content,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.dp, style.border, chipShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(style.content),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Chip variant for a stable [UnavailableReasons] key: OS-version gates are PERMANENT
 * on the given device (muted UNSUPPORTED chip), everything else (read errors, missing
 * reports) is TRANSIENT (warning-style UNAVAILABLE chip — a retry may help).
 */
fun chipVariantForReason(reason: String): StatusChipVariant = when (reason) {
    UnavailableReasons.REQUIRES_ANDROID_10, UnavailableReasons.REQUIRES_ANDROID_11 ->
        StatusChipVariant.UNSUPPORTED
    else -> StatusChipVariant.UNAVAILABLE
}
