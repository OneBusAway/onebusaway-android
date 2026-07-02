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
package org.onebusaway.android.api.contract

import kotlinx.serialization.Serializable

/**
 * The OBA REST API envelope wrapping every `/api/where` response:
 * `{version, code, currentTime, text, data}`.
 *
 * [code] is the OBA status code (see `ObaApi.OBA_*`), **not** the HTTP status. [T] is the shape of
 * the `data` payload for a given endpoint (e.g. [EntryWithReferences]).
 *
 * This is the kotlinx.serialization-backed envelope that replaced the former hand-rolled Jackson
 * response hierarchy; every endpoint models its payload as a Kotlin data class.
 */
@Serializable
data class ObaEnvelope<T>(
    // The OBA API returns `version` as a JSON number (e.g. 2), not a string.
    val version: Int = 0,
    val code: Int = 0,
    val currentTime: Long = 0,
    val text: String = "",
    val data: T? = null,
)

/**
 * The common `data` shape for single-entry endpoints: one [entry] plus the shared [references]
 * pool that entries point into by id.
 */
@Serializable
data class EntryWithReferences<T>(
    val entry: T,
    val references: References = References(),
)

/**
 * The common `data` shape for list endpoints: a [list] of entries plus the shared [references].
 * [limitExceeded] is true when the API truncated the result to its maximum response size.
 */
@Serializable
data class ListWithReferences<T>(
    val list: List<T> = emptyList(),
    val references: References = References(),
    val limitExceeded: Boolean = false,
    // True when the query point lies outside the served region (the *-for-location endpoints).
    val outOfRange: Boolean = false,
)

/**
 * The shared reference pool returned alongside an entry. Only the reference kinds a migrated
 * endpoint actually consumes are modeled; unmodeled kinds (stops, trips, situations, routes) are
 * tolerated on the wire via `ignoreUnknownKeys` and get added as endpoints need them.
 */
@Serializable
data class References(
    val agencies: List<AgencyReference> = emptyList(),
    val stops: List<StopReference> = emptyList(),
    val routes: List<RouteReference> = emptyList(),
    val trips: List<TripReference> = emptyList(),
    val situations: List<SituationReference> = emptyList(),
) {
    // Index each pool by id (lazily, once per response) so repeated resolution — the per-arrival
    // projections and the per-frame vehicle sampler — is O(1) instead of a linear scan.
    private val agencyById by lazy { agencies.associateBy { it.id } }
    private val stopById by lazy { stops.associateBy { it.id } }
    private val routeById by lazy { routes.associateBy { it.id } }
    private val tripById by lazy { trips.associateBy { it.id } }
    private val situationById by lazy { situations.associateBy { it.id } }

    /** Resolves an agency in this pool by id, or null when absent. */
    fun agency(id: String): AgencyReference? = agencyById[id]

    /** Resolves a stop in this pool by id, or null when absent. */
    fun stop(id: String): StopReference? = stopById[id]

    /** Resolves a route in this pool by id, or null when absent. */
    fun route(id: String): RouteReference? = routeById[id]

    /** Resolves a trip in this pool by id, or null when absent. */
    fun trip(id: String): TripReference? = tripById[id]

    /** Resolves a situation in this pool by id, or null when absent. */
    fun situation(id: String): SituationReference? = situationById[id]
}

/** Wire model for a route, as it appears in an entry or the references pool. */
@Serializable
data class RouteReference(
    val id: String = "",
    val shortName: String? = null,
    val longName: String? = null,
    val description: String? = null,
    val type: Int = 0,
    val url: String? = null,
    // Raw hex strings as returned by the API (e.g. "FDB71A"), or null; parsed to an Android color by
    // the consumer that needs it (trip-details / arrivals line color).
    val color: String? = null,
    val textColor: String? = null,
    val agencyId: String = "",
)

/**
 * Wire model for a transit agency — the full agency record returned by the `agency` endpoint and
 * carried (typically with just id/name/url consumed) in the references pool of other responses.
 */
@Serializable
data class AgencyReference(
    val id: String = "",
    val name: String = "",
    val url: String? = null,
    val timezone: String? = null,
    val lang: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val fareUrl: String? = null,
    val disclaimer: String? = null,
    val privateService: Boolean = false,
)

/**
 * Wire model for an agencies-with-coverage list entry. Identifies an agency in [References] by
 * [agencyId]; the coverage geometry (lat/lon/spans) is unmodeled until a consumer needs it.
 */
@Serializable
data class AgencyCoverage(
    val agencyId: String = "",
)

/**
 * Wire model for a stop, as it appears in a list entry or the references pool. Only the fields a
 * consumer reads are modeled so far (code, locationType, routeIds, etc. are added when needed).
 */
@Serializable
data class StopReference(
    val id: String = "",
    val name: String? = null,
    val code: String? = null,
    val direction: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val locationType: Int = 0,
    val routeIds: List<String> = emptyList(),
)

/**
 * Wire model for the stops-for-route entry: the directional [stopGroupings] and (when
 * `includePolylines=true`) the route [polylines] (encoded shapes). Stops/routes/agency are resolved
 * by id from the references pool.
 */
@Serializable
data class StopsForRoute(
    val stopGroupings: List<StopGrouping> = emptyList(),
    val polylines: List<ShapeEntry> = emptyList(),
)

/** A grouping of stops (typically by direction) within a route. */
@Serializable
data class StopGrouping(
    val stopGroups: List<StopGroup> = emptyList(),
)

/** One directional group: a display [name] and the ordered [stopIds] it contains. */
@Serializable
data class StopGroup(
    val name: StopGroupName = StopGroupName(),
    val stopIds: List<String> = emptyList(),
) {
    /** The group's display name — the first entry of the name object's `names` array, like legacy. */
    val displayName: String? get() = name.names.firstOrNull()
}

/**
 * The group's name object. The canonical OBA field is the `names` array (the scalar `name` some
 * servers also emit is non-standard); [StopGroup.displayName] reads `names[0]` to match legacy.
 */
@Serializable
data class StopGroupName(
    val names: List<String> = emptyList(),
)

/**
 * Wire model for a trip — the full trip record returned by the `trip` endpoint and carried (with
 * just routeId/headsign/blockId typically consumed) in the references pool of other responses.
 * Names match the wire (`tripHeadsign`/`tripShortName`/`timeZone`).
 */
@Serializable
data class TripReference(
    val id: String = "",
    val routeId: String = "",
    val tripHeadsign: String? = null,
    val tripShortName: String? = null,
    val blockId: String? = null,
    val directionId: String? = null,
    val serviceId: String? = null,
    val shapeId: String? = null,
    val timeZone: String? = null,
)

/** Wire model for the trip-details entry: real-time [status] and the [schedule] of stop times. */
@Serializable
data class TripDetailsEntry(
    val tripId: String = "",
    val status: TripStatus? = null,
    val schedule: TripSchedule? = null,
)

/**
 * Real-time status for a trip. Only the fields the trip-details screen reads are modeled; times are
 * epoch millis, [scheduleDeviation] is seconds (+late/−early), [status] is the wire string (e.g.
 * "CANCELED"), and [activeTripId] is the trip the vehicle is currently serving.
 */
@Serializable
data class TripStatus(
    val activeTripId: String = "",
    val predicted: Boolean = false,
    val scheduleDeviation: Long = 0,
    val serviceDate: Long = 0,
    val status: String = "",
    val phase: String? = null,
    val vehicleId: String? = null,
    val closestStop: String? = null,
    val closestStopTimeOffset: Long = 0,
    val nextStop: String? = null,
    val nextStopTimeOffset: Long? = null,
    val position: Position? = null,
    val orientation: Double? = null,
    val distanceAlongTrip: Double? = null,
    val scheduledDistanceAlongTrip: Double? = null,
    val totalDistanceAlongTrip: Double? = null,
    val lastUpdateTime: Long = 0,
    val lastLocationUpdateTime: Long = 0,
    val lastKnownLocation: Position? = null,
    val lastKnownDistanceAlongTrip: Double? = null,
    val lastKnownOrientation: Double? = null,
    val blockTripSequence: Int = 0,
    val occupancyStatus: String? = null,
)

/** A lat/lon point (e.g. a trip's last-known vehicle location). */
@Serializable
data class Position(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

/** The scheduled stop times of a trip, in order, plus the adjacent block trips and zone. */
@Serializable
data class TripSchedule(
    val stopTimes: List<StopTime> = emptyList(),
    val timeZone: String? = null,
    val previousTripId: String? = null,
    val nextTripId: String? = null,
)

/**
 * One scheduled stop on a trip; [arrivalTime]/[departureTime] are epoch millis and
 * [distanceAlongTrip] is meters (used by the schedule-replay extrapolator).
 */
@Serializable
data class StopTime(
    val stopId: String = "",
    val stopHeadsign: String? = null,
    val arrivalTime: Long = 0,
    val departureTime: Long = 0,
    val historicalOccupancy: String? = null,
    val predictedOccupancy: String? = null,
    val distanceAlongTrip: Double = 0.0,
)

/**
 * The arrivals-and-departures-for-stop entry: the [arrivalsAndDepartures] at [stopId], plus the
 * [nearbyStopIds] and stop-level [situationIds] (resolved against [References]).
 */
@Serializable
data class ArrivalsForStop(
    val stopId: String = "",
    val arrivalsAndDepartures: List<ArrivalDeparture> = emptyList(),
    val nearbyStopIds: List<String> = emptyList(),
    val situationIds: List<String> = emptyList(),
)

/**
 * One predicted/scheduled arrival-departure at a stop. Wire names `tripHeadsign`/`routeShortName`
 * match the API; times are epoch millis; occupancy/status are wire strings mapped to the display
 * enums by the projection. Only the fields the arrivals projection reads are modeled.
 */
@Serializable
data class ArrivalDeparture(
    val routeId: String = "",
    val tripId: String = "",
    val stopId: String = "",
    val tripHeadsign: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val stopSequence: Int = 0,
    val serviceDate: Long = 0,
    val vehicleId: String? = null,
    val predicted: Boolean = false,
    val scheduledArrivalTime: Long = 0,
    val predictedArrivalTime: Long = 0,
    val scheduledDepartureTime: Long = 0,
    val predictedDepartureTime: Long = 0,
    val tripStatus: TripStatus? = null,
    val frequency: Frequency? = null,
    val historicalOccupancy: String? = null,
    val occupancyStatus: String? = null,
    val situationIds: List<String> = emptyList(),
)

/** Headway-based (exact_times=0) service window for a frequency trip; all epoch millis / seconds. */
@Serializable
data class Frequency(
    val startTime: Long = 0,
    val endTime: Long = 0,
    val headway: Long = 0,
)

/** Wire model for a service alert (situation) in the references pool. */
@Serializable
data class SituationReference(
    val id: String = "",
    val summary: SituationText = SituationText(),
    val description: SituationText = SituationText(),
    val url: SituationText = SituationText(),
    val severity: String? = null,
    val activeWindows: List<SituationWindow> = emptyList(),
    val allAffects: List<SituationAffects> = emptyList(),
)

/** An OBA localized-string wrapper (`{value, lang}`); only the [value] is modeled. */
@Serializable
data class SituationText(
    val value: String? = null,
)

/** A situation active window; [from]/[to] are epoch seconds (to == 0 means no end). */
@Serializable
data class SituationWindow(
    val from: Long = 0,
    val to: Long = 0,
)

/** A situation's affects clause; only [routeId] is modeled (for route-filtered alerts). */
@Serializable
data class SituationAffects(
    val routeId: String? = null,
)

/** Marker payload for endpoints whose response carries no data — only the envelope code matters (e.g. report-problem). */
@Serializable
class NoData

/** The current-time entry: server [time] (epoch millis) and its [readableTime] ISO-8601 rendering. */
@Serializable
data class CurrentTime(
    val time: Long = 0,
    val readableTime: String = "",
)

/**
 * The schedule-for-stop entry: a stop's scheduled service for a [date], grouped by route in
 * [stopRouteSchedules]. [timeZone] is the stop's zone; times below are epoch millis.
 */
@Serializable
data class StopSchedule(
    val stopId: String = "",
    val timeZone: String = "",
    val date: Long = 0,
    val stopRouteSchedules: List<RouteSchedule> = emptyList(),
)

/** A route's scheduled service at a stop, split by direction in [stopRouteDirectionSchedules]. */
@Serializable
data class RouteSchedule(
    val routeId: String = "",
    val stopRouteDirectionSchedules: List<DirectionSchedule> = emptyList(),
)

/** One direction's scheduled stop times ([scheduleStopTimes]) under a [tripHeadsign]. */
@Serializable
data class DirectionSchedule(
    val tripHeadsign: String = "",
    val scheduleStopTimes: List<ScheduleStopTime> = emptyList(),
)

/** One scheduled visit of a trip to the stop; [arrivalTime]/[departureTime] are epoch millis. */
@Serializable
data class ScheduleStopTime(
    val tripId: String = "",
    val serviceId: String? = null,
    val stopHeadsign: String? = null,
    val arrivalTime: Long = 0,
    val departureTime: Long = 0,
)

/**
 * Wire model for one OneBusAway region from the regions directory (`regions-vN.json`). Field names
 * mirror the legacy `Region` (the persistence/domain type this maps to via
 * `RegionDto.toObaRegion()`); `ignoreUnknownKeys` tolerates fields no consumer reads.
 */
@Serializable
data class RegionDto(
    val id: Long = 0,
    val regionName: String = "",
    val active: Boolean = false,
    val obaBaseUrl: String? = null,
    val sidecarBaseUrl: String? = null,
    val plausibleAnalyticsServerUrl: String? = null,
    val umamiAnalytics: UmamiAnalyticsDto? = null,
    val siriBaseUrl: String? = null,
    val bounds: List<RegionBoundsDto> = emptyList(),
    val open311Servers: List<Open311ServerDto> = emptyList(),
    val language: String? = null,
    val contactEmail: String? = null,
    val supportsObaDiscoveryApis: Boolean = false,
    val supportsObaRealtimeApis: Boolean = false,
    val supportsSiriRealtimeApis: Boolean = false,
    val twitterUrl: String? = null,
    val experimental: Boolean = false,
    val stopInfoUrl: String? = null,
    val otpBaseUrl: String? = null,
    val otpContactEmail: String? = null,
    val supportsOtpBikeshare: Boolean = false,
    val supportsEmbeddedSocial: Boolean = false,
    val paymentAndroidAppId: String? = null,
    val paymentWarningTitle: String? = null,
    val paymentWarningBody: String? = null,
)

/** One bounding box of a region (center [lat]/[lon] and its [latSpan]/[lonSpan]). */
@Serializable
data class RegionBoundsDto(
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val latSpan: Double = 0.0,
    val lonSpan: Double = 0.0,
)

/** An Open311 (issue-reporting) server configured for a region. */
@Serializable
data class Open311ServerDto(
    val jurisdictionId: String? = null,
    val apiKey: String? = null,
    val baseUrl: String? = null,
)

/** A region's Umami analytics config (`{url, id}`); absent when the region isn't instrumented. */
@Serializable
data class UmamiAnalyticsDto(
    val url: String? = null,
    val id: String? = null,
)

/**
 * The shape (trip path) entry: an encoded-polyline string ([points], Google's algorithm) of
 * [length] points. Decode with `PolylineDecoder.decodeLine`.
 */
@Serializable
data class ShapeEntry(
    val points: String = "",
    val length: Int = 0,
)
