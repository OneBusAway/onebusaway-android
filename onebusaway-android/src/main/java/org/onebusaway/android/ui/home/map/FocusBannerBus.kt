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
package org.onebusaway.android.ui.home.map

import androidx.annotation.ColorInt
import kotlin.time.Duration
import org.onebusaway.android.api.data.serverTimeOrNever
import org.onebusaway.android.map.render.OccupancyBucket
import org.onebusaway.android.map.render.VehicleBitmaps
import org.onebusaway.android.map.render.VehicleMarker
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.util.ScheduleDeviation

/**
 * The bus drilled into below a route — the deepest row of the focus banner's path, stop › route › bus or
 * route › bus (#2471). It is where the map says whether that bus is early, on time or late, which the
 * marker stopped saying when its disc took the route's colour (#2043) and the info bubble went (#2194).
 *
 * Built from the selected vehicle's marker as of its latest poll, so a route opened from search (which
 * has no board) still gets one; until the map's poll holds the bus, from the focused stop's arrivals
 * board instead (see [ArrivalInfo.toFocusBannerBus]).
 */
data class FocusBannerBus(
    /** The vehicle's number — its id less the OBA agency prefix — or null when blank. */
    val vehicleId: String?,
    /** Already bucketed on [ScheduleDeviation]'s band, so the banner and the listings can't disagree. */
    val deviationStatus: ScheduleDeviation.Status,
    /** The unsigned rounded deviation ([ScheduleDeviation.roundedMinutes]); read only when early or late. */
    val deviationMinutes: Long,
    /** How full the bus is; null when it reports nothing. */
    val occupancy: OccupancyBucket?,
    /** When the vehicle last reported, on the server clock; null when it never has. */
    val lastReport: ServerTime?,
    /** The poll's own server time: what the banner ticks forward from to age [lastReport]. */
    val polledAt: ServerTime
)

/**
 * The trip drilled into below a route — the path's last level whenever the focus has one, so the banner
 * never shows less of the path than the focus holds: Back drops one level at a time, and a level the
 * rider can't see would make it look like nothing happened.
 *
 * [bus] is its vehicle, or null when neither the map nor the board has one for the trip yet: an ETA pill
 * can select the next trip while its bus is still finishing the one before (#2397).
 */
data class FocusBannerTrip(
    val bus: FocusBannerBus?,
    /**
     * The trip's band colour, which marks its level of the path — the same tint as its band, its marker
     * and its ETA pill's rim (#1990). Null when there is no band to name.
     */
    @param:ColorInt val bandColor: Int?,
    /**
     * When the trip is scheduled at the focused stop, as its arrivals board lists it — the name a rider
     * knows a trip by ("the 3:12"), and what the row calls it while there is no bus to describe. Null
     * when the path has no stop, or the stop's arrivals don't list the trip.
     */
    val scheduledAtStop: ServerTime?
)

/**
 * [focusBannerBus] over primitives, so it can be tested without a marker (which needs `Location`).
 * The age is server time minus server time, both from the same poll (#1612).
 */
internal fun focusBannerBus(
    vehicleId: String?,
    isRealtime: Boolean,
    deviation: Duration,
    occupancy: OccupancyBucket?,
    lastReport: ServerTime?,
    polledAt: ServerTime
): FocusBannerBus {
    val status = ScheduleDeviation.status(isRealtime, deviation)
    return FocusBannerBus(
        // OBA ids are `<agency>_<entity>`, split at the first `_` (`AgencyAndId.convertFromString`, the same
        // contract OtpObaIdResolver keys on), so this drops exactly the agency and keeps an entity id that
        // has underscores of its own. The number is what's painted on the bus; "1_" is noise to a rider.
        vehicleId = vehicleId?.substringAfter('_')?.takeIf { it.isNotBlank() },
        deviationStatus = status,
        deviationMinutes = ScheduleDeviation.roundedMinutes(if (status == ScheduleDeviation.Status.EARLY) -deviation else deviation),
        occupancy = occupancy,
        lastReport = lastReport,
        polledAt = polledAt
    )
}

/**
 * The banner's bus row from the focused stop's arrivals board, for the beat after an ETA pill selects a
 * trip its pin says is live but before the map's own poll holds that bus: the board and the map poll on
 * separate clocks, so the board can know a bus has started a trip a poll before the map does. Without
 * this the row said "not on the map yet" about a bus the tapped pill had just called live (#2471).
 *
 * Null unless the board itself places a vehicle on this trip ([ArrivalInfo.vehicleOnMap], the pin's own
 * condition). The same wire fields the marker reads, off the arrival's trip status rather than the
 * route poll's, so the row doesn't reword itself when the marker arrives.
 */
internal fun ArrivalInfo.toFocusBannerBus(): FocusBannerBus? = if (vehicleOnMap) {
    focusBannerBus(
        vehicleId = vehicleId,
        isRealtime = predicted,
        deviation = vehicleScheduleDeviation,
        occupancy = OccupancyBucket.of(predictedOccupancy),
        lastReport = vehicleLastUpdateTime,
        polledAt = serverNow
    )
} else {
    null
}

/** The banner's bus row for the selected vehicle's marker, worded as the marker itself is drawn. */
internal fun VehicleMarker.toFocusBannerBus(): FocusBannerBus = focusBannerBus(
    vehicleId = status.vehicleId,
    // The marker's own live-or-scheduled call, so the row and the disc's grey can't disagree.
    isRealtime = isRealtime,
    deviation = status.scheduleDeviation,
    occupancy = VehicleBitmaps.occupancyBucket(this),
    lastReport = serverTimeOrNever(status.lastUpdateTime),
    polledAt = ServerTime(source.currentTimeMs)
)
