/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.home.chrome

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How opaque [NavigationBarStrip] is: enough that the buttons read, a little of the map still shows. */
private const val STRIP_ALPHA = 0.8f

/**
 * How much of the bottom edge [NavigationBarStrip] covers: the navigation bar's height when the bar has
 * buttons along the bottom, 0 otherwise (gesture navigation, or a landscape bar on a side). The sheets
 * rise by this much so their resting height is measured from the top of the strip, not the screen edge.
 */
@Composable
fun navigationBarStripHeight(): Dp {
    val density = LocalDensity.current
    return if (WindowInsets.tappableElement.getBottom(density) > 0) {
        with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    } else {
        0.dp
    }
}

/**
 * A strip behind the system navigation bar on HOME (#2337), so the bar's buttons sit on a band of their
 * own rather than straight over the edge-to-edge map or the sheets. Mostly opaque ([STRIP_ALPHA]): the
 * map only hints through it.
 *
 * Drawn only for a navigation bar with buttons in it — the platform reports those, and only those, as
 * [WindowInsets.tappableElement] — so gesture navigation stays fully edge-to-edge, its handle floating
 * over the map. The strip goes on whichever edge the buttons are on: the bottom, or a side in landscape.
 *
 * The window's own navigation-bar contrast scrim is switched off while HOME is composed (and restored on
 * leaving): it is far more see-through, so over the map it let the streets show through the buttons,
 * and over this strip it would only tint it.
 *
 * Call it in a [BoxScope] that fills the window, after the map and its sheets.
 */
@Composable
fun BoxScope.NavigationBarStrip() {
    val window = LocalActivity.current?.window
    if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        DisposableEffect(window) {
            val enforced = window.isNavigationBarContrastEnforced
            window.isNavigationBarContrastEnforced = false
            onDispose { window.isNavigationBarContrastEnforced = enforced }
        }
    }
    val color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = STRIP_ALPHA)

    // The inset reads are absolute (left/right, not start/end), so the strips are sized and aligned
    // absolutely too — an RTL layout mustn't mirror a bar the system keeps on the physical right.
    val buttons = WindowInsets.tappableElement
    val bar = WindowInsets.navigationBars
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val bottom = navigationBarStripHeight()
    if (bottom > 0.dp) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom).background(color))
    }
    if (buttons.getLeft(density, layoutDirection) > 0) {
        val width = with(density) { bar.getLeft(density, layoutDirection).toDp() }
        Box(Modifier.align(AbsoluteAlignment.CenterLeft).fillMaxHeight().width(width).background(color))
    }
    if (buttons.getRight(density, layoutDirection) > 0) {
        val width = with(density) { bar.getRight(density, layoutDirection).toDp() }
        Box(Modifier.align(AbsoluteAlignment.CenterRight).fillMaxHeight().width(width).background(color))
    }
}
