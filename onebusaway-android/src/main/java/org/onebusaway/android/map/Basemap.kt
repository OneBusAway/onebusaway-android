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
package org.onebusaway.android.map

import androidx.annotation.StringRes
import org.onebusaway.android.R

/** The rider's basemap (spec §3.9), persisted under `preference_key_basemap`; only the Google flavour draws it. */
enum class Basemap(val prefValue: String, @StringRes val labelRes: Int) {
    STANDARD("standard", R.string.map_layers_basemap_standard),
    SATELLITE("satellite", R.string.map_layers_basemap_satellite),
    HYBRID("hybrid", R.string.map_layers_basemap_hybrid)
}

fun basemapFromPref(value: String?): Basemap = Basemap.entries.firstOrNull { it.prefValue == value } ?: Basemap.STANDARD
