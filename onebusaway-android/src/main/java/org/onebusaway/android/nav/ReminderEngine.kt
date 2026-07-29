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

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

/** A complete, versioned destination-reminder session. This model has no Android dependencies. */
@Serializable
internal data class ReminderPlan(
    val version: Int = CURRENT_VERSION,
    val sessionId: String,
    val rides: List<ReminderRide>
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported reminder plan version: $version" }
        require(sessionId.isNotBlank()) { "A reminder plan requires a session id" }
        require(rides.isNotEmpty()) { "A reminder plan requires at least one transit ride" }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
internal data class ReminderRide(
    val mode: ReminderMode,
    val routeLabel: String?,
    val tripId: String,
    val board: ReminderStop,
    val penultimate: ReminderStop,
    val alight: ReminderStop,
    /** Server-scheduled boarding instant, or null when a legacy session did not carry it. */
    @Serializable(with = NullableReminderServerTimeSerializer::class)
    val scheduledStart: ServerTime?,
    /** Server-scheduled alighting instant, or null when a legacy session did not carry it. */
    @Serializable(with = NullableReminderServerTimeSerializer::class)
    val scheduledEnd: ServerTime?
)

@Serializable
internal enum class ReminderMode {
    BUS,
    BUSISH,
    TRAM,
    SUBWAY,
    RAIL,
    FERRY,
    CABLE_CAR,
    GONDOLA,
    FUNICULAR,
    TRANSIT,
    TRAINISH;

    val usesRequestStopWording: Boolean
        get() = this == BUS || this == BUSISH
}

@Serializable
internal data class ReminderStop(val id: String, val name: String, val point: ReminderPoint)

@Serializable
internal data class ReminderPoint(val latitude: Double, val longitude: Double)

/** A platform-neutral location fix supplied to [ReminderEngine.reduce]. */
internal data class ReminderLocationSample(
    val point: ReminderPoint,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val timestamp: WallTime
)

@Serializable
internal data class ReminderEngineState(
    val activeRideIndex: Int = 0,
    val getReadyEmitted: Boolean = false,
    val alightNowEmitted: Boolean = false,
    val penultimateInsideSamples: Int = 0,
    val penultimateReached: Boolean = false,
    val penultimateDepartureSamples: Int = 0,
    val alightInsideSamples: Int = 0,
    val previousAlightDistanceMeters: Double? = null,
    val towardAlightSamples: Int = 0,
    val rideProgressEstablished: Boolean = false,
    val speechMuted: Boolean = false,
    @Serializable(with = NullableReminderWallTimeSerializer::class)
    val lastSampleTimestamp: WallTime? = null,
    val completed: Boolean = false
)

internal sealed interface ReminderEffect {
    data class Progress(
        val rideIndex: Int,
        val alightDistanceMeters: Double,
        val getReadyRadiusMeters: Double
    ) : ReminderEffect

    data class GetReady(val rideIndex: Int, val stop: ReminderStop, val isTransfer: Boolean) : ReminderEffect

    data class AlightNow(
        val rideIndex: Int,
        val stop: ReminderStop,
        val isTransfer: Boolean,
        val usesRequestStopWording: Boolean
    ) : ReminderEffect

    data class RideCompleted(val rideIndex: Int) : ReminderEffect

    data object SessionCompleted : ReminderEffect
}

internal data class ReminderTransition(val state: ReminderEngineState, val effects: List<ReminderEffect>)

/**
 * Product policy for destination-reminder GPS filtering and alert confirmation. These values come
 * from the accepted destination-reminder design: reject fixes worse than 100 m, scale the early
 * warning to 90 seconds of travel within 300–1,200 m, require two consecutive fixes for geofence
 * transitions, and recover sparse traces inside 400 m only after forward ride progress is proven.
 * Accuracy-scaled radii reduce false positives from noisy fixes while their caps keep a poor fix
 * from turning into an unbounded geofence.
 */
internal object DestinationReminderPolicy {
    const val MAX_ACCEPTED_ACCURACY_METERS = 100f
    const val MIN_GET_READY_METERS = 300.0
    const val MAX_GET_READY_METERS = 1_200.0
    const val GET_READY_SECONDS = 90.0
    const val MIN_ARRIVAL_RADIUS_METERS = 35.0
    const val MAX_ARRIVAL_RADIUS_METERS = 100.0
    const val ARRIVAL_ACCURACY_MULTIPLIER = 1.5
    const val MIN_DEPARTURE_RADIUS_METERS = 75.0
    const val MAX_DEPARTURE_RADIUS_METERS = 150.0
    const val DEPARTURE_ACCURACY_MULTIPLIER = 2.0
    const val SPARSE_FIX_FALLBACK_METERS = 400.0
    const val REQUIRED_CONSECUTIVE_SAMPLES = 2

    fun arrivalRadius(accuracyMeters: Float): Double = min(
        MAX_ARRIVAL_RADIUS_METERS,
        max(MIN_ARRIVAL_RADIUS_METERS, accuracyMeters * ARRIVAL_ACCURACY_MULTIPLIER)
    )

    fun departureRadius(accuracyMeters: Float): Double = min(
        MAX_DEPARTURE_RADIUS_METERS,
        max(MIN_DEPARTURE_RADIUS_METERS, accuracyMeters * DEPARTURE_ACCURACY_MULTIPLIER)
    )
}

/**
 * Pure destination-reminder reducer. Its only input is immutable domain state plus one location fix;
 * notifications, persistence, speech, analytics, and logging are effects handled by Android adapters.
 */
internal object ReminderEngine {
    fun reduce(
        plan: ReminderPlan,
        state: ReminderEngineState,
        sample: ReminderLocationSample
    ): ReminderTransition {
        if (state.completed || state.activeRideIndex !in plan.rides.indices) return ReminderTransition(state, emptyList())
        if (sample.accuracyMeters !in 0f..DestinationReminderPolicy.MAX_ACCEPTED_ACCURACY_METERS) {
            return ReminderTransition(state, emptyList())
        }
        if (state.lastSampleTimestamp != null && sample.timestamp <= state.lastSampleTimestamp) {
            return ReminderTransition(state, emptyList())
        }

        val ride = plan.rides[state.activeRideIndex]
        val penultimateDistance = distanceMeters(sample.point, ride.penultimate.point)
        val alightDistance = distanceMeters(sample.point, ride.alight.point)
        val getReadyRadius = sample.speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.times(DestinationReminderPolicy.GET_READY_SECONDS)
            ?.coerceIn(DestinationReminderPolicy.MIN_GET_READY_METERS, DestinationReminderPolicy.MAX_GET_READY_METERS)
            ?: DestinationReminderPolicy.MIN_GET_READY_METERS
        val arrivalRadius = DestinationReminderPolicy.arrivalRadius(sample.accuracyMeters)
        val departureRadius = DestinationReminderPolicy.departureRadius(sample.accuracyMeters)
        val effects = mutableListOf<ReminderEffect>(
            ReminderEffect.Progress(state.activeRideIndex, alightDistance, getReadyRadius)
        )

        val movingTowardAlight = state.previousAlightDistanceMeters?.let { alightDistance < it } ?: false
        val towardAlightSamples = if (movingTowardAlight) state.towardAlightSamples + 1 else 0
        var next = state.copy(
            previousAlightDistanceMeters = alightDistance,
            towardAlightSamples = towardAlightSamples,
            rideProgressEstablished = state.rideProgressEstablished ||
                towardAlightSamples >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES,
            lastSampleTimestamp = sample.timestamp
        )
        val isTransfer = state.activeRideIndex < plan.rides.lastIndex

        if (!next.getReadyEmitted && penultimateDistance <= getReadyRadius) {
            next = next.copy(getReadyEmitted = true)
            effects += ReminderEffect.GetReady(state.activeRideIndex, ride.alight, isTransfer)
        }

        if (!next.penultimateReached) {
            val insideCount = if (penultimateDistance <= arrivalRadius) state.penultimateInsideSamples + 1 else 0
            next = next.copy(
                penultimateInsideSamples = insideCount,
                penultimateReached = insideCount >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES
            )
            if (next.penultimateReached) next = next.copy(rideProgressEstablished = true)
        }

        if (next.penultimateReached && !next.alightNowEmitted) {
            val departedCount = if (penultimateDistance > departureRadius && movingTowardAlight) {
                state.penultimateDepartureSamples + 1
            } else {
                0
            }
            next = next.copy(penultimateDepartureSamples = departedCount)
            if (departedCount >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES) {
                next = next.copy(alightNowEmitted = true)
                effects += ReminderEffect.AlightNow(
                    state.activeRideIndex,
                    ride.alight,
                    isTransfer,
                    ride.mode.usesRequestStopWording
                )
            }
        }

        // A device may sleep through the penultimate stop. Once the destination is unmistakably near,
        // deliver each missed alert exactly once rather than silently abandoning the rider.
        if (
            next.rideProgressEstablished &&
            alightDistance <= DestinationReminderPolicy.SPARSE_FIX_FALLBACK_METERS &&
            !next.alightNowEmitted
        ) {
            if (!next.getReadyEmitted) {
                next = next.copy(getReadyEmitted = true)
                effects += ReminderEffect.GetReady(state.activeRideIndex, ride.alight, isTransfer)
            }
            if (!next.alightNowEmitted) {
                next = next.copy(alightNowEmitted = true)
                effects += ReminderEffect.AlightNow(
                    state.activeRideIndex,
                    ride.alight,
                    isTransfer,
                    ride.mode.usesRequestStopWording
                )
            }
        }

        // Stop coordinates are commonly offset from the vehicle path. Use the wider departure
        // geofence for arrival completion while still requiring two independent fixes.
        val alightInsideCount = if (alightDistance <= departureRadius) state.alightInsideSamples + 1 else 0
        next = next.copy(alightInsideSamples = alightInsideCount)
        if (next.rideProgressEstablished && alightInsideCount >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES) {
            effects += ReminderEffect.RideCompleted(state.activeRideIndex)
            if (state.activeRideIndex == plan.rides.lastIndex) {
                next = next.copy(completed = true)
                effects += ReminderEffect.SessionCompleted
            } else {
                next = ReminderEngineState(activeRideIndex = state.activeRideIndex + 1)
            }
        }

        return ReminderTransition(next, effects)
    }

    internal fun distanceMeters(first: ReminderPoint, second: ReminderPoint): Double {
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(second.longitude - first.longitude)
        val haversine = sin(deltaLat / 2) *
            sin(deltaLat / 2) +
            cos(lat1) *
            cos(lat2) *
            sin(deltaLon / 2) *
            sin(deltaLon / 2)
        return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}

private object ReminderServerTimeSerializer : KSerializer<ServerTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ServerTime", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: ServerTime) = encoder.encodeLong(value.epochMs)
    override fun deserialize(decoder: Decoder): ServerTime = ServerTime(decoder.decodeLong())
}

private object NullableReminderServerTimeSerializer : KSerializer<ServerTime?> by ReminderServerTimeSerializer.nullable

private object ReminderWallTimeSerializer : KSerializer<WallTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WallTime", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: WallTime) = encoder.encodeLong(value.epochMs)
    override fun deserialize(decoder: Decoder): WallTime = WallTime(decoder.decodeLong())
}

private object NullableReminderWallTimeSerializer : KSerializer<WallTime?> by ReminderWallTimeSerializer.nullable
