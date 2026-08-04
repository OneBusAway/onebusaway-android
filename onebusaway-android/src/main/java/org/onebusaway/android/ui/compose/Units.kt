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
package org.onebusaway.android.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import org.onebusaway.android.util.PreferenceUtils

/**
 * The metric/imperial choice already resolved for this subtree, or null where nothing has resolved it
 * yet.
 *
 * Two kinds of host provide it. A **screen** provides it once at its root so the distance rows beneath
 * it read a value instead of each re-resolving one — see the provider in `TripResultsList`. A
 * **preview** provides it because the preference read behind [unitsAreMetric] goes through
 * `PreferenceUtils` to `Application.get()`, which throws under layoutlib, so a preview of any screen
 * that formats a distance dies without it.
 *
 * `static`, because the value can only change when the rider edits Settings: reads become plain lookups
 * that register no snapshot observer, which matters when one is done per row of a long list.
 */
val LocalUnitsAreMetric = staticCompositionLocalOf<Boolean?> { null }

/**
 * Whether to render distances in metric units: [LocalUnitsAreMetric] where a host has resolved it,
 * otherwise the rider's preference.
 *
 * Resolve this **once, at the composable that owns the screen**, provide it back through
 * [LocalUnitsAreMetric], and pass the result down to
 * [org.onebusaway.android.directions.util.ConversionUtils.getFormattedDistanceParts] — the formatter
 * deliberately takes the choice as a parameter rather than reading it (see its KDoc). Calling this from
 * a row is then just a local read; calling it from a row *without* a provider above means the
 * preference — a Hilt entry-point hop, three string lookups and a locale query — per row that enters
 * composition.
 */
@Composable
fun unitsAreMetric(): Boolean {
    val override = LocalUnitsAreMetric.current
    val context = LocalContext.current
    return override ?: remember(context) { PreferenceUtils.getUnitsAreMetricFromPreferences(context) }
}
