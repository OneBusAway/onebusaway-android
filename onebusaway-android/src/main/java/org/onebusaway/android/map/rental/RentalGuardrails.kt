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
package org.onebusaway.android.map.rental

/**
 * The two limits that make a dockless rental layer safe to draw (#2168).
 *
 * `vehicleRentalsByBbox` has **no server-side result limit**: OTP returns every rental entity inside
 * the box, and the one deployment reachable today serves 13,396 of them. The OTP1 endpoint this layer
 * used to read got away with neither limit because it returned docked stations, of which a city has
 * dozens; neither absence survives a dockless fleet. So the viewport is gated *before* the request
 * ([isWithinRentalZoomGate]) and the result is gated *after* it ([RentalDensity]).
 *
 * Both are pure functions of numbers so they are unit-testable without a map.
 */

/**
 * Metres per degree of latitude — the WGS-84 meridian quarter-arc (10,001,965.729 m) over 90°.
 * Latitude is used rather than longitude precisely because it doesn't vary with where you are, so the
 * gate means the same thing in Seattle as at the equator.
 */
private const val METERS_PER_DEGREE_LATITUDE = 111_133.0

/**
 * The tallest viewport the rental layer will fetch for, in metres of north-south extent.
 *
 * 20 km, matching the sibling iOS app's `maxVisibleHeight` for this layer — and deliberately half its
 * 40 km stop gate, because a dockless fleet is one to two orders of magnitude denser than a stop
 * network. Above this the layer draws nothing and asks the rider to zoom in, which is both the only
 * honest thing to show (a screen of 13k overlapping pins conveys nothing) and the only affordable
 * one.
 */
const val RENTAL_MAX_VISIBLE_HEIGHT_METERS = 20_000.0

/**
 * How many rental places the layer will draw at once before it gives up and asks for a smaller
 * viewport. 500, matching iOS's density budget.
 */
const val RENTAL_DENSITY_BUDGET = 500

/** The viewport's north-south extent in metres, from the camera's latitude span. */
fun visibleHeightMeters(latSpan: Double): Double = latSpan * METERS_PER_DEGREE_LATITUDE

/**
 * Whether the viewport is tight enough to fetch rentals for. Checked *before* the request, so a
 * region-wide view costs no round trip at all.
 */
fun isWithinRentalZoomGate(latSpan: Double): Boolean = visibleHeightMeters(latSpan) <= RENTAL_MAX_VISIBLE_HEIGHT_METERS

/**
 * What the layer should do with a fetched set, once the layer toggles and the range filter have
 * already narrowed it.
 *
 * The budget is applied to what will actually be *drawn* rather than to what came back, so a rider
 * looking only at bikes isn't refused because the same box also held ten thousand scooters.
 *
 * When the budget is exceeded the layer draws **nothing** rather than an arbitrary 500 of them: a
 * truncated set is a map that silently lies about where the nearest vehicle is, and the rider acting
 * on it walks to a bike that was never the closest one. Clustering is the richer answer here and the
 * one iOS chose; this app has no clustering in either map flavor, so the honest interim is to say so
 * and ask for a smaller viewport — the same contract the stops layer already has with
 * [org.onebusaway.android.map.StopsBanner.MoreStopsAvailable].
 */
sealed interface RentalDensity {
    /** Within budget: draw these. */
    data class Draw(val places: List<RentalPlace>) : RentalDensity

    /** Over budget: draw nothing and prompt for a tighter viewport. [count] is what was on offer. */
    data class TooMany(val count: Int) : RentalDensity
}

fun rentalDensity(places: List<RentalPlace>, budget: Int = RENTAL_DENSITY_BUDGET): RentalDensity = if (places.size > budget) RentalDensity.TooMany(places.size) else RentalDensity.Draw(places)
