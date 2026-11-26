// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nidamanx.formica.R

/**
 * The launcher icon of the installed build: its foreground on its background, cut like a
 * launcher does (the central 72 of 108). Decorative: a text next to it names the app.
 */
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    shape: Shape = RoundedCornerShape(24.dp),
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colorResource(R.color.icon_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}
