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
package org.onebusaway.android.map.googlemapsv2

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Adds a marker to a live map, failing loudly if the map has already been torn down.
 *
 * [GoogleMap.addMarker] is declared nullable for exactly one reason: it returns null once the
 * underlying native map has been released. Every renderer call site here runs while the map is
 * attached, so a null means the renderer outlived its map — a lifecycle bug in *our* code, not a
 * transient condition to fall back from. Failing here names that invariant instead of surfacing an
 * unattributed `NullPointerException` from whichever of the ten call sites happened to run first.
 */
internal fun GoogleMap.addMarkerOrFail(options: MarkerOptions): Marker = checkNotNull(addMarker(options)) {
    "GoogleMap.addMarker returned null — the map was released while the renderer was still active"
}
