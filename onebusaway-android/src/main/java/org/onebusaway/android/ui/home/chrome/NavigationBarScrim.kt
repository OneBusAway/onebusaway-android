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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The platform's own navigation-bar scrims (DecorView's light scrim; androidx.activity's default dark
// one), so HOME's bar reads the same as a system-drawn one would.
private val LIGHT_SCRIM = Color(0xE6FFFFFF)
private val DARK_SCRIM = Color(0x801B1B1B)

/**
 * A translucent scrim behind the system navigation bar, over the edge-to-edge home map (#2337).
 *
 * Drawn only for a navigation bar with buttons in it — the platform reports those, and only those, as
 * [WindowInsets.tappableElement] — so gesture navigation stays fully edge-to-edge, its handle floating
 * over the map. The scrim goes on whichever edge the buttons are on: the bottom, or a side in landscape.
 *
 * HOME owns this scrim rather than leaving it to the window's navigation-bar contrast enforcement, which
 * is up to the platform and the device maker whether it draws; so while HOME is composed the window's
 * enforcement is switched off (and restored on leaving), and the bar never gets two scrims stacked.
 * Its colour follows the bar's own light/dark appearance, the way the system's scrim does, so the
 * buttons keep their contrast against it.
 *
 * Call it in a [BoxScope] that fills the window, above the map and below everything else.
 */
@Composable
fun BoxScope.NavigationBarScrim() {
    val window = LocalActivity.current?.window
    if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        DisposableEffect(window) {
            val enforced = window.isNavigationBarContrastEnforced
            window.isNavigationBarContrastEnforced = false
            onDispose { window.isNavigationBarContrastEnforced = enforced }
        }
    }
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val color = remember(window, view, darkTheme) {
        val light = window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars }
            ?: !darkTheme
        if (light) LIGHT_SCRIM else DARK_SCRIM
    }

    // The inset reads are absolute (left/right, not start/end), so the scrims are sized and aligned
    // absolutely too — an RTL layout mustn't mirror a bar the system keeps on the physical right.
    val buttons = WindowInsets.tappableElement
    val bar = WindowInsets.navigationBars
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    if (buttons.getBottom(density) > 0) {
        val height = with(density) { bar.getBottom(density).toDp() }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height).background(color))
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
