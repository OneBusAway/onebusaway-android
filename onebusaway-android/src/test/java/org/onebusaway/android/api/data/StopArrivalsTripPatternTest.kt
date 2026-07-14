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
package org.onebusaway.android.api.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.References
import org.onebusaway.android.api.contract.RouteReference
import org.onebusaway.android.api.contract.TripReference
import org.onebusaway.android.models.TripPatternGeometry

class StopArrivalsTripPatternTest {

    @Test
    fun `trip patterns include only shapes belonging to the supplied stop trips`() {
        val snapshot = StopArrivals(
            data = EntryWithReferences(
                entry = ArrivalsForStop(stopId = "stop"),
                references = References(
                    routes = listOf(RouteReference(id = "route")),
                    trips = listOf(
                        TripReference(id = "served-1", routeId = "route", shapeId = "served-shape"),
                        TripReference(id = "served-2", routeId = "route", shapeId = "served-shape"),
                        TripReference(id = "other-branch", routeId = "route", shapeId = "other-shape"),
                        TripReference(id = "missing-shape", routeId = "route"),
                    ),
                ),
            ),
            currentTime = 0L,
            minutesAfter = 65,
        )

        val patterns = snapshot.tripPatternGeometries(
            listOf("served-1", "served-2", "missing-shape", "unknown-trip")
        )

        assertEquals(
            setOf(TripPatternGeometry("served-shape", "route", null)),
            patterns,
        )
    }
}
