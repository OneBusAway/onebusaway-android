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
package org.onebusaway.android.extrapolation

import android.location.Location
import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.models.Status
import org.onebusaway.android.map.render.DataAgeMarker
import org.onebusaway.android.map.render.GeoPoint
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.Polyline

/**
 * Projects an extrapolation onto the route shape to produce the per-frame [TripExtrapolation]: the
 * median vehicle estimate, the [FAST_ESTIMATE_QUANTILE] "best case" point, the graded uncertainty
 * band (geometry + weight, no color), and the last-server-fix marker. The geometry half of the
 * feature's TripVehicleOverlay + DistanceEstimateOverlay, lifted out of the Google-only overlay
 * classes into one flavor-neutral, display-free function.
 *
 * Returns null when there is no route shape (nothing to draw). On a non-[ExtrapolationResult.Success]
 * frame the vehicle/fast/band are absent but the data-age marker still shows the last fix.
 *
 * @param nowMs the frame time in the **device** clock domain (matches [TripState.anchorLocalTimeMs])
 */
fun buildTripExtrapolation(
    state: TripState,
    result: ExtrapolationResult,
    nowMs: WallTime,
): TripExtrapolation? {
    val polyline = state.polyline ?: return null
    val distribution = (result as? ExtrapolationResult.Success)?.distribution

    return TripExtrapolation(
        vehiclePoint = distribution?.pointAlong(polyline, distribution.median()),
        fastEstimatePoint = distribution?.pointAlong(polyline, distribution.quantile(FAST_ESTIMATE_QUANTILE)),
        band = distribution?.let { weightedBandSegments(it, polyline) } ?: emptyList(),
        dataAge = dataAgeMarker(state, nowMs),
        // The anchor's instant is constant between fixes; a change means fresh AVL data arrived.
        // Unwrapped to a raw Long for the renderer's animation clock (out of the typed-time slice).
        fixTimeMs = state.anchorLocalTimeMs.epochMs,
    )
}

/** Composes [TripState.extrapolate] with [buildTripExtrapolation] — the per-frame producer the driver runs. */
internal fun extrapolationFromState(state: TripState?, nowMs: WallTime): TripExtrapolation? =
    state?.let { buildTripExtrapolation(it, it.extrapolate(nowMs), nowMs) }

/** The extrapolated (median) vehicle [point] along the trip shape and its forward [bearing] there. */
private data class VehicleProjection(val point: GeoPoint, val bearing: Float)

/**
 * The extrapolated (median) vehicle position along the trip shape at [nowMs] plus the route shape's
 * tangent there (the vehicle's movement bearing), or null when there's no shape or no usable
 * extrapolation. The single-point counterpart of [buildTripExtrapolation], for the route map's
 * per-vehicle dead reckoning between polls; the bearing keeps the marker's direction arrow following
 * the glide instead of the stale server orientation.
 */
private fun extrapolatedVehicleProjection(state: TripState, nowMs: WallTime): VehicleProjection? {
    val polyline = state.polyline ?: return null
    val distribution = (state.extrapolate(nowMs) as? ExtrapolationResult.Success)?.distribution ?: return null
    val distance = distribution.median().takeIf(Double::isFinite) ?: return null
    // Take the position and the tangent from the same segment so the arrow matches the drawn point.
    val seg = polyline.segmentIndex(distance)
    val point = polyline.interpolate(distance, seg)?.toGeoPoint() ?: return null
    return VehicleProjection(point, polyline.bearingAt(seg))
}

/**
 * Dead-reckoned [ExtrapolatedVehicle]s for every active, non-cancelled trip in [routeTrips] whose
 * currently served route is in [routeIds], using [lookupState] to find each trip's [TripState]. The
 * per-vehicle counterpart of [extrapolationFromState] — the route map's live position producer, a
 * pure function the route controller's vehicle sampler calls each display frame (and that unit tests
 * can call directly without Android).
 *
 * When [directionId] is non-null, only vehicles whose currently-served trip runs that GTFS direction
 * are kept — the "show vehicles on map" filter that drops the opposite-direction buses. Null keeps
 * both directions (the whole-route view).
 *
 * Each vehicle is dead-reckoned forward from its last fix via [extrapolatedVehicleProjection] when the
 * trip's shape is known, else placed at the reported last-known/position point. [ExtrapolatedVehicle.fixTimeMs]
 * is the store's anchor instant (constant between fixes) when extrapolating, else the reported update
 * time, so the renderer animates the marker only across a fresh-AVL jump.
 */
fun extrapolatedVehicles(
    routeTrips: RouteTrips,
    routeIds: Set<String>,
    nowMs: WallTime,
    directionId: Int? = null,
    lookupState: (String?) -> TripState?,
): List<ExtrapolatedVehicle> =
    routeTrips.trips.mapNotNull { trip ->
        val status = trip.status ?: return@mapNotNull null
        val activeTrip = routeTrips.trip(status.activeTripId) ?: return@mapNotNull null
        if (activeTrip.routeId !in routeIds || Status.CANCELED == status.status) return@mapNotNull null
        if (directionId != null && activeTrip.directionId != directionId) return@mapNotNull null
        val state = lookupState(status.activeTripId)
        val projection = state?.let { extrapolatedVehicleProjection(it, nowMs) }
        val point = projection?.point
            ?: status.lastKnownLocation?.toGeoPoint()
            ?: status.position?.toGeoPoint()
            ?: return@mapNotNull null
        // Real-time iff the source of [point] is real-time: an extrapolated point inherits its anchor
        // fix's flag, a point taken from the current status uses that status'. This keeps the marker
        // from reading "scheduled" for a vehicle we're actively tracking/extrapolating (#1621).
        val isRealtime = if (projection != null) {
            state?.anchor?.isLocationRealtime == true
        } else {
            status.isLocationRealtime
        }
        ExtrapolatedVehicle(
            point = point,
            // The path tangent off the shape; NaN off-shape, so the marker falls back to the orientation.
            bearing = projection?.bearing ?: Float.NaN,
            fixTimeMs = state?.let { it.anchorLocalTimeMs.epochMs.takeIf { ms -> ms > 0L } }
                ?: status.lastUpdateTime,
            status = status,
            isRealtime = isRealtime,
        )
    }

/** The point [distance] along [polyline], or null when [distance] is non-finite or off the shape. */
private fun ProbDistribution.pointAlong(polyline: Polyline, distance: Double): GeoPoint? =
    distance.takeIf(Double::isFinite)?.let { polyline.interpolate(it)?.toGeoPoint() }

private fun weightedBandSegments(distribution: ProbDistribution, polyline: Polyline): List<WeightedBandSegment> =
    // Draw the band only out to the fast-estimate marker (the optimistic forward bound), not the full
    // PDF tail — the distribution continues past it, but the overlay stops there.
    uncertaintyBandSlices(distribution, highQuantile = FAST_ESTIMATE_QUANTILE).mapNotNull { slice ->
        val points = polyline.subPolyline(slice.startDist, slice.endDist)?.takeIf { it.size >= 2 }
            ?: return@mapNotNull null
        WeightedBandSegment(points.map(Location::toGeoPoint), slice.alpha.coerceIn(0f, 1f))
    }

private fun dataAgeMarker(state: TripState, nowMs: WallTime): DataAgeMarker? {
    val position = state.anchor?.position ?: return null
    if (state.anchorLocalTimeMs.epochMs <= 0) return null
    // Shown whenever there's a last fix, like the original (no max-age hide); the label is its age.
    // now − anchor is a same-domain (device) Duration; unwrap to raw Long ms for the marker.
    val ageMs = (nowMs - state.anchorLocalTimeMs).inWholeMilliseconds.coerceAtLeast(0L)
    return DataAgeMarker(position.toGeoPoint(), ageMs)
}

private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
