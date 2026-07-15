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

import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.Marker
import org.onebusaway.android.map.render.StopMarker

/** Renderer-independent boundary for interchangeable Google route-stop drawing strategies. */
internal interface GoogleRouteStopLayer {
    fun render(stops: List<StopMarker>, focusedStopId: String?, zoom: Float)

    fun onCameraMoveStarted() = Unit

    fun onCameraSettled(zoom: Float)

    fun stopForMarker(marker: Marker): StopMarker? = null

    fun stopForCircle(circle: Circle): StopMarker? = null

    fun dispose()
}
