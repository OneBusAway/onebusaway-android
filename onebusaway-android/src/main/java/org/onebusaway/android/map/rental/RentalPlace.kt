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
 * substituted default, so a consumer can always tell an absent fact from a zero one: a vehicle with no
 * published range reports null rather than 0 km, which would read as a flat battery.
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
    val operative: Boolean? = null
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
 * The two kinds of rental the fetch feeds. Each decides a marker's colour and glyph, and each has its
 * own toggle under the map's master rentals button (#2168).
 *
 * **Both come out of a single `vehicleRentalsByBbox` response**, which is why the toggles cost no
 * request: flipping one filters places the app already holds (see `RentalLayerController`). Scooters
 * start off, following the sibling iOS app's default and its reasoning — they are the large majority
 * of a dockless fleet, so defaulting them on buries the transit map the app is for.
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
 * The single layer a marker is drawn as — its colour and its glyph.
 *
 * [rentalLayersOf] answers which layers a place *belongs to*, which can be more than one (a dock
 * holding both kinds) or none (a rental car). A marker has to pick exactly one, and the choice must be
 * made against what the rider actually has **enabled**: a dock of bikes and scooters shown while only
 * Scooters is on has to wear the scooter colour and glyph, or the map answers a question nobody asked.
 * Picking blind from the place's own set drew it as a bike there, because bikes sort first.
 *
 * A dock enabled under *both* has to settle on one, and it resolves in [RentalLayer] declaration
 * order — bikes first — rather than by the place's own set order. That set's order is the feed's: a
 * station's form factors come from its `availableVehicles.byType` array, so picking its first element
 * would let a poll that returns the same dock's types in the other order repaint the marker a
 * different colour with nothing about the dock having changed.
 *
 * Falls back to the place's own first layer in that same order, then to bikes, for a place enabled by
 * nothing — which `RentalLayerController` filters out before it ever gets here, so the fallback is a
 * total-function formality rather than a case the map reaches.
 */
fun rentalMarkerLayer(place: RentalPlace, enabled: Set<RentalLayer>): RentalLayer {
    val belongsTo = rentalLayersOf(place)
    return RentalLayer.entries.firstOrNull { it in belongsTo && it in enabled }
        ?: RentalLayer.entries.firstOrNull { it in belongsTo }
        ?: RentalLayer.BIKES
}
