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
package org.onebusaway.android.ui.tripresults

import org.onebusaway.android.directions.model.RentalFormFactor
import org.onebusaway.android.directions.model.RentalPropulsion
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripVehicleRental

/**
 * Turns a leg's [TripVehicleRental] into what the directions row actually shows about it (#2150) — who
 * the bike belongs to, what kind of vehicle it is, whether there's a dock to find it at, and where the
 * tap that says "unlock it" should send the rider.
 *
 * Pure (no `Context`, no `android.graphics`), like [ModeSymbols] and [TripLogBuilder] beside it, so
 * `RentalPickupsTest` covers the operator lookup and the link ordering directly. Strings and colours
 * are resolved by the renderer from the enums/ARGB ints this hands it.
 */

/**
 * A rented vehicle as a directions row presents it. Built only when there is something to say — see
 * [rentalPickup]; a bikeshare leg the wire told us nothing about (the OTP1 path) draws the plain bike
 * row it always did rather than an empty chip.
 */
data class RentalPickup(
    val operator: RentalOperator,
    /** The vehicle, or null when the feed didn't say — a dock publishes no type; it holds what it holds. */
    val vehicle: RentalVehicleKind?,
    /** The dock's name, when the rider is picking up from one that published a name. */
    val stationName: String?,
    /** How far the vehicle can still travel on its charge, in meters, when the feed says. */
    val rangeMeters: Int?,
    /** Where the row's action sends the rider, or null when there is nowhere to send them. */
    val link: RentalLink?,
    /**
     * Where to send them instead when nothing on the device handles [link] — the rider who was offered
     * a `lime://` deep link and hasn't got Lime installed. Null when [link] is all there is, and never
     * itself a link that could fail the same way (see [RentalLink.Deep.mayNeedTheirApp]).
     */
    val fallback: RentalLink? = null
)

/**
 * The operator of a rental network, as the row names it: [displayName] on a [brandColor] chip.
 *
 * OTP publishes neither of those — [RentalOperator.networkId] (a GBFS `system_id` like `lime_seattle`)
 * is the *only* operator fact on the wire, and GBFS 2.x has no brand fields at all. So a known
 * operator's name and colour come from [RentalOperators]' catalog, and an unknown one falls back to
 * wearing its raw network id on a neutral chip: an honest "some network called `foo_bar`" beats
 * inventing a brand for it.
 */
data class RentalOperator(
    val displayName: String,
    /**
     * ARGB, or null to let the chip take the app's neutral tint (see `RouteBadgeChip`) — which is also
     * how a caller tells a catalogued operator from one wearing its raw network id, since every catalog
     * entry states a colour.
     */
    val brandColor: Int?
)

/**
 * Where a rental row's action button goes, in the order [rentalPickup] prefers them.
 *
 * [Deep] is the only one that names the *exact* vehicle the rider was routed onto; the others merely
 * open the operator, which is why the row words them differently ("Open in Lime" vs. "Rent with
 * Lime"). Nothing here claims a reservation was made — see #2138 for why that's a separate milestone.
 */
sealed interface RentalLink {

    /**
     * The operator's own deep link to this vehicle/dock (`rentalUris.android`, else `.web`).
     *
     * [mayNeedTheirApp] tells the two apart where it matters, which is whether following the link can
     * fail: an Android URI is free to wear a scheme only the operator's own app answers (`lime://…`),
     * so a device without that app handles nothing, while `rentalUris.web` is an http(s) URL by GBFS's
     * definition and a browser always answers it. Only the former needs a [RentalPickup.fallback] —
     * and only the latter can *be* one.
     */
    data class Deep(val uri: String, val mayNeedTheirApp: Boolean) : RentalLink

    /** The operator's Android app, by package — launched if installed, else its store page. */
    data class OperatorApp(val packageName: String) : RentalLink

    /** The operator's site: `rentalNetwork.url` when the feed publishes one, else the catalog's. */
    data class Web(val url: String) : RentalLink
}

/**
 * The vehicle a rental leg is ridden on, narrowed from GBFS's form factor × propulsion to the words the
 * row can actually say. "Electric" is [RentalPropulsion.ELECTRIC] or [RentalPropulsion.ELECTRIC_ASSIST]
 * and nothing else — an explicit reading of the wire vocabulary (throttle mode and pedal-assist), not a
 * guess about what a bike with a battery is called.
 *
 * [RentalFormFactor.OTHER] deliberately has no entry: the feed is saying it *isn't* one of these, so the
 * row says nothing about the vehicle rather than picking the nearest word.
 */
enum class RentalVehicleKind { BIKE, EBIKE, CARGO_BIKE, ELECTRIC_CARGO_BIKE, SCOOTER, ESCOOTER, MOPED, CAR }

/**
 * What this leg's rental row shows, or null when the leg isn't a rental or the wire said nothing worth
 * drawing.
 *
 * Either endpoint's rental counts, and the pickup wins when both do: a docked trip starts and ends at a
 * station, a dockless one may start at a vehicle and end nowhere in particular, and it is where the
 * rider *gets* the bike that decides whether they're looking for a dock. That mirrors
 * [streetMode][org.onebusaway.android.ui.tripresults.streetMode], which reads the same two endpoints to
 * decide the leg is a rental at all, so a leg cannot be a bikeshare leg with no rental to show or the
 * other way round.
 */
internal fun TripLeg.rentalPickup(): RentalPickup? = if (streetMode() == StreetMode.BIKESHARE) {
    rentalPickup(from.rental ?: to.rental)
} else {
    // Notably the walk *to* the bike, whose `to` is the rental vehicle: the rider is told whose bike
    // it is on the row where they ride it, once, rather than on both legs that touch it.
    null
}

/**
 * The presentable form of [rental], or null when it names no network — which is the OTP1 path, whose
 * rental holds an opaque id and nothing else. Everything the row draws hangs off the operator, so a
 * chip reading "unknown network" with no link under it and no vehicle beside it would be worse than
 * the plain bike row the leg already had. OTP2 always states the network (`VehicleRentalNetwork
 * .networkId` is non-null in the pinned schema), so this drops nothing that path can produce.
 */
internal fun rentalPickup(rental: TripVehicleRental?): RentalPickup? {
    val networkId = rental?.networkId?.ifBlank { null } ?: return null
    val links = rental.links(networkId)
    return RentalPickup(
        operator = RentalOperators.of(networkId),
        vehicle = rental.vehicleKind(),
        stationName = rental.stationName,
        rangeMeters = rental.rangeMeters,
        link = links.firstOrNull(),
        // The first one that can't fail for want of an app to answer a custom scheme, and never the
        // primary itself — so a row whose only link is one of those has no fallback rather than a
        // retry of it.
        fallback = links.drop(1).firstOrNull { !(it is RentalLink.Deep && it.mayNeedTheirApp) }
    )
}

/**
 * Every action this rental offers, most specific first: the operator's deep link to this very vehicle,
 * then their app, then their site. The Android URI leads the web one because it is what the operator
 * published *for* an Android handoff (the pattern Google's micromobility deep links document) — and the
 * web one follows it rather than being dropped, since it names the same vehicle and, being an ordinary
 * URL, is the one thing that still works on a device the Android URI's scheme means nothing to. The
 * catalog's app package comes next because opening the app the rider already has beats a web page, and
 * the feed's own `rentalNetwork.url` outranks the catalog's site because it came from the operator.
 *
 * Empty when nothing at all is known — an unknown network with no URIs, which is every network the app
 * has no catalog entry for until its feed starts publishing rental URIs.
 */
private fun TripVehicleRental.links(networkId: String): List<RentalLink> {
    val known = RentalOperators.known(networkId)
    return listOfNotNull(
        androidUri?.let { RentalLink.Deep(it, mayNeedTheirApp = true) },
        webUri?.let { RentalLink.Deep(it, mayNeedTheirApp = false) },
        known?.appPackage?.let { RentalLink.OperatorApp(it) },
        networkUrl?.let { RentalLink.Web(it) },
        known?.webUrl?.let { RentalLink.Web(it) }
    )
}

/** The vehicle's kind, or null when the feed named no form factor (or named [RentalFormFactor.OTHER]). */
private fun TripVehicleRental.vehicleKind(): RentalVehicleKind? {
    val electric = propulsion == RentalPropulsion.ELECTRIC || propulsion == RentalPropulsion.ELECTRIC_ASSIST
    return when (formFactor) {
        RentalFormFactor.BICYCLE -> if (electric) RentalVehicleKind.EBIKE else RentalVehicleKind.BIKE
        RentalFormFactor.CARGO_BICYCLE ->
            if (electric) RentalVehicleKind.ELECTRIC_CARGO_BIKE else RentalVehicleKind.CARGO_BIKE
        // GBFS splits kick scooters three ways (seated/standing/the pre-3.0 catch-all); the rider is
        // renting a scooter in all three, and the seat isn't what the row is for.
        RentalFormFactor.SCOOTER, RentalFormFactor.SCOOTER_SEATED, RentalFormFactor.SCOOTER_STANDING ->
            if (electric) RentalVehicleKind.ESCOOTER else RentalVehicleKind.SCOOTER
        RentalFormFactor.MOPED -> RentalVehicleKind.MOPED
        RentalFormFactor.CAR -> RentalVehicleKind.CAR
        RentalFormFactor.OTHER, null -> null
    }
}

/**
 * The catalog of rental operators the app can name — every fact in it sourced, because none of them is
 * on the wire (see [RentalOperator]).
 *
 * Keyed by **exact** GBFS `system_id`, never by prefix: `lime_seattle` is Lime, but no rule says a
 * network id starting "lime" is, and a wrong brand on a rider's screen is worse than a plain id. That
 * means one entry per city an operator runs in, which is the honest cost of a directory that publishes
 * no brand — and adding one is a line.
 *
 * The entries below are the complete set of networks reachable from this app today: Puget Sound is the
 * only region publishing an `otpBaseGraphqlUrl` (checked against the live regions directory,
 * 2026-08-03), and its OTP serves exactly these two networks (13,395 `lime_seattle` vehicles and one
 * `bird-seattle-washington`, from the deployment's own `rentalVehicles` query the same day).
 */
object RentalOperators {

    /** A catalog entry — see [RentalOperators] for where each field comes from. */
    data class KnownOperator(
        val displayName: String,
        val brandColor: Int,
        /**
         * The operator's Android app. Must also be listed in the manifest's `<queries>` block, or
         * Android 11+ package visibility hides it from `getLaunchIntentForPackage` and the app the
         * rider has installed looks absent — see `ExternalIntents.openAppOrStoreListing`.
         */
        val appPackage: String?,
        val webUrl: String?
    )

    private val CATALOG = mapOf(
        // Name: the network's own GBFS system_information `attribution_organization_name` ("Lime").
        // Colour: sampled from Lime's Play Store icon, which is 3872 px of #00DD00 on white.
        // Package: play.google.com/store/apps/details?id=com.limebike is "Lime - #RideGreen".
        "lime_seattle" to KnownOperator("Lime", 0xFF00DD00.toInt(), "com.limebike", "https://www.li.me/"),
        // Name: shortened from the network's GBFS `operator` ("Bird Rides, Inc."), which is the legal
        // entity rather than what the app is called; its own store listing is "Bird — Ride Electric".
        // Colour: sampled from that listing's icon (#26CCF0 over 14020 px of it).
        // Package: the network's GBFS `rental_apps.android.store_uri` names co.bird.android itself.
        "bird-seattle-washington" to KnownOperator("Bird", 0xFF26CCF0.toInt(), "co.bird.android", "https://www.bird.co/")
    )

    /** The catalog entry for [networkId], or null when the app has never heard of it. */
    fun known(networkId: String): KnownOperator? = CATALOG[networkId]

    /**
     * How to present [networkId] — the catalog entry when there is one, else the id itself on a neutral
     * chip.
     */
    fun of(networkId: String): RentalOperator {
        val known = known(networkId)
        return RentalOperator(displayName = known?.displayName ?: networkId, brandColor = known?.brandColor)
    }
}
