package com.nexus.gamebooster.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexus.gamebooster.ui.arcade.ArcadeHead

/**
 * Screen header — v1.2.1 "ARCADE HUD" restyle (`.head` of the redesign
 * reference): the lime Orbitron `// CODE` tag, the heavy 24 sp title and the
 * muted subtitle, rendered through [ArcadeHead].
 *
 * @param code optional HUD screen code ("HOME", "LIBRARY", …) shown as the
 *   `// CODE` tag; empty hides the tag (legacy call sites stay compatible).
 */
@Composable
fun NgbPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    code: String = "",
) {
    ArcadeHead(
        code = code,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    )
}
