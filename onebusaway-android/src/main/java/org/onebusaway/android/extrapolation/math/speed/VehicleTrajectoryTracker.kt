/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.math.speed

import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.extrapolation.math.SpeedDistribution
import org.onebusaway.android.io.elements.ObaRoute
import org.onebusaway.android.io.elements.ObaTripStatus
import org.onebusaway.android.io.elements.bestDistanceAlongTrip

/**
 * Singleton speed-estimation facade. Delegates trip data access to [TripDataManager] and owns only
 * speed estimation logic (gamma model, schedule speed, route type routing).
 */
object VehicleTrajectoryTracker {

    @JvmStatic fun getInstance() = this

    private val dataManager = TripDataManager
    private val scheduleEstimator = ScheduleSpeedEstimator()
    private var estimator: SpeedEstimator = GammaSpeedEstimator()
    private var lastDistribution: SpeedDistribution? = null

    /**
     * Returns the estimated speed distribution for the given key and current state. Uses the route
     * type from TripDataManager to select the appropriate estimator.
     */
    @Synchronized
    fun getEstimatedDistribution(
            key: String?,
            status: ObaTripStatus?,
            timestampMs: Long
    ): SpeedDistribution? {
        if (key == null || status == null) return null
        val tripId = status.activeTripId ?: return null
        val routeType = dataManager.getRouteType(key)
        val est =
                if (routeType != null && ObaRoute.isGradeSeparated(routeType)) {
                    scheduleEstimator
                } else {
                    estimator
                }
        val result = est.estimateSpeed(tripId, timestampMs, dataManager)
        val dist =
                when (result) {
                    is SpeedEstimateResult.Success -> result.distribution
                    is SpeedEstimateResult.Failure -> null
                }
        lastDistribution = dist
        return dist
    }

    /** Returns the estimated speed in m/s for the given key and current state. */
    @Synchronized
    fun getEstimatedSpeed(key: String?, status: ObaTripStatus?, timestampMs: Long): Double? =
            getEstimatedDistribution(key, status, timestampMs)?.median()

    /**
     * Convenience overload that looks up the last recorded ObaTripStatus from TripDataManager and
     * uses the current system time.
     */
    @Synchronized
    fun getEstimatedSpeed(key: String?): Double? {
        val status = dataManager.getLastState(key)
        return getEstimatedSpeed(key, status, System.currentTimeMillis())
    }

    /** Returns the distribution from the last speed estimate. */
    @Synchronized fun getLastDistribution(): SpeedDistribution? = lastDistribution

    /** Sets the active speed estimator. */
    @Synchronized
    fun setEstimator(estimator: SpeedEstimator?) {
        if (estimator != null) {
            this.estimator = estimator
        }
    }

    /** Clears estimation state. */
    @Synchronized
    fun clearAll() {
        lastDistribution = null
    }
}

/** Max age of the newest AVL entry before we consider extrapolation unreliable. */
private const val MAX_EXTRAPOLATION_AGE_MS = 5L * 60 * 1000

/**
 * Extrapolates the current distance along the trip based on the newest valid history entry and
 * estimated speed. Returns null if extrapolation is not possible.
 *
 * @param history vehicle history entries for the trip
 * @param speedMps estimated speed in meters per second
 * @param currentTimeMs current time in milliseconds
 * @return extrapolated distance in meters, or null
 */
fun extrapolateDistance(
        history: List<ObaTripStatus>?,
        speedMps: Double,
        currentTimeMs: Long
): Double? {
    if (speedMps <= 0 || history == null) return null
    val newest = history.findLast {
        it.bestDistanceAlongTrip != null && it.lastLocationUpdateTime > 0
    } ?: return null
    val lastTime = newest.lastLocationUpdateTime
    if (currentTimeMs - lastTime > MAX_EXTRAPOLATION_AGE_MS) return null
    val lastDist = newest.bestDistanceAlongTrip ?: return null
    return lastDist + speedMps * (currentTimeMs - lastTime) / 1000.0
}
