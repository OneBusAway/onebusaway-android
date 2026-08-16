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
package org.onebusaway.android.ui.nav

import android.content.Intent
import java.net.URI
import java.net.URLDecoder

/**
 * The one place that knows how *another app* names a place to this one (#1936) — the vocabulary behind
 * the `geo:` and `ACTION_SEND` intent-filters on `HomeActivity`. It is what replaced the trip planner's
 * in-app address-book picker: rather than reading the rider's contacts ourselves, we accept the place
 * their address book (or maps app, or browser) already knows how to hand out.
 *
 * Two ways in, and the difference between them is deliberate:
 *
 *  1. **`ACTION_VIEW` on a `geo:` URI** — the platform's standard "open this location", which Contacts
 *     emits for a postal address and every maps app understands. Claiming this scheme puts OneBusAway in
 *     the same chooser as the map apps, which is exactly where a rider looking for transit directions
 *     wants to find it.
 *  2. **`ACTION_SEND` of `text/plain`** — the share sheet. Shared text may be a maps link ([MAPS_HOSTS])
 *     or just an address.
 *
 * `https` maps links are deliberately *not* claimed as `ACTION_VIEW`: doing so would put this app in the
 * chooser for every Google Maps URL on the device, which is not an offer a transit app should be making.
 * Through the share sheet the rider has already said they meant us, so the same URLs are read there.
 *
 * [parse] is pure — [PlaceRequest] is an already-projected intent with no Android dependency — so the
 * whole vocabulary is JVM-unit-testable ([org.onebusaway.android.ui.nav.PlaceIntentsTest]); [read] is the
 * thin Android-facing projection, and `HomeActivity.maybePlanToPlaceFromIntent` is the one consumer.
 *
 * Routing-wise these intents resolve to nothing: the directions focus is home-map state rather than a
 * NavHost destination, so [IntentRouteMapper] correctly leaves them on the home/map path and the work
 * happens in the Activity's launch-intent side effects, next to the other focus changes.
 *
 * Distinct from [ExternalDeepLinks], which owns the links that name a *stop, trip or region* — OneBusAway's
 * own vocabulary, which only this app (and OneBusAway for iOS) speaks. This file speaks everyone else's.
 * `docs/DEEP_LINKING.md` documents both.
 */
object PlaceIntents {

    /** A place another app named for us. */
    sealed interface Place {
        /** An exact position, plus whatever the sender called it (null when it named it nothing). */
        data class Point(val lat: Double, val lon: Double, val label: String? = null) : Place

        /** Text naming a place — a postal address, or a place name — for the geocoder to resolve. */
        data class Query(val text: String) : Place
    }

    /** The Android-free projection of an incoming intent that [parse] consumes; produced by [read]. */
    data class PlaceRequest(
        /** The intent's data URI as written (still percent-encoded), or null if it carries none. */
        val dataUri: String? = null,
        /** `Intent.EXTRA_TEXT` — what a `text/plain` share carries. */
        val sharedText: String? = null
    )

    /** Reads [intent] as a place another app is handing us, or null if it isn't one. */
    fun placeForIntent(intent: Intent): Place? = parse(read(intent))

    /**
     * The place [request] names, or null if it names none.
     *
     * The data URI is consulted first and the shared text second, so a share that carries both is read
     * from the machine-readable half. Either may be present without the other.
     */
    fun parse(request: PlaceRequest): Place? = request.dataUri?.let { parseUriString(it) } ?: request.sharedText?.let { parseSharedText(it) }

    /** Projects the Android [intent] into the plain [PlaceRequest] that [parse] consumes. */
    private fun read(intent: Intent): PlaceRequest = PlaceRequest(
        dataUri = intent.data?.toString(),
        // Read as a CharSequence, which is what EXTRA_TEXT is declared as: a sender that shares styled
        // text puts a Spanned here, and getStringExtra would hand back null for it.
        sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    )

    /** A URI in either family — the `geo:` scheme, or a link on one of [MAPS_HOSTS]. */
    private fun parseUriString(uri: String): Place? = if (uri.startsWith(GEO_SCHEME, ignoreCase = true)) parseGeoUri(uri) else parseMapsUrl(uri)

    // --- geo: ---------------------------------------------------------------------------------------

    /**
     * `geo:` — the platform's "open this location" URI, in the forms Android documents
     * ([common intents](https://developer.android.com/guide/components/intents-common#Maps)) plus
     * RFC 5870's `;`-separated parameters:
     *
     * ```text
     * geo:47.6097,-122.3422            an exact point
     * geo:47.6097,-122.3422;u=35       …with RFC 5870 uncertainty (ignored)
     * geo:47.6097,-122.3422?z=17       …with a zoom (ignored — we frame the trip, not the map)
     * geo:0,0?q=1600+Amphitheatre+Pkwy an address to geocode
     * geo:0,0?q=47.6,-122.3(Pike+Place+Market)   a labelled point
     * ```
     *
     * `q` wins over the URI's own coordinate when it *is* one, and otherwise labels it — see
     * [GEO_PLACEHOLDER] for the one case where a coordinate is present and still not a position.
     */
    private fun parseGeoUri(uri: String): Place? {
        val schemeSpecificPart = uri.substring(GEO_SCHEME.length)
        val uriPoint = parseCoordinates(schemeSpecificPart.substringBefore('?'))
        val query = formParams(schemeSpecificPart.substringAfter('?', "")).nonBlank(PARAM_Q)
            // No `q` to fall back on, so a URI that is nothing but the placeholder names nothing at all.
            ?: return uriPoint?.takeIf { it != GEO_PLACEHOLDER }
        // `q` may itself be a coordinate, optionally labelled.
        labelledCoordinates(query)?.let { return it }
        // Otherwise it is text, and it only *labels* the URI when the URI carries a real position.
        return if (uriPoint == null || uriPoint == GEO_PLACEHOLDER) Place.Query(query) else uriPoint.copy(label = query)
    }

    // --- maps links ---------------------------------------------------------------------------------

    /**
     * A shared link on one of [MAPS_HOSTS], read for the place it points at. An unrecognized host, or one
     * whose link says nothing we can read, returns null and the shared text falls back to its prose (see
     * [parseSharedText]) — which is how a Maps **short link** (`maps.app.goo.gl`, `goo.gl/maps`) is
     * handled. Those name their place only to whoever resolves the redirect, and a network round trip to
     * expand somebody else's URL is not a thing this app should be doing on a share; the place name Maps
     * shares alongside the link is the better source anyway.
     */
    private fun parseMapsUrl(url: String): Place? {
        val link = parseUrl(url) ?: return null
        if (link.host !in MAPS_HOSTS) return null
        osmMarker(link.params)?.let { return it }
        googlePlacePath(link.pathSegments)?.let { return it }
        val values = PLACE_PARAMS.mapNotNull { link.params.nonBlank(it) }
        // An exact coordinate beats text wherever it is spelled, because it needs no geocoder and can't
        // resolve to the wrong place: `maps.apple.com/?q=Home&ll=47.6,-122.3` means that position, named
        // Home. Text is the answer only when no parameter carries a position.
        val text = values.firstOrNull { labelledCoordinates(it) == null }
        values.firstNotNullOfOrNull { labelledCoordinates(it) }?.let { return it.copy(label = it.label ?: text) }
        return text?.let { Place.Query(it) }
    }

    /** `…/maps/place/<Name>/@<lat>,<lng>,<zoom>z/…` — how a Google Maps place page is copied out. */
    private fun googlePlacePath(pathSegments: List<String>): Place.Point? {
        val at = pathSegments.firstOrNull { it.startsWith(GOOGLE_AT_PREFIX) } ?: return null
        val point = parseCoordinates(at.removePrefix(GOOGLE_AT_PREFIX)) ?: return null
        val placeIndex = pathSegments.indexOf(GOOGLE_PLACE_SEGMENT)
        val name = if (placeIndex < 0) {
            null
        } else {
            pathSegments.getOrNull(placeIndex + 1)?.takeIf { it.isNotBlank() && !it.startsWith(GOOGLE_AT_PREFIX) }
        }
        return point.copy(label = name)
    }

    /** `openstreetmap.org/?mlat=…&mlon=…` — OSM spells its marker as a coordinate pair, not one value. */
    private fun osmMarker(params: Map<String, String>): Place.Point? {
        val lat = params.nonBlank(PARAM_OSM_LAT) ?: return null
        val lon = params.nonBlank(PARAM_OSM_LON) ?: return null
        return parseCoordinates("$lat,$lon")
    }

    // --- shared text --------------------------------------------------------------------------------

    /**
     * Text shared to the app: any URI in it that we can read wins, and otherwise the prose around them is
     * the query.
     *
     * Dropping the URIs from that query rather than geocoding the raw text matters — Google Maps shares a
     * place as `"Pike Place Market\nhttps://maps.app.goo.gl/…"`, and the link half is not a place name.
     */
    private fun parseSharedText(text: String): Place? {
        URI_TOKEN.findAll(text).firstNotNullOfOrNull { parseUriString(it.value) }?.let { return it }
        val prose = URI_TOKEN.replace(text, " ").split(WHITESPACE).filter { it.isNotBlank() }
        return prose.takeIf { it.isNotEmpty() }?.let { Place.Query(it.joinToString(" ")) }
    }

    // --- shared parsing -----------------------------------------------------------------------------

    /**
     * `<lat>,<lng>` — the one coordinate spelling all of the above share. A trailing third component
     * (RFC 5870's altitude, Google's `17z` zoom) and RFC 5870's `;`-separated parameters are accepted and
     * dropped; anything that isn't two numbers inside the coordinate ranges is not a coordinate.
     */
    private fun parseCoordinates(text: String): Place.Point? {
        val parts = text.substringBefore(';').split(',')
        if (parts.size !in 2..3) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Place.Point(lat, lon)
    }

    /** `<lat>,<lng>(<label>)` — [parseCoordinates] plus `geo:`'s optional parenthesised label. */
    private fun labelledCoordinates(value: String): Place.Point? {
        val open = value.indexOf('(')
        if (open < 0) return parseCoordinates(value)
        val label = value.substring(open + 1).substringBeforeLast(')').trim()
        return parseCoordinates(value.substring(0, open))?.copy(label = label.takeIf { it.isNotBlank() })
    }

    /** An `http`/`https` URL decomposed into the parts [parseMapsUrl] reads, or null if it isn't one. */
    private fun parseUrl(url: String): Link? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in WEB_SCHEMES) return null
        return Link(
            host = uri.host?.lowercase(),
            pathSegments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.map(::formDecode),
            params = formParams(uri.rawQuery.orEmpty())
        )
    }

    /** An already-decomposed maps link. */
    private data class Link(
        val host: String?,
        val pathSegments: List<String>,
        val params: Map<String, String>
    )

    /** `a=1&b=2` → the decoded pairs. A repeated name keeps its last value, as a server would read it. */
    private fun formParams(query: String): Map<String, String> = query
        .split('&')
        .filter { it.isNotEmpty() }
        .associate { formDecode(it.substringBefore('=')) to formDecode(it.substringAfter('=', "")) }

    /**
     * Decodes an `application/x-www-form-urlencoded` value — the encoding these URIs are documented to
     * use, `+`-for-space included (Android's own example is
     * `geo:0,0?q=1600+Amphitheatre+Parkway%2C+Mountain+View%2C+CA`).
     *
     * A malformed escape decodes to itself rather than throwing: the value is a place name on its way to
     * a geocoder, and a stray `%` in one is no reason to drop the whole intent.
     */
    private fun formDecode(value: String): String = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)

    /** A parameter present with no value reads as `""` — treat it as absent, as [ExternalDeepLinks] does. */
    private fun Map<String, String>.nonBlank(name: String): String? = this[name]?.takeIf { it.isNotBlank() }

    // --- vocabulary ---------------------------------------------------------------------------------

    private const val GEO_SCHEME = "geo:"

    private val WEB_SCHEMES = setOf("http", "https")

    /**
     * `geo:0,0` — the platform's documented spelling of "this URI names its place in `q`, not in its own
     * coordinate": every `?q=` form in Android's common-intents documentation is written `geo:0,0?q=…`,
     * and Contacts emits exactly that for a postal address.
     *
     * Read as the sentinel it is, rather than as a position off West Africa. The alternative — letting the
     * coordinate always win — would geocode nothing but would also send every address-book address to the
     * middle of the Atlantic; letting `q` always win would instead discard a real coordinate whenever a
     * sender supplies both, which the labelled forms above do.
     */
    private val GEO_PLACEHOLDER = Place.Point(0.0, 0.0)

    /**
     * The maps hosts whose shared links are read. Deliberately a short, exact list rather than a pattern:
     * a link this app can't read costs the rider nothing (its prose is geocoded instead), while guessing
     * at an unfamiliar host's parameters would send them somewhere wrong.
     *
     * Google's country domains (`maps.google.co.uk`, …) are not enumerated for the same reason the
     * short-link hosts aren't handled — see [parseMapsUrl].
     */
    private val MAPS_HOSTS = setOf(
        "maps.google.com",
        "www.google.com",
        "google.com",
        "maps.apple.com",
        "www.openstreetmap.org",
        "openstreetmap.org"
    )

    /**
     * Every query parameter across [MAPS_HOSTS] that names a place, in the order a tie between two text
     * values is broken: Google's classic `q`/`daddr` and its `api=1` `query`/`destination`, and Apple's
     * `q`/`daddr`/`address`/`ll`. A trip needs the *destination*, so where a link names both ends (the
     * directions forms) only the far end is listed — `origin`/`saddr`/`sll` are read by nobody here.
     */
    private val PLACE_PARAMS = listOf("destination", "daddr", "q", "query", "address", "ll")

    private const val PARAM_Q = "q"
    private const val PARAM_OSM_LAT = "mlat"
    private const val PARAM_OSM_LON = "mlon"

    private const val GOOGLE_PLACE_SEGMENT = "place"
    private const val GOOGLE_AT_PREFIX = "@"

    /** A URI as it appears inside shared prose: a scheme we read, then everything up to whitespace. */
    private val URI_TOKEN = Regex("""(?:https?://|geo:)\S+""", RegexOption.IGNORE_CASE)

    private val WHITESPACE = Regex("""\s+""")
}
