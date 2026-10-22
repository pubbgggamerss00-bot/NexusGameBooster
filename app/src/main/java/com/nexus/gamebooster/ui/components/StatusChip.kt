package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.monitoring.UnavailableReasons

/**
 * Semantic chip variants used across status rows.
 *
 *  - OK: reading succeeded / action applied.
 *  - OK_MUTED: success WITHOUT new work (e.g. an already-applied boost action) — the
 *    same green family as OK, muted so "nothing was done this time" stays visible.
 *  - OFFLINE: source not connected (reserved for potential future live sources).
 *  - UNAVAILABLE: reading genuinely failed right now (transient — a retry can help).
 *  - UNSUPPORTED: the API is permanently unavailable on this OS version (a muted,
 *    neutral style — different from the warning-style UNAVAILABLE).
 *  - PLANNED: feature is planned for a later phase (never a fake value).
 */
enum class StatusChipVariant { OK, OK_MUTED, OFFLINE, UNAVAILABLE, UNSUPPORTED, PLANNED }

/**
 * Small pill with a colored dot + label.
 *  - OK: reading succeeded.
 *  - OFFLINE: source not connected (reserved for Phase 2/3 live sources).
 *  - UNAVAILABLE: reading genuinely failed / not readable.
 *  - PLANNED: feature is planned for a later phase (never a fake value).
 */
@Composable
fun StatusChip(
    text: String,
    variant: StatusChipVariant,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (variant) {
        StatusChipVariant.OK -> {
            container = scheme.primary.copy(alpha = 0.14f)
            content = scheme.primary
        }
        StatusChipVariant.OK_MUTED -> {
            container = scheme.primary.copy(alpha = 0.07f)
            content = scheme.primary.copy(alpha = 0.55f)
        }
        StatusChipVariant.OFFLINE -> {
            container = scheme.secondary.copy(alpha = 0.16f)
            content = scheme.secondary
        }
        StatusChipVariant.UNAVAILABLE -> {
            container = scheme.error.copy(alpha = 0.12f)
            content = scheme.error
        }
        StatusChipVariant.UNSUPPORTED -> {
            container = scheme.onSurface.copy(alpha = 0.08f)
            content = scheme.onSurfaceVariant
        }
        StatusChipVariant.PLANNED -> {
            container = scheme.onSurface.copy(alpha = 0.08f)
            content = scheme.onSurfaceVariant
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(content),
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
