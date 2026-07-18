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

import org.onebusaway.android.models.RouteTrips
import org.onebusaway.android.extrapolation.data.TripState
import org.onebusaway.android.extrapolation.math.prob.ProbDistribution
import org.onebusaway.android.models.Status
import org.onebusaway.android.map.render.DataAgeMarker
import org.onebusaway.android.util.GeoPoint
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.Polyline
import org.onebusaway.android.util.toGeoPoint

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
    includeMarkers: Boolean = true,
): TripExtrapolation? {
    val polyline = state.polyline ?: return null
    val distribution = (result as? ExtrapolationResult.Success)?.distribution

    return TripExtrapolation(
        // The median estimate + data-age marker are only wanted by a caller that draws them itself; the
        // route map's selected-vehicle overlay (the sole production caller) passes includeMarkers=false —
        // it draws the live vehicle disc + most-recent-data dot separately — so we skip the extra
        // projection + allocation on its ~20–120Hz path (#1752).
        vehiclePoint = if (includeMarkers) distribution?.let { polyline.pointAtDistance(it.median()) } else null,
        fastEstimatePoint = distribution?.let { polyline.pointAtDistance(it.quantile(FAST_ESTIMATE_QUANTILE)) },
        band = distribution?.let { weightedBandSegments(it, polyline) } ?: emptyList(),
        dataAge = if (includeMarkers) dataAgeMarker(state, nowMs) else null,
        // A domain-agnostic *change token*, not a displayed instant: the renderer only compares it for
        // `!=` (a change means fresh AVL data) — never formats or subtracts it. 0 is the token's own
        // "no fix" value (matching the `TripOverlay`/`MapRenderState` field default), not a coerced
        // 1970 timestamp, so the `?: 0L` here is safe where a display-path one would be a bug.
        fixTimeMs = state.anchorLocalTimeMs?.epochMs ?: 0L,
    )
}

/** Composes [TripState.extrapolate] with [buildTripExtrapolation] — the per-frame producer the driver runs. */
internal fun extrapolationFromState(state: TripState?, nowMs: WallTime, includeMarkers: Boolean = true): TripExtrapolation? =
    state?.let { buildTripExtrapolation(it, it.extrapolate(nowMs), nowMs, includeMarkers) }

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
    val point = polyline.interpolate(distance, seg) ?: return null
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
    // Whether to project each vehicle's last fix onto the shape ([dataFixPoint]). Only the discrete,
    // once-per-poll set needs it (the renderer reads the selected vehicle's dot from there); the ~20–120Hz
    // motion sampler leaves it null so the projection stays off the per-frame path — it's constant between
    // fixes and read only for the one selected vehicle, so computing it every frame is pure waste. Placed
    // before [lookupState] so the callback stays last (trailing-lambda call sites keep working).
    includeDataFixPoint: Boolean = false,
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
            state.anchor?.isLocationRealtime == true
        } else {
            status.isLocationRealtime
        }
        ExtrapolatedVehicle(
            point = point,
            // The path tangent off the shape; NaN off-shape, so the marker falls back to the orientation.
            bearing = projection?.bearing ?: Float.NaN,
            fixTimeMs = state?.anchorLocalTimeMs?.epochMs ?: status.lastUpdateTime,
            status = status,
            isRealtime = isRealtime,
            // The last real fix's position on the shape (the glide's seed), so the most-recent-data dot
            // sits at the band's origin rather than at the raw off-shape reported lat/lng (#1752). Only
            // the discrete set path asks for it; the per-frame path leaves it null (see the param).
            dataFixPoint = if (includeDataFixPoint) state?.let(::anchorFixPoint) else null,
        )
    }

/** The point [distance] along this shape, or null when [distance] is non-finite or off the shape. */
private fun Polyline.pointAtDistance(distance: Double): GeoPoint? =
    distance.takeIf(Double::isFinite)?.let { interpolate(it) }

private fun weightedBandSegments(distribution: ProbDistribution, polyline: Polyline): List<WeightedBandSegment> =
    // Draw the band only out to the fast-estimate marker (the optimistic forward bound), not the full
    // PDF tail — the distribution continues past it, but the overlay stops there.
    uncertaintyBandSlices(distribution, highQuantile = FAST_ESTIMATE_QUANTILE).mapNotNull { slice ->
        val points = polyline.subPolyline(slice.startDist, slice.endDist)?.takeIf { it.size >= 2 }
            ?: return@mapNotNull null
        WeightedBandSegment(points, slice.alpha.coerceIn(0f, 1f))
    }

/**
 * The last real fix's position on the route shape: the anchor's distanceAlongTrip — the exact value the
 * glide is seeded from ([TripState.extrapolate]) — projected onto the shape. This keeps the "most recent
 * data" marker pinned to the glide's origin so it can never float ahead of (or off) the glide. Drawing it
 * at the anchor's raw `position` instead (a different, server-extrapolated field than distanceAlongTrip)
 * let the two disagree, so the dot appeared ahead of the glide when they diverged — the regression from
 * a821321a8 ("Remove bestLocation — always use position for display"). Falls back to the reported position
 * only when the fix carries no distance or the shape can't place it. Shared by the trip map's data-age
 * marker and the route map's most-recent-data dot so both draw the dot from one source (#1752).
 */
internal fun anchorFixPoint(state: TripState): GeoPoint? {
    val anchor = state.anchor ?: return null
    return anchor.distanceAlongTrip?.let { dist -> state.polyline?.pointAtDistance(dist) }
        ?: anchor.position?.toGeoPoint()
}

private fun dataAgeMarker(state: TripState, nowMs: WallTime): DataAgeMarker? {
    val anchorLocal = state.anchorLocalTimeMs ?: return null
    val point = anchorFixPoint(state) ?: return null
    // Shown whenever there's a last fix, like the original (no max-age hide); the label is its age.
    // now − anchor is a same-domain (device) Duration; unwrap to raw Long ms for the marker.
    val ageMs = (nowMs - anchorLocal).inWholeMilliseconds.coerceAtLeast(0L)
    return DataAgeMarker(point, ageMs)
}
