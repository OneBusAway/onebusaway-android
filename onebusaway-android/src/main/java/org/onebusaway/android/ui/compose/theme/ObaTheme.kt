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
package org.onebusaway.android.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import org.onebusaway.android.R

/**
 * Material 3 theme for Compose screens, built from the same md_theme_* color resources that back
 * the XML Theme.OneBusAway themes.
 *
 * Colors are read via [colorResource] instead of being duplicated as Kotlin constants so there is
 * a single source of truth: night mode switches automatically with the resource configuration,
 * and white-label brand flavors that override colors.xml (see docs/REBRANDING.md) are respected
 * without any Compose-side changes. Slots not defined in colors.xml fall back to the Material 3
 * baseline for the current light/dark mode.
 */
@Composable
fun ObaTheme(content: @Composable () -> Unit) {
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = colorResource(R.color.md_theme_primary),
        onPrimary = colorResource(R.color.md_theme_onPrimary),
        primaryContainer = colorResource(R.color.md_theme_primaryContainer),
        onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer),
        secondary = colorResource(R.color.md_theme_secondary),
        onSecondary = colorResource(R.color.md_theme_onSecondary),
        secondaryContainer = colorResource(R.color.md_theme_secondaryContainer),
        onSecondaryContainer = colorResource(R.color.md_theme_onSecondaryContainer),
        error = colorResource(R.color.md_theme_error),
        onError = colorResource(R.color.md_theme_onError),
        errorContainer = colorResource(R.color.md_theme_errorContainer),
        onErrorContainer = colorResource(R.color.md_theme_onErrorContainer),
        background = colorResource(R.color.md_theme_background),
        onBackground = colorResource(R.color.md_theme_onBackground),
        surface = colorResource(R.color.md_theme_surface),
        onSurface = colorResource(R.color.md_theme_onSurface),
        surfaceContainerLowest = colorResource(R.color.md_theme_surfaceContainerLowest),
        surfaceContainerLow = colorResource(R.color.md_theme_surfaceContainerLow),
        surfaceContainer = colorResource(R.color.md_theme_surfaceContainer),
        surfaceContainerHigh = colorResource(R.color.md_theme_surfaceContainerHigh),
        surfaceContainerHighest = colorResource(R.color.md_theme_surfaceContainerHighest)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * True when the color scheme in effect is a dark one.
 *
 * Deliberately measured from the scheme itself rather than read off [isSystemInDarkTheme]: callers use
 * this to pick a tone that will contrast with the surface they are actually drawing on, and that is a
 * property of the scheme in force — which is not always the system setting. A `@Preview` pinned to a
 * dark scheme, a white-label brand whose `md_theme_surface` override lands on the other side of the
 * line (see docs/REBRANDING.md), and any nested `MaterialTheme` all need the scheme's answer, not the
 * device's.
 */
@Composable
fun ColorScheme.isDarkTheme(): Boolean = surface.luminance() < 0.5f
