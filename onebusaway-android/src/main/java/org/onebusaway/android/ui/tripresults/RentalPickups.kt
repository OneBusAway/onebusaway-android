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

import org.onebusaway.android.directions.model.RentalEndpointKind
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
     * itself a link that could fail the same way (see [RentalLink.mayNeedTheirApp]).
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
 * [namesTheVehicle] splits them in the way the rider can feel: [Deep] and [Synthesized] open the
 * operator's app *on the vehicle they were routed onto*, the others merely open the operator, and the
 * row words the two differently ("Open in Lime" vs. "Rent with Lime"). Nothing here claims a
 * reservation was made — see #2138 for why that's a separate milestone.
 */
sealed interface RentalLink {

    /**
     * Whether following this can find nothing on the device that handles it — a custom scheme only the
     * operator's own app answers. Those are the links that need a [RentalPickup.fallback], and the ones
     * that can never *be* one.
     */
    val mayNeedTheirApp: Boolean

    /** Whether this opens on the vehicle the rider was routed onto, rather than on the operator. */
    val namesTheVehicle: Boolean

    /**
     * The operator's own deep link to this vehicle/dock (`rentalUris.android`, else `.web`).
     *
     * [mayNeedTheirApp] tells the two apart where it matters, which is whether following the link can
     * fail: an Android URI is free to wear a scheme only the operator's own app answers (`lime://…`),
     * so a device without that app handles nothing, while `rentalUris.web` is an http(s) URL by GBFS's
     * definition and a browser always answers it. Only the former needs a [RentalPickup.fallback] —
     * and only the latter can *be* one.
     */
    data class Deep(val uri: String, override val mayNeedTheirApp: Boolean) : RentalLink {
        override val namesTheVehicle: Boolean get() = true
    }

    /**
     * The link the *app* builds to this vehicle, for the operator that publishes none (#2158) — see
     * [RentalVehicleUriTemplate] for what is known about the shape and what isn't.
     *
     * Held as components rather than a built URI for two reasons. It is assembled with `Uri.Builder`
     * (`RentalDeepLinks`), which this file — pure, JVM-tested — has no business calling; and the
     * template may stamp the moment of the tap, which a URI built when the row was drawn would get
     * wrong by however long the sheet sat open.
     */
    data class Synthesized(
        val template: RentalVehicleUriTemplate,
        /** The operator's own id for the vehicle: OTP's `network:id`, with the network stripped off. */
        val vehicleId: String
    ) : RentalLink {
        override val mayNeedTheirApp: Boolean get() = true
        override val namesTheVehicle: Boolean get() = true
    }

    /** The operator's Android app, by package — launched if installed, else its store page. */
    data class OperatorApp(val packageName: String) : RentalLink {
        override val mayNeedTheirApp: Boolean get() = false
        override val namesTheVehicle: Boolean get() = false
    }

    /** The operator's site: `rentalNetwork.url` when the feed publishes one, else the catalog's. */
    data class Web(val url: String) : RentalLink {
        override val mayNeedTheirApp: Boolean get() = false
        override val namesTheVehicle: Boolean get() = false
    }
}

/**
 * The shape of an operator's own "show me this vehicle" URI, for building one the feed didn't publish
 * (#2158). Components, never a format string: percent-encoding a query *value* does not escape the `&`
 * and `=` that separate it from the next one, so a template interpolated by hand is a URI the operator's
 * app reads as extra parameters. `Uri.Builder` cannot express that mistake.
 *
 * Only an operator whose *Android* URI shape is sourced gets one — see [RentalOperators] for each
 * operator's evidence, and for the one step of it that is still an assumption.
 */
data class RentalVehicleUriTemplate(
    val scheme: String,
    val host: String,
    /** The query parameter naming the vehicle. */
    val vehicleIdParam: String,
    /**
     * The query parameter carrying when the link was built, in epoch **seconds**, or null for an
     * operator whose links carry no such stamp. Its presence is what makes the URI worth re-building at
     * the moment of the tap: a stamp minted when the row was drawn is stale by the time a rider who
     * left the sheet open comes back to it, and it is the operator's app that decides what stale means.
     */
    val timestampParam: String?
)

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
        fallback = links.drop(1).firstOrNull { !it.mayNeedTheirApp }
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
 * The link the app builds itself (#2158) sits between them: below both feed-published URIs, which came
 * from the operator and say what the operator meant, and above the plain app launch, which drops the
 * rider on a map they must find their own bike on again. That is the whole of its worth — it is the app
 * launch, aimed. What that rank costs when the aim is *off* is not nothing, which is the open question
 * on this: see [RentalOperators.LIME_VEHICLE_URI] for what the operator's app did with an id it didn't
 * recognise.
 *
 * Empty when nothing at all is known — an unknown network with no URIs, which is every network the app
 * has no catalog entry for until its feed starts publishing rental URIs.
 */
private fun TripVehicleRental.links(networkId: String): List<RentalLink> {
    val known = RentalOperators.known(networkId)
    return listOfNotNull(
        androidUri?.let { RentalLink.Deep(it, mayNeedTheirApp = true) },
        webUri?.let { RentalLink.Deep(it, mayNeedTheirApp = false) },
        synthesizedLink(known),
        known?.appPackage?.let { RentalLink.OperatorApp(it) },
        networkUrl?.let { RentalLink.Web(it) },
        known?.webUrl?.let { RentalLink.Web(it) }
    )
}

/**
 * The link the app builds to this vehicle from [known]'s URI shape, or null when it can't build one
 * that means anything: an operator with no sourced shape, an endpoint that is a **dock** (whose [id] is
 * a station id, which no `selected_vehicle_id` will ever match), or an id with nothing left after the
 * network is stripped off it.
 *
 * Each of those is a fall-through, never a substitution — the rental's remaining links are offered in
 * their own right, so the worst case is the behaviour that shipped in #2156.
 */
private fun TripVehicleRental.synthesizedLink(known: RentalOperators.KnownOperator?): RentalLink.Synthesized? {
    val template = known?.vehicleUri ?: return null
    if (kind != RentalEndpointKind.VEHICLE) return null
    return rawVehicleId()?.let { RentalLink.Synthesized(template, it) }
}

/**
 * The operator's own id for this vehicle: OTP qualifies it as `network:id`, and the operator's links
 * name the bare GBFS id. Everything through the **first** colon goes, which is the join OTP made — the
 * feed's own id is free to contain more of them. An id with no colon at all is passed through as it
 * stands rather than emptied.
 *
 * Null when nothing is left, so the caller drops to a link that needs no id instead of building one
 * with an empty parameter.
 */
private fun TripVehicleRental.rawVehicleId(): String? = id?.substringAfter(':')?.ifBlank { null }

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
 *
 * The brand colours — the one fact here with no published source at all, sampled from each operator's
 * own store icon — were signed off on 2026-08-03 (#2156) rather than dropped for a neutral chip, since
 * naming the operator a rider can *recognise* is what #2150 is for. They are meant to be temporary in
 * this form: the intended home is a brand registry shared across the OBA implementations, at which
 * point [KnownOperator.brandColor] becomes a lookup into it rather than a hex kept here. (The sibling
 * iOS app made the other choice and paints every rental surface one app-owned purple — worth knowing
 * before anyone re-opens this.)
 *
 * [KnownOperator.vehicleUri] is the other fact here that isn't simply looked up (#2158) — the URI shape
 * that lets a row open the operator's app *on the vehicle*, for the operator that publishes no such link
 * itself. Each entry states its evidence; [LIME_VEHICLE_URI] states the step of it that a maintainer has
 * to accept rather than check.
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
        val webUrl: String?,
        /**
         * How this operator's app is asked to show one vehicle, when the feed publishes no link of its
         * own (#2158) — null for an operator whose Android URI shape isn't sourced, or that has no such
         * link to build. Sourced per entry below; the rule is that an unsourced shape means **no
         * synthesis**, never a plausible-looking guess.
         */
        val vehicleUri: RentalVehicleUriTemplate? = null
    )

    /**
     * Lime's "show me this vehicle" URI, in Lime's own words.
     *
     * Not reverse-engineered: Lime *publishes* this shape, in the GBFS feeds of the cities where it
     * turns rental URIs on. On 2026-08-03 every one of the 7,097 vehicles in
     * `data.lime.bike/api/partners/v1/gbfs/washington_dc/free_bike_status` carried
     * `rental_uris.android` = `limebike://map?selected_vehicle_id=<id>&generated_at=<seconds>`, byte for
     * byte the same as its `.ios` sibling — which is what settles the two things a synthesized link
     * would otherwise be guessing at. The scheme is not an iOS-only scheme (Lime ships one app URI for
     * both platforms, and publishes it under the `android` key itself), and `generated_at` is epoch
     * **seconds**, read off the published values rather than inferred from their magnitude: they track
     * the same feed's `last_updated`, which GBFS defines as epoch seconds. Seattle is simply a city
     * where Lime has not turned the field on — the same feed there publishes no `rental_uris` at all,
     * for any of its 13k vehicles, which is the hole this fills.
     *
     * 🚩 **The step that is not sourced, and the reason this is a human sign-off gate — now with a
     * device result against it.** The id. Lime's published links carry a short code (`IBO2JSMUXZVUQ`),
     * *not* the GBFS `bike_id` beside it in the same record (a UUID) — and a UUID is all OTP hands us,
     * since `vehicleId` is that `bike_id` under its network prefix. So the app fills
     * `selected_vehicle_id` from the id space the feed identifies vehicles by, which is demonstrably not
     * the one Lime's own links use.
     *
     * Tapped on a Pixel 7 Pro with Lime installed (2026-08-03), a link built this way produced Lime's
     * **"Resource not found"** error screen — not its map. So the graceful-failure argument this rests
     * on (`ExternalIntents.openFeedUri` reporting false, the caller falling back) does not cover the
     * case that actually happens: Lime *claims* the scheme, accepts the URI, and fails inside its own
     * app, where the fallback chain can no longer see it. On that evidence the rider is left worse off
     * than the plain app launch `RentalLink.OperatorApp` gives them, which is what a maintainer signing
     * this off has to weigh. The same URI is what the sibling iOS app emits — its own tests assert only
     * that the URL is well-formed, so it has never been shown to select a vehicle either (#2158).
     */
    private val LIME_VEHICLE_URI = RentalVehicleUriTemplate(
        scheme = "limebike",
        host = "map",
        vehicleIdParam = "selected_vehicle_id",
        timestampParam = "generated_at"
    )

    private val CATALOG = mapOf(
        // Name: the network's own GBFS system_information `attribution_organization_name` ("Lime").
        // Colour: sampled from Lime's Play Store icon, which is 3872 px of #00DD00 on white.
        // Package: play.google.com/store/apps/details?id=com.limebike is "Lime - #RideGreen".
        // Vehicle URI: Lime's own, copied off a Lime feed that publishes them — see LIME_VEHICLE_URI.
        "lime_seattle" to KnownOperator(
            displayName = "Lime",
            brandColor = 0xFF00DD00.toInt(),
            appPackage = "com.limebike",
            webUrl = "https://www.li.me/",
            vehicleUri = LIME_VEHICLE_URI
        ),
        // Name: shortened from the network's GBFS `operator` ("Bird Rides, Inc."), which is the legal
        // entity rather than what the app is called; its own store listing is "Bird — Ride Electric".
        // Colour: sampled from that listing's icon (#26CCF0 over 14020 px of it).
        // Package: the network's GBFS `rental_apps.android.store_uri` names co.bird.android itself.
        //
        // No vehicle URI, on the operator's own evidence rather than for want of looking: Bird's
        // `rental_apps` publishes an Android `discovery_uri` of `bird://charger-onboarding` — a link for
        // people who *charge* scooters, not people who ride one — and nothing that takes a vehicle id.
        // (The sibling iOS app carries a `bird://` app-launch entry, which is its iOS `discovery_uri`.
        // Android has no use for one: an app launch is exactly what `appPackage` above does, and it does
        // it better, since a rider without the app lands on the Play listing instead of nowhere.)
        "bird-seattle-washington" to KnownOperator(
            displayName = "Bird",
            brandColor = 0xFF26CCF0.toInt(),
            appPackage = "co.bird.android",
            webUrl = "https://www.bird.co/"
        )
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
