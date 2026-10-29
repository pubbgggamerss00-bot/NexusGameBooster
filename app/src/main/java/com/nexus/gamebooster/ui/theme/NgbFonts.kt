@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.nexus.gamebooster.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nexus.gamebooster.R

/**
 * v1.2.1 "ARCADE HUD" type kit — the exact font stack of the v1.2.1 redesign
 * reference (Nexus Game Booster.html):
 *
 *  - --f-body: 'Vazirmatn'          → [NgbBodyFont] (variable, wght 400–900).
 *    Persian-first body font; also carries Latin. Wired into [NgbTypography]
 *    as the DEFAULT family so every Material text style inherits it.
 *  - --f-hud : 'Rajdhani'           → [NgbHudFont] (static 500/700).
 *    Squared tabular digits for gauges, countdowns, codes and ping values.
 *  - --f-tag : 'Orbitron'           → [NgbTagFont] (variable, 600/800).
 *    Sci-fi tracking tags: "NEXUS BOOSTER", "// HOME", "TAP TO BOOST".
 *
 * All fonts are SIL OFL 1.1 (see THIRD_PARTY_NOTICES.md). Variable axes are
 * driven through [FontVariation] so a single TTF per family serves every
 * weight the design uses — no extra font-file weight in the APK.
 */

/** Persian/Latin body font — the redesign's `--f-body`. */
val NgbBodyFont: FontFamily = FontFamily(
    Font(
        resId = R.font.vazirmatn_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.vazirmatn_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.vazirmatn_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.vazirmatn_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
    Font(
        resId = R.font.vazirmatn_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
    Font(
        resId = R.font.vazirmatn_variable,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900)),
    ),
)

/** HUD numerics/codes font — the redesign's `--f-hud` (Rajdhani 500/700). */
val NgbHudFont: FontFamily = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_medium, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
    Font(R.font.rajdhani_bold, FontWeight.ExtraBold),
    Font(R.font.rajdhani_bold, FontWeight.Black),
)

/** Sci-fi tag font — the redesign's `--f-tag` (Orbitron 600/800). */
val NgbTagFont: FontFamily = FontFamily(
    Font(
        resId = R.font.orbitron_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.orbitron_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)

/** [NgbTypography] rebuilt on the Vazirmatn default family (same metrics/weights). */
fun ngbTypographyWithFonts(base: Typography): Typography = base.withDefaultFamily(NgbBodyFont)

private fun Typography.withDefaultFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
