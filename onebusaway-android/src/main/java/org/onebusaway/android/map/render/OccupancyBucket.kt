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

import androidx.annotation.StringRes
import org.onebusaway.android.R
import org.onebusaway.android.models.Occupancy

/**
 * How full a vehicle is, at the resolution its marker can actually show (#2194): the seven GTFS-realtime
 * levels collapsed to a coarse empty/some/most/full scale, since seven can't be told apart at map scale.
 *
 * This is the shared vocabulary for the two things that report crowding — the tab's pips
 * ([VehicleBitmaps]) and the marker's accessible name ([vehicleTitle]) — so both read it from one place
 * and cannot drift. Each bucket carries **both** its drawn form ([pips]) and its spoken one ([labelRes]),
 * rather than the title deriving words from a pip count: that made the words depend on how many pips
 * happen to fit, so adding a bucket or a fourth pip would have silently left a level the marker draws
 * with nothing to say about it.
 *
 * **Absence is not a bucket.** A vehicle that reports no occupancy maps to `null`, not to [EMPTY] — "we
 * have no fullness data" and "we do, and it's empty" are different facts, and the marker states them
 * differently: no tab at all versus a tab whose pips are all empty. Keeping that distinction in the type
 * is what stopped an unequipped fleet from looking like an empty one.
 */
internal enum class OccupancyBucket(val pips: Int, @StringRes val labelRes: Int) {
    EMPTY(0, R.string.realtime_empty),
    MANY_SEATS(1, R.string.realtime_many_seats_available),
    STANDING_ROOM(2, R.string.realtime_standing_room),
    FULL(3, R.string.realtime_full);

    companion object {
        /**
         * The bucket [occupancy] falls in, or null when it reports nothing.
         *
         * Exhaustive over the wire enum on purpose — no `else` — so a new GTFS-realtime level is a
         * compile error here rather than something that quietly lands in the wrong bucket.
         */
        fun of(occupancy: Occupancy?): OccupancyBucket? = when (occupancy) {
            null -> null
            Occupancy.EMPTY -> EMPTY
            Occupancy.MANY_SEATS_AVAILABLE -> MANY_SEATS
            Occupancy.FEW_SEATS_AVAILABLE, Occupancy.STANDING_ROOM_ONLY -> STANDING_ROOM
            Occupancy.CRUSHED_STANDING_ROOM_ONLY, Occupancy.FULL, Occupancy.NOT_ACCEPTING_PASSENGERS -> FULL
        }
    }
}
