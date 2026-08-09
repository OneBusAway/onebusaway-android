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
package org.onebusaway.android.api.data

import android.util.Log
import java.net.HttpURLConnection
import javax.inject.Inject
import org.onebusaway.android.api.adapters.DtoRoute
import org.onebusaway.android.api.adapters.DtoStop
import org.onebusaway.android.api.adapters.asArrivalData
import org.onebusaway.android.api.contract.ArrivalsForLocationData
import org.onebusaway.android.api.net.ObaApiProvider
import org.onebusaway.android.api.requireData
import org.onebusaway.android.map.render.CameraSnapshot
import org.onebusaway.android.models.ArrivalData
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.time.ServerTime
import retrofit2.HttpException

/**
 * A resolved snapshot of every stop's arrivals inside one viewport, from a single
 * arrivals-and-departures-for-location response (#2107). The stop-scoped sibling is [StopArrivals];
 * this exposes the same `models` interfaces so the UI never sees a wire DTO, and keeps the envelope
 * private the same way.
 */
class NearbyArrivals internal constructor(
    private val data: ArrivalsForLocationData,
    /**
     * The server clock this response was rendered at — the baseline every ETA below is measured
     * against, so device clock skew cancels (#1612). Minted here at the wire boundary rather than
     * carried as a bare `Long` for each consumer to wrap (#1620).
     */
    val serverNow: ServerTime,
    /** The effective minutes-after window this response was fetched with. */
    val minutesAfter: Int
) {
    private val refs get() = data.references

    /**
     * True when more matched the box than the server returned. Worth surfacing rather than ignoring:
     * the server truncates the *arrivals* list ordered by distance from the query centre, so a
     * truncated response drops the farthest bays outright instead of trimming each one — a rider
     * could find their bay simply absent with nothing on screen saying so.
     */
    val limitExceeded: Boolean get() = data.entry?.limitExceeded ?: false

    /**
     * Every arrival at every stop in the box, adapted to [ArrivalData], with unrecoverable corrupt
     * timestamps dropped and duplicate trip instances collapsed — the same treatment
     * [StopArrivals.arrivals] gives one stop's list (#1710 / #2012). `directionId` is resolved off
     * the trip references, since the arrival elements don't carry it (as on the per-stop path).
     *
     * A null `entry` is the server's own empty-box answer, not a broken response (see
     * `ArrivalsForLocationData`), so it resolves to no arrivals rather than an error.
     */
    val arrivals: List<ArrivalData>
        get() = data.entry?.arrivalsAndDepartures.orEmpty()
            .mapNotNull { it.asArrivalData(refs.trip(it.tripId)?.directionId?.toIntOrNull()) }
            .collapseDuplicateTripInstances { tripId -> refs.trip(tripId)?.blockId }

    /** Resolves a bay from the references pool by id, or null when absent. */
    fun stop(id: String): ObaStop? = refs.stop(id)?.let(::DtoStop)

    /** Resolves a route from the references pool by id, or null when absent. */
    fun route(id: String): ObaRoute? = refs.route(id)?.let(::DtoRoute)

    /** Resolves a route's agency name from the references pool, or null. */
    fun agencyName(id: String): String? = refs.agency(id)?.name

    /**
     * Every service alert this response references: the box-level ones plus every alert an arrival
     * names, de-duplicated by id with order preserved — matching [StopArrivals.situations].
     */
    fun situations(): List<ObaSituation> {
        val entry = data.entry ?: return emptyList()
        val arrivalSituationIds = entry.arrivalsAndDepartures.flatMap { it.situationIds }
        return (entry.situationIds + arrivalSituationIds)
            .distinct()
            .mapNotNull { refs.situation(it) }
            .map(::DtoSituation)
    }
}

/** One viewport's arrivals fetch, or the verdict that this region cannot serve them. */
sealed interface NearbyArrivalsResult {

    /** The response, resolved. Its `arrivals` may be empty — a box with no stops in it. */
    data class Loaded(val arrivals: NearbyArrivals) : NearbyArrivalsResult

    /**
     * This region's server does not implement arrivals-and-departures-for-location: it answered HTTP
     * 404. A durable fact about the deployment, not an error to retry — see [isEndpointAbsent].
     */
    data object Unsupported : NearbyArrivalsResult

    /** A transient failure (transport, timeout, a non-OK OBA envelope code). Retry on the next poll. */
    data class Failed(val cause: Throwable) : NearbyArrivalsResult
}

/** Fetches every stop's arrivals inside a viewport in one request (#2107). */
interface NearbyArrivalsDataSource {

    /**
     * One viewport's arrivals at [minutesAfter]. Never throws, and deliberately not a `Result`: the
     * three outcomes the caller has to tell apart are a response, an unsupported region, and a
     * transient failure, and folding the middle one into `failure` is exactly the conflation that
     * would switch the feature off on a timeout.
     */
    suspend fun arrivals(viewport: CameraSnapshot, minutesAfter: Int): NearbyArrivalsResult
}

class DefaultNearbyArrivalsDataSource @Inject constructor(
    private val api: ObaApiProvider
) : NearbyArrivalsDataSource {

    override suspend fun arrivals(
        viewport: CameraSnapshot,
        minutesAfter: Int
    ): NearbyArrivalsResult {
        val result = api.call {
            val envelope = it.arrivalsAndDeparturesForLocation(
                lat = viewport.center.latitude,
                lon = viewport.center.longitude,
                latSpan = viewport.latSpan,
                lonSpan = viewport.lonSpan,
                minutesAfter = minutesAfter
            )
            NearbyArrivals(
                envelope.requireData(),
                ServerTime(serverNowOrDeviceClock(envelope.currentTime)),
                minutesAfter
            )
        }
        return result.fold(
            onSuccess = { NearbyArrivalsResult.Loaded(it) },
            onFailure = { cause ->
                if (isEndpointAbsent(cause)) {
                    Log.i(TAG, "Region does not serve arrivals-and-departures-for-location")
                    NearbyArrivalsResult.Unsupported
                } else {
                    Log.e(TAG, "nearby arrivals failed", cause)
                    NearbyArrivalsResult.Failed(cause)
                }
            }
        )
    }

    private companion object {
        const val TAG = "NearbyArrivalsDataSource"
    }
}

/**
 * Whether a failure says this server has no such endpoint. **HTTP 404 and nothing else.**
 *
 * Not an inference about the response — an explicit statement from the transport that the resource
 * does not exist, read off the status line before any decoding. It has to be read from the code
 * rather than the body because a deployment lacking the endpoint answers with whatever its container
 * serves: the San Diego region returns a raw Tomcat HTML error page, which no JSON decode could
 * classify. And there is no legitimate 404 on this path for a server that *does* implement the
 * action — the path is fixed and the query carries only a viewport, so an empty box is answered with
 * an empty response, never a 404.
 *
 * Deliberately narrow, keyed on the *HTTP* status alone. An OBA envelope code of 404 arrives as
 * [org.onebusaway.android.api.ObaApiException] and stays transient, as do timeouts, TLS failures,
 * 5xx, and parse errors — all things that happen to regions which do serve this endpoint. Nothing
 * but an explicit HTTP 404 switches the feature off.
 *
 * Failure mode: a server that implements the action but sits behind a proxy 404-ing it for an
 * unrelated reason loses the drawer until the process restarts. Tolerable because the verdict is
 * held in memory only and re-probed on the next launch (see `NearbyArrivalsSupport`).
 */
internal fun isEndpointAbsent(error: Throwable): Boolean = error is HttpException && error.code() == HttpURLConnection.HTTP_NOT_FOUND
