package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.gamebooster.ui.theme.NgbPrimary
import com.nexus.gamebooster.ui.theme.NgbSecondary
import com.nexus.gamebooster.ui.theme.NgbTextMuted

/**
 * Shared compact header used to give every primary screen a consistent visual
 * identity — now the "ROCKET EDITION" kit panel (v1.2.1 re-skin): chamfered
 * gradient-ring box instead of the rounded gradient card.
 *
 * [code] is the optional mockup `.head .code` HUD tag — the lime, letter-spaced
 * `// CODE` line rendered LTR above the title (e.g. "HOME", "LIBRARY"). It is
 * decorative only: when null the header renders title/subtitle exactly as before.
 */
@Composable
fun NgbPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    code: String? = null,
) {
    NgbPanel(
        modifier = modifier.fillMaxWidth(),
        cut = 16.dp,
        ring = NgbPanelRing.Hot,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (!code.isNullOrBlank()) {
                Text(
                    text = "// " + code,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp,
                    color = NgbSecondary,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFECFFF4),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NgbTextMuted,
                )
            }
        }
    }
}
