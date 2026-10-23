package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.ui.theme.neonGlow

/**
 * The kit's section header (`.ph`): a glowing green diamond marker followed by
 * the section title — the marker is the rotated 8 dp square with the 8 px glow
 * straight from the mockup CSS.
 */
@Composable
fun NgbSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    markerColor: Color = Color(0xFF27F58A),
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
 * The kit's mini tag (`.mini`): a 6 dp-radius violet pill with the diamond
 * dot — used for the sync-card counts (DNS / configs).
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
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF8B5CF6).copy(alpha = 0.14f))
            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCFC6FF)))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFFCFC6FF))
    }
}
