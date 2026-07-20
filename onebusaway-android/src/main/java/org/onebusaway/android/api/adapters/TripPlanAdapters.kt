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
package org.onebusaway.android.api.adapters

import kotlin.time.Duration.Companion.seconds
import org.onebusaway.android.api.contract.OtpItineraryDto
import org.onebusaway.android.api.contract.OtpLegDto
import org.onebusaway.android.api.contract.OtpLegGeometryDto
import org.onebusaway.android.api.contract.OtpPlaceDto
import org.onebusaway.android.api.contract.OtpWalkStepDto
import org.onebusaway.android.directions.model.TripAbsoluteDirection
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.TripLegGeometry
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.directions.model.TripPlace
import org.onebusaway.android.directions.model.TripRelativeDirection
import org.onebusaway.android.directions.model.TripStep
import org.onebusaway.android.directions.model.TripVertexType
import org.onebusaway.android.time.ServerTime

/**
 * Maps the OTP `/plan` wire DTOs (`api/contract/OtpPlanModels.kt`) onto the app-owned trip-plan domain
 * model (`directions/model/TripItinerary.kt`), replacing the old `to…()` mappers that built
 * `org.opentripplanner.api.model.*` POJOs directly. Every enum/`ServerTime`/`Duration` value is minted
 * here, exactly once — see `TripItinerary.kt`'s doc comment for why that matters. This is also the one
 * place that decides a malformed leg/itinerary (missing a place or a time OTP always sends for a
 * well-formed response) is an error rather than something for every downstream reader to null-check —
 * [requireField] throws, which `OtpPlanParser`'s callers already treat the same as any other malformed
 * response.
 *
 * `duration`/`departureDelay`/`arrivalDelay` are seconds on the OTP1 wire (confirmed against the
 * existing consumers before this migration — e.g. `DirectionsGenerator`'s `leg.departureDelay * 1000L`
 * and `TripResultsRepository`'s `durationSec * 1000` both multiply by 1000 to reach milliseconds, so the
 * wire value itself is seconds); only `startTime`/`endTime` are epoch milliseconds.
 */
fun OtpItineraryDto.toTripItinerary(): TripItinerary = TripItinerary(
    duration = (duration?.toLong() ?: 0L).seconds,
    startTime = requireField("itinerary.startTime", startTime?.toLongOrNull()?.let { ServerTime(it) }),
    legs = legs.map { it.toTripLeg() }
)

fun OtpLegDto.toTripLeg(): TripLeg = TripLeg(
    mode = mode.toEnum<TripMode>(),
    route = route,
    routeId = routeId,
    routeShortName = routeShortName,
    routeLongName = routeLongName,
    routeColor = routeColor,
    agencyName = agencyName,
    headsign = headsign,
    tripId = tripId,
    realTime = realTime ?: false,
    distance = distance ?: 0.0,
    duration = (duration?.toLong() ?: 0L).seconds,
    departureDelay = (departureDelay?.toInt() ?: 0).seconds,
    arrivalDelay = (arrivalDelay?.toInt() ?: 0).seconds,
    startTime = requireField("leg.startTime", startTime?.toLongOrNull()?.let { ServerTime(it) }),
    endTime = requireField("leg.endTime", endTime?.toLongOrNull()?.let { ServerTime(it) }),
    from = requireField("leg.from", from?.toTripPlace()),
    to = requireField("leg.to", to?.toTripPlace()),
    intermediateStops = intermediateStops?.map { it.toTripPlace() },
    stop = stop?.map { it.toTripPlace() },
    steps = steps.map { it.toTripStep() },
    legGeometry = legGeometry?.toTripLegGeometry()
)

/**
 * A field every well-formed OTP leg/itinerary carries; its absence means a malformed response.
 * `internal` (not `private`) so `Otp2PlanAdapters.kt` — the same "mint at the boundary" adapter
 * discipline, for OTP2 GraphQL — shares this rather than re-declaring it.
 */
internal fun <T : Any> requireField(name: String, value: T?): T = value ?: error("OTP response missing required field: $name")

fun OtpPlaceDto.toTripPlace(): TripPlace = TripPlace(
    name = name,
    stopCode = stopCode,
    lat = lat,
    lon = lon,
    vertexType = vertexType.toEnum<TripVertexType>(),
    bikeShareId = bikeShareId
)

fun OtpWalkStepDto.toTripStep(): TripStep = TripStep(
    distance = distance ?: 0.0,
    relativeDirection = relativeDirection.toEnum<TripRelativeDirection>(),
    absoluteDirection = absoluteDirection.toEnum<TripAbsoluteDirection>(),
    streetName = streetName,
    exit = exit,
    stayOn = stayOn ?: false,
    lat = lat ?: 0.0,
    lon = lon ?: 0.0
)

fun OtpLegGeometryDto.toTripLegGeometry(): TripLegGeometry = TripLegGeometry(points = points, length = length?.toInt() ?: 0)

/**
 * Decodes an OTP wire enum name, degrading an unknown/absent value to null rather than throwing.
 * `internal` so `Otp2PlanAdapters.kt` shares this too — see [requireField].
 */
internal inline fun <reified E : Enum<E>> String?.toEnum(): E? = this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }
