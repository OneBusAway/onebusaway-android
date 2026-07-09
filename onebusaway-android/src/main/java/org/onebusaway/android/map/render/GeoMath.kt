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
package org.onebusaway.android.map.render

import org.onebusaway.android.util.haversineDistance

/**
 * Great-circle distance in meters between two points. Takes flavor-neutral [GeoPoint]s and delegates
 * to the shared [haversineDistance], which matches the server's distance-along-trip values (same
 * Earth radius); neither carries an Android dependency, so callers stay pure / JVM-testable.
 */
fun haversineMeters(a: GeoPoint, b: GeoPoint): Double =
    haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
