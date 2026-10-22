package com.nexus.gamebooster.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Horizontal and vertical page padding for every screen. */
private val ScreenPadding = 16.dp

/** Maximum content width on tablets / landscape — content stays a centered column. */
private val MaxContentWidth = 640.dp

/**
 * Standard screen shell: a full-size, vertically scrollable page with 16.dp padding
 * whose content is a CENTERED column capped at 640.dp wide. Mobile-first (phones fill
 * the width) while tablets/landscape get a readable line length instead of stretched
 * rows. Screens place their sections inside the [content] lambda as usual.
 */
@Composable
fun NgbScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MaxContentWidth),
            content = content,
        )
    }
}
