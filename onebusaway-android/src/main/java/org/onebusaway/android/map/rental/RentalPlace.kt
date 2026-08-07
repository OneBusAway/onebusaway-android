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

import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion

/**
 * One rentable thing on the map — a parked free-floating vehicle, or a dock full of them (#2168).
 *
 * Replaces the OTP1-shaped `BikeStation`, which modelled everything as a docked bike station and
 * therefore drew a parked scooter as a dock offering "bikes available / spaces available". OTP1
 * signalled the difference with a single `isFloatingBike` flag that had no consumers at all; this
 * model makes the two shapes a [kind] the renderer and the detail window both read.
 *
 * Minted at the wire boundary in `api/adapters/RentalPlaceAdapters.kt` — from OTP2's
 * `vehicleRentalsByBbox` union (the rich path) or from OTP1's `/bike_rental` stations (the fallback
 * for regions with no OTP2 endpoint). Consumers never see either wire shape.
 *
 * **Every field OTP1 cannot state is nullable, and null means "the feed didn't say"** — never a
 * substituted default. That distinction is load-bearing for the range filter, which must fail *open*
 * on a vehicle whose feed omits its range rather than silently hiding it (see [matchesMinimumRange]).
 *
 * All fields but the identifying ones default, so a test can build a fixture tersely; production code
 * always arrives through an adapter.
 */
data class RentalPlace(
    val id: String,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val kind: RentalKind = RentalKind.STATION,
    /** The GBFS `system_id` naming the operator, via OTP's `rentalNetwork.networkId`. Null on OTP1. */
    val networkId: String? = null,
    /** The operator's own site, from `rentalNetwork.url`. */
    val networkUrl: String? = null,
    /** `rentalUris.android` — an operator deep link that may name a scheme only their app answers. */
    val androidUri: String? = null,
    /** `rentalUris.web` — an http(s) URL by GBFS's definition, so any browser answers it. */
    val webUri: String? = null,
    /**
     * What kind of vehicle this is — stated per-vehicle by OTP2, and for a station derived from the
     * types its `availableVehicles` are counted under, so a dock of scooters is one.
     *
     * Empty means the feed named none: every OTP1 station, and an OTP2 station whose feed publishes
     * no per-type counts. [rentalLayersOf] decides what an empty set is drawn as.
     */
    val formFactors: Set<RentalFormFactor> = emptySet(),
    val propulsion: RentalPropulsion? = null,
    /** How far the vehicle can still travel on its charge, in metres (`fuel.range`). */
    val rangeMeters: Int? = null,
    /** Battery/fuel remaining as a 0..1 ratio (`fuel.percent`). */
    val fuelPercent: Double? = null,
    /** A station's occupancy: vehicles to take, and docks to leave one in. */
    val vehiclesAvailableCount: Int? = null,
    val docksAvailableCount: Int? = null,
    /** Whether a vehicle can be taken / returned here *right now*, when the feed says. */
    val allowPickupNow: Boolean? = null,
    val allowDropoffNow: Boolean? = null,
    /** False when the operator has taken this vehicle or station out of service. */
    val operative: Boolean? = null,
    /** Whether the counts above came from a realtime source rather than nominal capacity. */
    val realTimeData: Boolean = false
) {
    /** True when the operator has explicitly taken this out of service — never merely "didn't say". */
    val isOutOfService: Boolean get() = operative == false
}

/** The two shapes a rental place comes in — the distinction OTP1's `isFloatingBike` never reached. */
enum class RentalKind {
    /** A free-floating vehicle parked somewhere: one vehicle, no dock. */
    VEHICLE,

    /** A docking station: a count of vehicles to take and of spaces to leave one in. */
    STATION
}

/**
 * The two map layers the rental fetch feeds. Bikes and scooters are separate toggles over **one**
 * request (see `RentalLayerController`), so a rider who wants both pays for one fetch.
 *
 * Bikes ships on and scooters off, copying the sibling iOS app's defaults *and its reasoning*:
 * scooters are the large majority of a dockless fleet — 74% of the supply in iOS's launch region —
 * so defaulting them on buries the transit map the app is actually for under them.
 */
enum class RentalLayer { BIKES, SCOOTERS }

/**
 * Which layer(s) [place] belongs to.
 *
 * Bicycle-family form factors are bikes and kick-scooter/moped-family ones are scooters, straight off
 * the GBFS vocabulary. Two cases are deliberate policy rather than a reading of the data:
 *
 *  - **A place stating no form factor at all draws on the bikes layer.** That is every OTP1 station —
 *    fetched from an endpoint literally named `bike_rental`, from a protocol with no form-factor
 *    concept — and an OTP2 dock whose feed publishes no per-type counts. Putting them on bikes is not
 *    a guess about what is in the dock; it is the only layer the OTP1 path has ever had, so the
 *    fallback path draws exactly what it drew before.
 *  - **A vehicle stating a form factor the app has no layer for draws on neither.** A rental *car* or
 *    an `OTHER` is not a bike and not a scooter, and the app offers no third toggle; showing it under
 *    one of these two would tell the rider they had found something they hadn't.
 */
fun rentalLayersOf(place: RentalPlace): Set<RentalLayer> {
    if (place.formFactors.isEmpty()) return setOf(RentalLayer.BIKES)
    return place.formFactors.mapNotNullTo(mutableSetOf()) { it.rentalLayer() }
}

private fun RentalFormFactor.rentalLayer(): RentalLayer? = when (this) {
    RentalFormFactor.BICYCLE, RentalFormFactor.CARGO_BICYCLE -> RentalLayer.BIKES
    // GBFS splits kick scooters three ways (seated / standing / the pre-3.0 catch-all) and a moped is
    // the same "small thing you unlock at the kerb" to a rider scanning the map for one.
    RentalFormFactor.SCOOTER, RentalFormFactor.SCOOTER_SEATED, RentalFormFactor.SCOOTER_STANDING,
    RentalFormFactor.MOPED -> RentalLayer.SCOOTERS

    RentalFormFactor.CAR, RentalFormFactor.OTHER -> null
}

/**
 * Whether [place] survives a minimum-range filter of [minimumRangeMeters] (null = no filter).
 *
 * **Fails open, on purpose.** A docked station has no single range to speak of, a pedal bike has no
 * charge to run out of, and plenty of feeds simply omit `fuel.range`; filtering those out would make
 * a range preset quietly delete most of the map rather than narrow it. Only a vehicle that *states* a
 * range short of the threshold is hidden — the one case where the rider asked a question the data
 * actually answers.
 */
fun matchesMinimumRange(place: RentalPlace, minimumRangeMeters: Int?): Boolean {
    if (minimumRangeMeters == null) return true
    val range = place.rangeMeters ?: return true
    return range >= minimumRangeMeters
}
