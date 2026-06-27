/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com),
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
package org.onebusaway.android.api.adapters

import android.location.Location
import org.onebusaway.android.api.contract.StopReference
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.util.LocationUtils

/**
 * Presents a [StopReference] DTO as the [ObaStop] model interface, so consumers that work through
 * the interface — the map stop overlay, search results, the arrivals→map boundary — accept the
 * modernized fetch unchanged (the same one-DTO-implements-the-interface pattern as DtoRoute/
 * DtoTripStatus).
 */
internal class DtoStop(private val ref: StopReference) : ObaStop {
    override val id: String get() = ref.id
    override val stopCode: String? get() = ref.code
    override val name: String? get() = ref.name
    override val location: Location get() = LocationUtils.makeLocation(ref.lat, ref.lon)
    override val latitude: Double get() = ref.lat
    override val longitude: Double get() = ref.lon
    override val direction: String? get() = ref.direction
    override val locationType: Int get() = ref.locationType
    override val routeIds: Array<String> get() = ref.routeIds.toTypedArray()
}

/**
 * Object defining a Stop element. Equality is by [id] only (preserved from the original).
 */
class ObaStopElement @JvmOverloads constructor(
    override val id: String = "",
    private val lat: Double = 0.0,
    private val lon: Double = 0.0,
    override val name: String = "",
    private val code: String = "",
    override val direction: String = "",
    override val locationType: Int = ObaStop.LOCATION_STOP,
    override val routeIds: Array<String> = EMPTY_ROUTES,
) : ObaStop {

    override val stopCode: String get() = code

    override val location: Location get() = LocationUtils.makeLocation(lat, lon)

    override val latitude: Double get() = lat

    override val longitude: Double get() = lon

    override fun hashCode(): Int = 31 + id.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObaStopElement) return false
        return id == other.id
    }

    override fun toString(): String = "ObaStopElement [direction=$direction, id=$id, name=$name]"

    companion object {
        @JvmField
        val EMPTY_ROUTES = arrayOf<String>()

        @JvmField
        val EMPTY_OBJECT = ObaStopElement()

        @JvmField
        val EMPTY_ARRAY = arrayOf<ObaStopElement>()
    }
}
