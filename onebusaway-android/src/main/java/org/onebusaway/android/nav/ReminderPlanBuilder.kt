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
package org.onebusaway.android.nav

import android.util.Log
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.decodedPoints
import org.onebusaway.android.directions.model.routeDisplayLabel
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.util.Polyline

internal sealed interface ReminderPlanResult {
    data class Success(val plan: ReminderPlan) : ReminderPlanResult
    data class Error(val reason: ReminderPlanError, val legNumber: Int? = null) : ReminderPlanResult
}

internal enum class ReminderPlanError { MISSING_TRIP, INCOMPLETE_STOP_INFORMATION, NO_TRANSIT_RIDES, INVALID_PLAN }

/** Converts either OTP protocol's normalized itinerary into an all-or-nothing reminder plan. */
internal object ReminderPlanBuilder {
    fun buildSingleRide(
        sessionId: String,
        tripId: String,
        board: ReminderStop?,
        penultimate: ReminderStop,
        alight: ReminderStop,
        mode: ReminderMode = ReminderMode.TRANSIT,
        routeLabel: String? = null,
        scheduledStart: ServerTime? = null,
        scheduledEnd: ServerTime? = null
    ): ReminderPlanResult {
        if (tripId.isBlank()) return ReminderPlanResult.Error(ReminderPlanError.MISSING_TRIP)
        return buildPlan(sessionId) {
            listOf(
                ReminderRide(
                    mode,
                    routeLabel,
                    tripId,
                    board,
                    penultimate,
                    alight,
                    scheduledStart,
                    scheduledEnd
                )
            )
        }
    }

    fun build(
        itinerary: TripItinerary,
        sessionId: String = UUID.randomUUID().toString()
    ): ReminderPlanResult {
        val rides = mutableListOf<ReminderRide>()
        itinerary.legs.forEachIndexed { legIndex, leg ->
            val mode = leg.mode ?: return@forEachIndexed
            if (!mode.isTransit) return@forEachIndexed
            val ride = leg.toReminderRide()
                ?: return ReminderPlanResult.Error(ReminderPlanError.INCOMPLETE_STOP_INFORMATION, legIndex + 1)
            val immediatelyFollowsTransit = legIndex > 0 &&
                itinerary.legs[legIndex - 1].mode?.isTransit == true
            if (leg.interlineWithPreviousLeg && immediatelyFollowsTransit && rides.isNotEmpty()) {
                val previous = rides.removeAt(rides.lastIndex)
                rides += previous.copy(
                    routeLabel = listOfNotNull(previous.routeLabel, ride.routeLabel).distinct().joinToString(" / ").ifBlank { null },
                    penultimate = ride.penultimate,
                    alight = ride.alight,
                    scheduledEnd = ride.scheduledEnd
                )
            } else {
                rides += ride
            }
        }
        if (rides.isEmpty()) return ReminderPlanResult.Error(ReminderPlanError.NO_TRANSIT_RIDES)
        return buildPlan(sessionId) { rides }
    }

    /**
     * Assembles a plan, turning [ReminderPlan]'s own `require` failures into a typed error. Only
     * [IllegalArgumentException] is caught: anything else (an `Error`, a bug in a caller's lambda)
     * is not a rejected itinerary and must not be reported to the rider as one.
     */
    private inline fun buildPlan(sessionId: String, rides: () -> List<ReminderRide>): ReminderPlanResult = try {
        ReminderPlanResult.Success(ReminderPlan(sessionId = sessionId, rides = rides()))
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Rejecting reminder plan for session $sessionId", e)
        ReminderPlanResult.Error(ReminderPlanError.INVALID_PLAN)
    }

    private fun TripLeg.toReminderRide(): ReminderRide? {
        val reminderMode = mode?.toReminderMode() ?: return null
        val orderedStops = normalizedStops() ?: return null
        if (orderedStops.size < 2) return null
        val trip = tripId?.takeIf { it.isNotBlank() } ?: return null
        val board = orderedStops.first()
        val penultimate = orderedStops[orderedStops.lastIndex - 1]
        val alight = orderedStops.last()
        return ReminderRide(
            mode = reminderMode,
            routeLabel = routeDisplayLabel(),
            tripId = trip,
            board = board,
            penultimate = penultimate,
            alight = alight,
            scheduledStart = startTime,
            scheduledEnd = endTime,
            shape = legGeometry?.let { reminderShape(it, board, penultimate, alight) }
        )
    }

    /**
     * Resolves a leg's own board-to-alight geometry into a [ReminderShape], or null when the shape
     * cannot be trusted to place the stops — in which case the ride keeps straight-line distances.
     *
     * Rejected when: the geometry is degenerate; a stop projects further than
     * [MAX_STOP_OFFSET_METERS] from the path (the shape is for a different pattern, or the stops are
     * not the ones this leg actually serves); or the offsets do not increase from board to
     * penultimate to alight (the shape runs the other way, or the stops are out of order), since a
     * non-monotone axis is exactly what this coordinate exists to avoid.
     */
    private fun reminderShape(
        geometry: TripLegGeometry,
        board: ReminderStop,
        penultimate: ReminderStop,
        alight: ReminderStop
    ): ReminderShape? {
        val encoded = geometry.points?.takeIf { it.isNotEmpty() } ?: return null
        val points = geometry.decodedPoints().takeIf { it.size >= 2 } ?: return null
        val polyline = Polyline(points)

        fun offsetOf(stop: ReminderStop): Double? = polyline
            .nearestProjection(stop.point.latitude, stop.point.longitude)
            ?.takeIf { it.distanceToPoint <= MAX_STOP_OFFSET_METERS }
            ?.distanceAlong

        val penultimateOffset = offsetOf(penultimate) ?: return null
        val alightOffset = offsetOf(alight) ?: return null
        // A one-stop ride legitimately has board == penultimate; only require strict growth to the
        // alight stop, which is the comparison the reducer actually makes.
        if (alightOffset <= penultimateOffset) return null
        val boardOffset = offsetOf(board)?.takeIf { it <= penultimateOffset }

        return ReminderShape(
            encodedPoints = encoded,
            pointCount = points.size,
            boardOffsetMeters = boardOffset,
            penultimateOffsetMeters = penultimateOffset,
            alightOffsetMeters = alightOffset
        )
    }

    private fun TripLeg.normalizedStops(): List<ReminderStop>? {
        val raw = buildList {
            add(from)
            addAll(stop?.takeIf { it.isNotEmpty() } ?: intermediateStops.orEmpty())
            add(to)
        }
        val converted = raw.map { it.toReminderStop() ?: return null }
        return converted.fold(mutableListOf()) { result, item ->
            if (result.lastOrNull()?.sameStop(item) != true) result += item
            result
        }
    }

    private fun TripPlace.toReminderStop(): ReminderStop? {
        val stopId = stopId?.takeIf { it.isNotBlank() } ?: stopCode?.takeIf { it.isNotBlank() } ?: return null
        val stopName = name?.takeIf { it.isNotBlank() } ?: return null
        val latitude = lat?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val longitude = lon?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        return ReminderStop(stopId, stopName, ReminderPoint(latitude, longitude))
    }

    private fun ReminderStop.sameStop(other: ReminderStop): Boolean = id == other.id

    private fun TripMode.toReminderMode(): ReminderMode? = when (this) {
        TripMode.BUS -> ReminderMode.BUS
        TripMode.BUSISH -> ReminderMode.BUSISH
        TripMode.TRAM -> ReminderMode.TRAM
        TripMode.SUBWAY -> ReminderMode.SUBWAY
        TripMode.RAIL -> ReminderMode.RAIL
        TripMode.FERRY -> ReminderMode.FERRY
        TripMode.CABLE_CAR -> ReminderMode.CABLE_CAR
        TripMode.GONDOLA -> ReminderMode.GONDOLA
        TripMode.FUNICULAR -> ReminderMode.FUNICULAR
        TripMode.TRANSIT -> ReminderMode.TRANSIT
        TripMode.TRAINISH -> ReminderMode.TRAINISH
        TripMode.WALK,
        TripMode.BICYCLE,
        TripMode.CAR,
        TripMode.BOARDING,
        TripMode.ALIGHTING,
        TripMode.TRANSFER -> null
    }

    private const val TAG = "ReminderPlanBuilder"

    /**
     * How far a stop may sit from the leg's own path and still be placed on it. Transit stops sit
     * off the centreline by design (the far side of an intersection, a platform beside the track),
     * so this has to be generous; beyond it the shape is not describing this leg's stops and the
     * ride is safer on straight-line distances. Same order as
     * `RouteSegmentHighlight.SEGMENT_STOP_TOLERANCE_METERS`, widened because that constant is
     * choosing between candidate stops while this one is validating a match.
     */
    private const val MAX_STOP_OFFSET_METERS = 150.0
}

internal object ReminderPlanJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(plan: ReminderPlan): String = json.encodeToString(plan)

    fun decode(value: String): ReminderPlan? = decodeOrNull { json.decodeFromString<ReminderPlan>(value) }

    fun encodeState(state: ReminderEngineState): String = json.encodeToString(state)

    fun decodeState(value: String): ReminderEngineState? = decodeOrNull { json.decodeFromString<ReminderEngineState>(value) }

    /**
     * A decoding failure is recoverable — the caller discards the stored session rather than
     * crashing the rider's app — so it comes back as null, and the caller reports it with the
     * session it belongs to. Deliberately narrow: [IllegalArgumentException] covers malformed JSON
     * (`SerializationException` is a subclass) and JSON that decodes but violates the model's own
     * `require`s, such as an unsupported format version or a plan with no rides. Anything else is
     * not a bad payload and must not be silenced. No Android dependencies, so the JVM tests can
     * exercise this directly.
     */
    private inline fun <T> decodeOrNull(decode: () -> T): T? = try {
        decode()
    } catch (_: IllegalArgumentException) {
        null
    }
}
