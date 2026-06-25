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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import org.onebusaway.android.R

/**
 * Material 3 theme for Compose screens, built from the same md_theme_* color resources that back
 * the XML Theme.OneBusAway themes.
 *
 * Colors are read via [colorResource] instead of being duplicated as Kotlin constants so there is
 * a single source of truth: night mode switches automatically with the resource configuration,
 * and white-label brand flavors that override colors.xml (see REBRANDING.md) are respected
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
