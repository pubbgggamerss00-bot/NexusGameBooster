package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.ui.motion.LocalNgbMotionEnabled
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.NgbChamferShape
import com.nexus.gamebooster.ui.theme.NgbInkSoft
import com.nexus.gamebooster.ui.theme.NgbOutline
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbPrimaryDeep
import com.nexus.gamebooster.ui.theme.neonGlow
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbPurpleSoft

/**
 * The kit's section header (`.ph`): a glowing green diamond marker followed by
 * the section title — the marker is the rotated 8 dp square with the 8 px glow
 * straight from the mockup CSS.
 */
@Composable
fun NgbSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    markerColor: Color = NgbPrimary,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(markerColor)
                .neonGlow(glowColor = markerColor, shape = RoundedCornerShape(2.dp), blurRadius = 6.dp, glowAlpha = 0.8f),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The kit's mini tag: a 6 dp CHAMFERED lime pill (the mockup `.chip` silhouette,
 * v1.2.1 re-skin — was a 6 dp-radius rounded pill) with the diamond dot — used
 * for the sync-card counts (DNS / configs).
 */
@Composable
fun NgbMiniChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(24.dp)
            .clip(NgbChamferShape(6.dp))
            .background(NgbSecondary.copy(alpha = 0.14f))
            .border(1.dp, NgbSecondary.copy(alpha = 0.45f), NgbChamferShape(6.dp))
            .padding(horizontal = 9.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(NgbPurpleSoft))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = NgbPurpleSoft)
    }
}

/**
 * Kit `.seg` segmented-control container (v1.2.1 re-skin from the HTML
 * prototype): the `--ink2` well with the 1 dp outline border, 4 dp padding and
 * 4 dp gaps. Children (usually [NgbSegButton]s) carry the chamfered silhouette;
 * the selected one paints the neon vertical gradient.
 */
@Composable
fun NgbSegRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(NgbChamferShape(8.dp))
            .background(NgbInkSoft)
            .border(1.dp, NgbOutline, NgbChamferShape(8.dp))
            .padding(4.dp),
        content = content,
    )
}

/**
 * Kit `.seg button` (v1.2.1 re-skin): 6 dp chamfered silhouette; the selected
 * option paints the mockup's exact vertical gradient `linear-gradient(180deg,
 * var(--neon), var(--neon-deep))` with the dark ink text and the green glow
 * (`box-shadow:0 0 14px rgba(61,255,124,.45)`). Pass [icon] for the tab style.
 * [role] keeps the TalkBack story (RadioButton for filter groups, Tab for tabs).
 */
@Composable
fun NgbSegButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = NgbChamferShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.neonGlow(glowColor = NgbPrimary, shape = shape, blurRadius = 14.dp, glowAlpha = 0.45f)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                if (selected) {
                    // .seg button.on — vertical neon → deep gradient.
                    drawRect(Brush.verticalGradient(listOf(NgbPrimary, NgbPrimaryDeep)))
                }
            }
            .ngbPressScale(interactionSource, LocalNgbMotionEnabled.current)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = role,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF04110A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) {
                // .seg button.on color: #04110A ink on the neon gradient.
                Color(0xFF04110A)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
