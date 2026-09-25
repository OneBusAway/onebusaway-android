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
package org.onebusaway.android.ui

import android.os.BadParcelableException
import android.os.Bundle
import org.onebusaway.android.map.MapParams

/**
 * The only launch extras consumed as activity ViewModel defaults: the map's route and camera seed.
 * Read named, typed values into a fresh Bundle instead of copying or enumerating arbitrary extras.
 * Navigation, notifications and stop focus read their own fields directly from the original Intent.
 */
internal fun homeViewModelArgs(extras: Bundle?): Bundle = Bundle().apply {
    extras.launchArg<String>(MapParams.ROUTE_ID)?.let { putString(MapParams.ROUTE_ID, it) }
    extras.launchArg<String>(MapParams.ROUTE_DIRECTION_STOP_ID)?.let { putString(MapParams.ROUTE_DIRECTION_STOP_ID, it) }
    extras.launchArg<Int>(MapParams.ROUTE_DIRECTION_ID)?.let { putInt(MapParams.ROUTE_DIRECTION_ID, it) }
    extras.launchArg<Boolean>(MapParams.ZOOM_TO_ROUTE)?.let { putBoolean(MapParams.ZOOM_TO_ROUTE, it) }
    extras.launchArg<Double>(MapParams.CENTER_LAT)?.let { putDouble(MapParams.CENTER_LAT, it) }
    extras.launchArg<Double>(MapParams.CENTER_LON)?.let { putDouble(MapParams.CENTER_LON, it) }
    extras.launchArg<Float>(MapParams.ZOOM)?.let { putFloat(MapParams.ZOOM, it) }
}

@Suppress("DEPRECATION") // Inspect the runtime type without coercing absent or mistyped fields to zero.
private inline fun <reified T> Bundle?.launchArg(key: String): T? = try {
    this?.get(key) as? T
} catch (_: BadParcelableException) {
    // A sender may also put an unreadable object under one of our known keys.
    null
}
