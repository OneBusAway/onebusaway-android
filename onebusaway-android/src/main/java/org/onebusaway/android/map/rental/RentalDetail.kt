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
package org.onebusaway.android.map.rental

import org.onebusaway.android.ui.tripresults.RentalPickup
import org.onebusaway.android.ui.tripresults.RentalVehicleKind
import org.onebusaway.android.ui.tripresults.rentalPickup
import org.onebusaway.android.ui.tripresults.rentalVehicleKind

/**
 * What the rental detail window says about a marker (#2168) — resolved once, as data, so the wording
 * rules are unit-testable and the composable is only layout. The strings themselves are resolved by
 * the renderer from the enums and numbers here.
 *
 * The occupancy pair is the header case for this type: a dock says "5 available, 3 docks", a parked
 * scooter says neither, and before #2168 both said "Bikes / Spaces" because the OTP1 model had only
 * one shape.
 */
data class RentalDetail(
    val kind: RentalKind,
    /**
     * The operator, and where a tap goes — null when the feed named no network, which is the whole
     * OTP1 path (its stations carry an opaque id and nothing else). Built by the *same* function the
     * directions rows use, so the two surfaces offer the same link in the same order.
     */
    val pickup: RentalPickup?,
    /**
     * What this is, when the feed said — and, for a dock, only when its counts name exactly one type
     * (a mixed dock has no single word, and gets none). Also picks the marker's glyph, so a dock of
     * scooters draws a scooter.
     */
    val vehicle: RentalVehicleKind?,
    /** The place's own name, when it has a meaningful one — docks do, free-floating vehicles don't. */
    val title: String?,
    val vehiclesAvailableCount: Int?,
    val docksAvailableCount: Int?,
    val rangeMeters: Int?,
    /** Battery/fuel remaining as a whole percentage, when the feed said. */
    val fuelPercent: Int?,
    /** Everything the rider needs warning about, loudest first. */
    val notices: List<RentalNotice>
)

/**
 * A reason this rental may not be usable. Each is raised only from an **explicit** false on the wire —
 * a feed that simply doesn't publish `allowPickupNow` is not asserting that you can't rent the thing,
 * and telling the rider otherwise would send them past a perfectly good bike.
 */
enum class RentalNotice {
    /** `operative == false`: the operator has taken this out of service. */
    NOT_IN_SERVICE,

    /** `allowPickupNow == false`: nothing to take right now. */
    NO_PICKUP_NOW,

    /** `allowDropoffNow == false`: nowhere to leave one right now. */
    NO_DROPOFF_NOW
}

/** Resolves what the detail window shows for [place]. */
fun rentalDetailOf(place: RentalPlace): RentalDetail {
    val vehicle = rentalVehicleKind(place.formFactors.singleOrNull(), place.propulsion)
    return RentalDetail(
        kind = place.kind,
        pickup = rentalPickup(
            networkId = place.networkId,
            androidUri = place.androidUri,
            webUri = place.webUri,
            networkUrl = place.networkUrl,
            vehicle = vehicle,
            // The window is *at* the dock, so repeating its name as "pick up at …" underneath its own
            // title would say the same thing twice; the title carries it.
            stationName = null,
            rangeMeters = place.rangeMeters
        ),
        vehicle = vehicle,
        title = place.name.ifBlank { null },
        // A free-floating vehicle is one vehicle, not a dock holding one, so it reports no occupancy
        // at all rather than "1 available" (see `RentalPlaceAdapters`).
        vehiclesAvailableCount = place.vehiclesAvailableCount?.takeIf { place.kind == RentalKind.STATION },
        docksAvailableCount = place.docksAvailableCount?.takeIf { place.kind == RentalKind.STATION },
        rangeMeters = place.rangeMeters,
        // GBFS states the charge as a 0..1 ratio; a whole percentage is what a rider reads.
        fuelPercent = place.fuelPercent?.let { (it * 100).toInt().coerceIn(0, 100) },
        notices = buildList {
            // Out of service subsumes the other two — an operator that pulled the vehicle has said
            // everything there is to say about whether you can take it — so it stands alone.
            if (place.isOutOfService) {
                add(RentalNotice.NOT_IN_SERVICE)
            } else {
                if (place.allowPickupNow == false) add(RentalNotice.NO_PICKUP_NOW)
                if (place.allowDropoffNow == false) add(RentalNotice.NO_DROPOFF_NOW)
            }
        }
    )
}
