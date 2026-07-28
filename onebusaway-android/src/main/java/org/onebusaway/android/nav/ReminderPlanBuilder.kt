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

import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.routeDisplayLabel

internal sealed interface ReminderPlanResult {
    data class Success(val plan: ReminderPlan) : ReminderPlanResult
    data class Error(val message: String) : ReminderPlanResult
}

/** Converts either OTP protocol's normalized itinerary into an all-or-nothing reminder plan. */
internal object ReminderPlanBuilder {
    fun buildSingleRide(
        sessionId: String,
        tripId: String,
        board: ReminderStop,
        penultimate: ReminderStop,
        alight: ReminderStop,
        mode: ReminderMode = ReminderMode.TRANSIT,
        routeLabel: String? = null,
        scheduledStart: Long = 0,
        scheduledEnd: Long = scheduledStart
    ): ReminderPlanResult {
        if (tripId.isBlank()) return ReminderPlanResult.Error("A reminder requires a transit trip.")
        return runCatching {
            ReminderPlan(
                sessionId = sessionId,
                rides = listOf(
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
            )
        }.fold(
            onSuccess = { ReminderPlanResult.Success(it) },
            onFailure = { ReminderPlanResult.Error(it.message ?: "Unable to create destination reminders.") }
        )
    }

    fun build(
        itinerary: TripItinerary,
        sessionId: String = UUID.randomUUID().toString()
    ): ReminderPlanResult {
        val rides = mutableListOf<ReminderRide>()
        itinerary.legs.forEachIndexed { legIndex, leg ->
            val mode = leg.mode ?: return@forEachIndexed
            if (!mode.isTransit) return@forEachIndexed
            val ride = leg.toReminderRide() ?: return ReminderPlanResult.Error(
                "Reminders are unavailable because transit leg ${legIndex + 1} has incomplete stop information."
            )
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
        if (rides.isEmpty()) return ReminderPlanResult.Error("This itinerary has no transit rides to monitor.")
        return runCatching { ReminderPlan(sessionId = sessionId, rides = rides) }
            .fold(
                onSuccess = { ReminderPlanResult.Success(it) },
                onFailure = { ReminderPlanResult.Error(it.message ?: "Unable to create destination reminders.") }
            )
    }

    private fun TripLeg.toReminderRide(): ReminderRide? {
        val reminderMode = mode?.toReminderMode() ?: return null
        if (from.toReminderStop() == null || to.toReminderStop() == null) return null
        val orderedStops = normalizedStops()
        if (orderedStops.size < 2) return null
        val trip = tripId?.takeIf { it.isNotBlank() } ?: return null
        return ReminderRide(
            mode = reminderMode,
            routeLabel = routeDisplayLabel(),
            tripId = trip,
            board = orderedStops.first(),
            penultimate = orderedStops[orderedStops.lastIndex - 1],
            alight = orderedStops.last(),
            scheduledStart = startTime.epochMs,
            scheduledEnd = endTime.epochMs
        )
    }

    private fun TripLeg.normalizedStops(): List<ReminderStop> {
        val raw = buildList {
            add(from)
            addAll(stop?.takeIf { it.isNotEmpty() } ?: intermediateStops.orEmpty())
            add(to)
        }
        return raw.mapNotNull { it.toReminderStop() }.fold(mutableListOf()) { result, item ->
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

    private fun ReminderStop.sameStop(other: ReminderStop): Boolean = id == other.id || point == other.point

    private fun TripMode.toReminderMode(): ReminderMode? = runCatching { ReminderMode.valueOf(name) }.getOrNull()
}

internal object ReminderPlanJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(plan: ReminderPlan): String = json.encodeToString(plan)

    fun decode(value: String): ReminderPlan? = runCatching { json.decodeFromString<ReminderPlan>(value) }.getOrNull()

    fun encodeState(state: ReminderEngineState): String = json.encodeToString(state)

    fun decodeState(value: String): ReminderEngineState? = runCatching { json.decodeFromString<ReminderEngineState>(value) }.getOrNull()
}
