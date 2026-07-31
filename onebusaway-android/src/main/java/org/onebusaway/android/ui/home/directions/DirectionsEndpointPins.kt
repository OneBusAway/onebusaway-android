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
package org.onebusaway.android.ui.home.directions

import org.onebusaway.android.map.ItineraryPins
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.util.geoPointOrNull

// Which of a trip's two ends the map marks with a pin of its own.
//
// An end the rider set to their current location gets none, at either stage of the trip: the map's
// location layer already marks that exact point with the blue dot, and a pin dropped on top of it says
// nothing the dot doesn't (#2111). Both stages are decided in this one place so the pin the form
// withheld doesn't reappear the moment the planned trip draws.

/**
 * The point to drop an endpoint's standalone From/To pin at while the form is being filled in: null
 * while the endpoint is still free text (no coordinates yet), and null for the rider's own location.
 */
fun TripEndpoint.pinPoint(): GeoPoint? = if (isDeviceLocation) null else geoPointOrNull(lat, lon)

/**
 * The terminus pins the itinerary a plan produced should wear. Read off the request that produced the
 * results rather than the live form, which the rider may have edited since; a plan restored from a
 * notification carries no request ([TripPlanParams] is null there), so both pins are drawn.
 */
fun TripPlanParams?.itineraryPins(): ItineraryPins = ItineraryPins(
    start = this?.from?.isDeviceLocation != true,
    end = this?.to?.isDeviceLocation != true
)
