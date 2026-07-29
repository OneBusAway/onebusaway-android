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

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.PolylineDecoder
import org.onebusaway.android.util.haversineDistance

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
    /** Where the rider gets on. Null for a legacy session, whose schema carried no boarding stop. */
    val board: ReminderStop?,
    val penultimate: ReminderStop,
    val alight: ReminderStop,
    /** Server-scheduled boarding instant, or null when a legacy session did not carry it. */
    @Serializable(with = NullableReminderServerTimeSerializer::class)
    val scheduledStart: ServerTime?,
    /** Server-scheduled alighting instant, or null when a legacy session did not carry it. */
    @Serializable(with = NullableReminderServerTimeSerializer::class)
    val scheduledEnd: ServerTime?,
    /** This ride's path and its stops' offsets along it; null falls back to straight-line distances. */
    val shape: ReminderShape? = null
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

/**
 * A ride's path, plus where its three stops sit along it. Carrying the stop offsets means the
 * engine projects only the live fix — the stops are resolved once, when the plan is built.
 *
 * Offsets are metres along [encodedPoints] from its start, in the same metric space as the OBA
 * server's `distanceAlongTrip` (see [haversineDistance]), so a shape and offsets that came from the
 * server can be used unchanged.
 *
 * Null on a ride whose geometry was unavailable or implausible; such a ride falls back to
 * straight-line distances. Optional-with-default so a plan serialised before this field existed
 * still decodes, which is what keeps an in-flight session alive across the upgrade.
 */
@Serializable
internal data class ReminderShape(
    val encodedPoints: String,
    val pointCount: Int,
    val boardOffsetMeters: Double?,
    val penultimateOffsetMeters: Double,
    val alightOffsetMeters: Double
) {
    /**
     * Decoded once per process rather than per location fix. A delegated property has no backing
     * field, so it is absent from both the serialised form (only [encodedPoints] is persisted) and
     * from `equals`/`hashCode`, which the reducer relies on to compare states.
     */
    val polyline: Polyline? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        PolylineDecoder.decode(encodedPoints, pointCount).takeIf { it.size >= 2 }?.let(::Polyline)
    }
}

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
    /**
     * The previous fix's [RideProgress.progressMeters], for the forward-movement test. Its scale
     * depends on which coordinate produced it, so only successive values from the same ride are
     * comparable — which is all the reducer does with it.
     */
    val previousProgressMeters: Double? = null,
    val advancedSamples: Int = 0,
    val rideProgressEstablished: Boolean = false,
    /**
     * Set when the session advances to a connecting ride: the rider is standing at the transfer
     * stop and has not boarded yet, so that ride's alerts stay suppressed until departure is seen.
     * False for the session's first ride, whose vehicle the rider is already on or about to board
     * (and which is the only ride a legacy single-ride session has).
     */
    val awaitingBoarding: Boolean = false,
    val boardInsideSamples: Int = 0,
    val speechMuted: Boolean = false,
    @Serializable(with = NullableReminderWallTimeSerializer::class)
    val lastSampleTimestamp: WallTime? = null,
    val completed: Boolean = false
) {
    /**
     * The state a following ride starts from. Progression is per-ride and resets, but the rider's
     * mute choice and the out-of-order-fix guard belong to the session and must survive a transfer.
     */
    fun advancedToRide(index: Int): ReminderEngineState = ReminderEngineState(
        activeRideIndex = index,
        awaitingBoarding = true,
        speechMuted = speechMuted,
        lastSampleTimestamp = lastSampleTimestamp
    )

    /**
     * The part of this state that a restored session must not lose: which ride is active, which
     * alerts have already fired, and whether the rider silenced speech. The rest is per-sample
     * filtering scratch that re-converges within a couple of fixes, so persisting on every change
     * to it would mean a database write for every location update of the whole ride.
     */
    fun restorationKey(): List<Any?> = listOf(
        activeRideIndex,
        getReadyEmitted,
        alightNowEmitted,
        awaitingBoarding,
        speechMuted,
        completed
    )
}

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
 * One location fix read against one ride, in whichever coordinate that ride supports. Producing this
 * is the only part of the reducer that knows about geometry; the alert, latch, and boarding rules
 * downstream are written against these fields and are identical for both coordinates.
 */
internal data class RideProgress(
    /**
     * A monotone-increasing measure of how far the rider has got. Along the route shape it is metres
     * travelled; without one it is the negated straight-line distance to the destination, so that
     * "larger means further along" holds either way. Only differences between fixes are meaningful.
     */
    val progressMeters: Double,
    /** Remaining distance to each stop. Negative once the stop is behind the rider (shape only). */
    val remainingToPenultimateMeters: Double,
    val remainingToAlightMeters: Double,
    val atPenultimate: Boolean,
    val beyondPenultimate: Boolean,
    val atAlight: Boolean,
    /** False when this ride has no boarding stop to reason about, which disables the boarding gate. */
    val hasBoard: Boolean,
    val atBoard: Boolean,
    /** Outside the boarding stop — weak on its own, meaningful once the rider was seen waiting there. */
    val leftBoard: Boolean,
    /** Demonstrably further along the ride than the boarding stop, with no "was seen there" evidence. */
    val pastBoard: Boolean,
    /** True when this reading came from the route shape rather than straight-line distances. */
    val usesShape: Boolean
)

/**
 * Product policy for destination-reminder GPS filtering and alert confirmation: reject fixes worse
 * than 100 m, scale the early warning to 90 seconds of travel, require two consecutive fixes to
 * confirm a stop transition, and recover sparse traces inside 400 m of the destination once forward
 * ride progress is proven.
 *
 * These thresholds are a **new** design for the modernized engine, not a carry-over — the legacy
 * provider used a different scheme (20/50/100 m bands with speed cutoffs, a fixed 300 m ready
 * radius). Per the repository's "no unsanctioned heuristics" rule they are a human-sign-off gate:
 * they are called out in the pull request for explicit approval, and changing them re-opens it.
 *
 * Two coordinate systems, and the difference is deliberate. When a ride knows its route shape, a
 * stop transition is a crossing on a monotone axis and needs only [STOP_CROSSING_MARGIN_METERS] of
 * noise tolerance. Without one, the engine is comparing straight-line distances, which are blind to
 * direction and to the route's actual path, so it needs the accuracy-scaled geofences and their
 * hysteresis instead — those infer a geofence size from a reported accuracy figure whose meaning
 * varies by location provider, and are the weakest part of this policy. They now apply only to the
 * fallback path.
 */
internal object DestinationReminderPolicy {
    const val MAX_ACCEPTED_ACCURACY_METERS = 100f
    const val GET_READY_SECONDS = 90.0
    const val MIN_GET_READY_METERS = 300.0

    /**
     * Cap on the speed-scaled early warning when measuring along the route. Bounds a bogus speed
     * reading (90 s at 200 km/h) without truncating the intended warning: rail at 100 km/h needs
     * 2.5 km to get 90 seconds.
     */
    const val MAX_GET_READY_ALONG_ROUTE_METERS = 5_000.0

    /**
     * The same cap for straight-line distances, and much tighter for a reason: straight-line
     * distance cannot tell "approaching the stop" from "passing within a kilometre of it twenty
     * minutes before serving it", which a loop or a one-way pair does routinely. A large radius on a
     * direction-blind measure would fire the early warning at the wrong time.
     */
    const val MAX_GET_READY_STRAIGHT_LINE_METERS = 1_200.0

    /**
     * How far past a stop's offset a fix must land to count as having crossed it, when measuring
     * along the route. Small because the projection is exact; noise rejection comes from the
     * accuracy filter and from requiring [REQUIRED_CONSECUTIVE_SAMPLES] confirming fixes.
     */
    const val STOP_CROSSING_MARGIN_METERS = 30.0

    /**
     * How far off its route a fix may land and still be read as a position along it. Beyond this the
     * rider is somewhere the shape does not describe — a detour, a bad fix, or the wrong vehicle —
     * and that fix is read with straight-line distances instead.
     */
    const val MAX_OFF_ROUTE_METERS = 100.0

    const val SPARSE_FIX_FALLBACK_METERS = 400.0
    const val REQUIRED_CONSECUTIVE_SAMPLES = 2

    // Straight-line fallback only. See the class note above.
    const val MIN_ARRIVAL_RADIUS_METERS = 35.0
    const val MAX_ARRIVAL_RADIUS_METERS = 100.0
    const val ARRIVAL_ACCURACY_MULTIPLIER = 1.5
    const val MIN_DEPARTURE_RADIUS_METERS = 75.0
    const val MAX_DEPARTURE_RADIUS_METERS = 150.0
    const val DEPARTURE_ACCURACY_MULTIPLIER = 2.0

    fun arrivalRadius(accuracyMeters: Float): Double = min(
        MAX_ARRIVAL_RADIUS_METERS,
        max(MIN_ARRIVAL_RADIUS_METERS, accuracyMeters * ARRIVAL_ACCURACY_MULTIPLIER)
    )

    fun departureRadius(accuracyMeters: Float): Double = min(
        MAX_DEPARTURE_RADIUS_METERS,
        max(MIN_DEPARTURE_RADIUS_METERS, accuracyMeters * DEPARTURE_ACCURACY_MULTIPLIER)
    )

    /** The early-warning distance for this fix, capped per the coordinate in use. */
    fun getReadyDistance(speedMetersPerSecond: Float?, usesShape: Boolean): Double {
        val cap = if (usesShape) MAX_GET_READY_ALONG_ROUTE_METERS else MAX_GET_READY_STRAIGHT_LINE_METERS
        return speedMetersPerSecond
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.times(GET_READY_SECONDS)
            ?.coerceIn(MIN_GET_READY_METERS, cap)
            ?: MIN_GET_READY_METERS
    }
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
        val progress = ride.progressFor(sample)
        val getReadyDistance = DestinationReminderPolicy.getReadyDistance(sample.speedMetersPerSecond, progress.usesShape)
        val effects = mutableListOf<ReminderEffect>(
            ReminderEffect.Progress(state.activeRideIndex, progress.remainingToAlightMeters, getReadyDistance)
        )

        // Forward movement, whichever coordinate produced it. Along a shape this is monotone by
        // construction; on straight-line distances it is the old "getting closer to the destination"
        // test, which a curving route can defeat.
        val advanced = state.previousProgressMeters?.let { progress.progressMeters > it } ?: false
        val advancedSamples = if (advanced) state.advancedSamples + 1 else 0
        var next = state.copy(
            previousProgressMeters = progress.progressMeters,
            advancedSamples = advancedSamples,
            rideProgressEstablished = state.rideProgressEstablished ||
                advancedSamples >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES,
            lastSampleTimestamp = sample.timestamp
        )
        val isTransfer = state.activeRideIndex < plan.rides.lastIndex

        if (next.awaitingBoarding) {
            next = next.confirmBoarding(progress, advanced)
            // Until the connecting vehicle is under way, every proximity signal here is the rider
            // waiting at the transfer stop — alerting on it would fire "prepare to exit" on a
            // platform, and the latch would then suppress the alert when it actually matters.
            if (next.awaitingBoarding) return ReminderTransition(next, effects)
        }

        if (!next.getReadyEmitted && progress.remainingToPenultimateMeters <= getReadyDistance) {
            next = next.copy(getReadyEmitted = true)
            effects += ReminderEffect.GetReady(state.activeRideIndex, ride.alight, isTransfer)
        }

        if (!next.penultimateReached) {
            val insideCount = if (progress.atPenultimate) state.penultimateInsideSamples + 1 else 0
            next = next.copy(
                penultimateInsideSamples = insideCount,
                penultimateReached = insideCount >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES
            )
            if (next.penultimateReached) next = next.copy(rideProgressEstablished = true)
        }

        if (next.penultimateReached && !next.alightNowEmitted) {
            val departedCount = if (progress.beyondPenultimate && advanced) state.penultimateDepartureSamples + 1 else 0
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
            progress.remainingToAlightMeters <= DestinationReminderPolicy.SPARSE_FIX_FALLBACK_METERS &&
            !next.alightNowEmitted
        ) {
            if (!next.getReadyEmitted) {
                next = next.copy(getReadyEmitted = true)
                effects += ReminderEffect.GetReady(state.activeRideIndex, ride.alight, isTransfer)
            }
            next = next.copy(alightNowEmitted = true)
            effects += ReminderEffect.AlightNow(
                state.activeRideIndex,
                ride.alight,
                isTransfer,
                ride.mode.usesRequestStopWording
            )
        }

        val alightInsideCount = if (progress.atAlight) state.alightInsideSamples + 1 else 0
        next = next.copy(alightInsideSamples = alightInsideCount)
        if (next.rideProgressEstablished && alightInsideCount >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES) {
            effects += ReminderEffect.RideCompleted(state.activeRideIndex)
            if (state.activeRideIndex == plan.rides.lastIndex) {
                next = next.copy(completed = true)
                effects += ReminderEffect.SessionCompleted
            } else {
                next = next.advancedToRide(state.activeRideIndex + 1)
            }
        }

        return ReminderTransition(next, effects)
    }

    /**
     * Reads one fix against this ride, preferring the route shape and falling back to straight-line
     * distances when the ride has no shape, its geometry could not be decoded, or this particular fix
     * landed too far off the path to be placed on it.
     */
    private fun ReminderRide.progressFor(sample: ReminderLocationSample): RideProgress = shapeProgress(sample) ?: straightLineProgress(sample)

    private fun ReminderRide.shapeProgress(sample: ReminderLocationSample): RideProgress? {
        val shape = shape ?: return null
        val polyline = shape.polyline ?: return null
        val projection = polyline.nearestProjection(sample.point.latitude, sample.point.longitude) ?: return null
        if (projection.distanceToPoint > DestinationReminderPolicy.MAX_OFF_ROUTE_METERS) return null

        val along = projection.distanceAlong
        val margin = DestinationReminderPolicy.STOP_CROSSING_MARGIN_METERS
        val boardOffset = shape.boardOffsetMeters
        return RideProgress(
            progressMeters = along,
            remainingToPenultimateMeters = shape.penultimateOffsetMeters - along,
            remainingToAlightMeters = shape.alightOffsetMeters - along,
            atPenultimate = abs(along - shape.penultimateOffsetMeters) <= margin,
            beyondPenultimate = along > shape.penultimateOffsetMeters + margin,
            atAlight = along >= shape.alightOffsetMeters - margin,
            hasBoard = boardOffset != null,
            atBoard = boardOffset != null && abs(along - boardOffset) <= margin,
            // On a monotone axis, having left the boarding stop and being past it are the same fact.
            leftBoard = boardOffset != null && along > boardOffset + margin,
            pastBoard = boardOffset != null && along > boardOffset + margin,
            usesShape = true
        )
    }

    private fun ReminderRide.straightLineProgress(sample: ReminderLocationSample): RideProgress {
        val arrivalRadius = DestinationReminderPolicy.arrivalRadius(sample.accuracyMeters)
        // Stop coordinates are commonly offset from the vehicle path, so arrival at the destination
        // uses the wider departure geofence. A route shape makes this compensation unnecessary.
        val departureRadius = DestinationReminderPolicy.departureRadius(sample.accuracyMeters)
        val penultimateDistance = distanceMeters(sample.point, penultimate.point)
        val alightDistance = distanceMeters(sample.point, alight.point)
        val boardDistance = board?.let { distanceMeters(sample.point, it.point) }
        return RideProgress(
            // Negated, so that "larger is further along" holds in both coordinates.
            progressMeters = -alightDistance,
            remainingToPenultimateMeters = penultimateDistance,
            remainingToAlightMeters = alightDistance,
            atPenultimate = penultimateDistance <= arrivalRadius,
            beyondPenultimate = penultimateDistance > departureRadius,
            atAlight = alightDistance <= departureRadius,
            hasBoard = boardDistance != null,
            atBoard = boardDistance != null && boardDistance <= arrivalRadius,
            leftBoard = boardDistance != null && boardDistance > departureRadius,
            // Straight-line distance cannot see direction, so "past the boarding stop" needs the
            // stronger evidence of being meaningfully nearer the destination than the boarding stop
            // is. Merely being far from the boarding stop is also true while walking towards it.
            pastBoard = board != null &&
                alightDistance < distanceMeters(board.point, alight.point) - departureRadius,
            usesShape = false
        )
    }

    /**
     * Clears [ReminderEngineState.awaitingBoarding] once the rider is under way on this ride,
     * by either of two independent signals:
     *
     * - they were seen at the boarding stop and have since left it, still moving forward (the normal
     *   wait-then-board sequence); or
     * - they are already beyond the boarding stop with ride progress established, which recovers the
     *   case where fixes were sparse or absent while they waited.
     */
    private fun ReminderEngineState.confirmBoarding(progress: RideProgress, advanced: Boolean): ReminderEngineState {
        // Nothing to gate on: a legacy session carries no boarding stop.
        if (!progress.hasBoard) return copy(awaitingBoarding = false)
        val insideSamples = if (progress.atBoard) {
            // Monotonic, not consecutive: a rider dwells at the stop, and one noisy fix in the
            // middle of the wait should not discard the evidence that they were there.
            min(boardInsideSamples + 1, DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES)
        } else {
            boardInsideSamples
        }
        val leftBoardingStop = insideSamples >= DestinationReminderPolicy.REQUIRED_CONSECUTIVE_SAMPLES &&
            progress.leftBoard &&
            advanced
        val pastBoardingStop = rideProgressEstablished && progress.pastBoard
        return copy(
            boardInsideSamples = insideSamples,
            awaitingBoarding = !(leftBoardingStop || pastBoardingStop)
        )
    }

    /**
     * Straight-line distance between two reminder points. Delegates to the shared [haversineDistance]
     * so these distances sit in the same metric space as [Polyline]'s cumulative distances and the
     * server's `distanceAlongTrip`, which the two coordinates are compared against each other.
     */
    internal fun distanceMeters(first: ReminderPoint, second: ReminderPoint): Double = haversineDistance(first.latitude, first.longitude, second.latitude, second.longitude)
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
