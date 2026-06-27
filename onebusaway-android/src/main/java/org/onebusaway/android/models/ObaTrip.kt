/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.models

/**
 * Interface for a Trip element.
 */
interface ObaTrip : ObaElement {

    /** The short name for the trip. */
    val shortName: String?

    /** The ID that defines the shape of the trip. */
    val shapeId: String?

    /** A binary value that indicates the direction of travel. */
    val directionId: Int

    /** An ID that uniquely identifies a set of dates for which the service is available. */
    val serviceId: String?

    /** The headsign for the trip. */
    val headsign: String?

    /** The timezone for the trip. */
    val timezone: String?

    /** The route ID for the trip. */
    val routeId: String

    /** The block ID for the trip. */
    val blockId: String?

    companion object {
        const val DIRECTION_OUTBOUND = 0
        const val DIRECTION_INBOUND = 1
    }
}
