package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.gamebooster.R
import com.nexus.gamebooster.ui.motion.ngbPressScale
import com.nexus.gamebooster.ui.theme.neonGlow
import com.nexus.gamebooster.ui.theme.ngbNeonBorderBrush
import com.nexus.gamebooster.ui.theme.ngbNeonHalo

/**
 * Hero card with the circular neon "Boost" button — now wired to the REAL Phase 5 boost
 * engine, with restrained, honest state handling:
 *
 *  - Idle: the bolt icon + "Boost" label (tappable).
 *  - Checking / running: a small REAL indeterminate [CircularProgressIndicator] inside
 *    the ring (never a percentage or fake progress) + the honest "Checking…"/"Boosting…"
 *    label; the button is disabled while a run is active.
 *  - Finished: a status-colored label (Completed / Partial / Cancelled / Failed) passed
 *    in by the owning screen; tapping starts a new run (Retry is the dialog's job).
 *
 * The card stays presentation-only: the caller supplies the localized label, the accent
 * color and the enabled flag — no boost logic, no invented values.
 *
 * Phase 8 polish: the ring carries a real single-color neon glow (static paint —
 * no animation; pre-API 28 devices get a layered-halo fallback), a radial accent
 * wash behind the bolt and a brighter rim, and the card wears the same neon
 * gradient frame as every other section. The ring gets automatic,
 * reduced-motion-aware press feedback via [ngbPressScale] (the SAME interaction
 * source is attached to the clickable), is exposed to screen readers as a
 * [Role.Button] whose merged label is the visible status text, and grows
 * vertically instead of clipping when font scaling makes the status label wrap
 * (the circle then becomes a taller stadium — height is min, not max).
 */
@Composable
fun BoostCard(
    label: String,
    accentColor: Color,
    enabled: Boolean,
    onBoostClick: () -> Unit,
    modifier: Modifier = Modifier,
    inProgress: Boolean = false,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .ngbNeonHalo(cornerRadius = 18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.25.dp, ngbNeonBorderBrush(alpha = 0.5f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.boost_card_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.boost_card_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(104.dp)
                    // Min, not fixed: with font scaling the status label can wrap and
                    // the ring must grow instead of clipping its text.
                    .heightIn(min = 104.dp)
                    // Static neon glow painted behind the ring (outermost in the
                    // chain so the later clip never cuts it).
                    .neonGlow(
                        glowColor = accentColor,
                        shape = CircleShape,
                        blurRadius = 16.dp,
                        glowAlpha = 0.45f,
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = 0.22f),
                                accentColor.copy(alpha = 0.06f),
                            ),
                        ),
                    )
                    .border(
                        width = 2.dp,
                        color = accentColor.copy(alpha = 0.8f),
                        shape = CircleShape,
                    )
                    .ngbPressScale(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onBoostClick,
                    ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                ) {
                    if (inProgress) {
                        // A REAL indeterminate indicator — no fake percentage is ever shown.
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
