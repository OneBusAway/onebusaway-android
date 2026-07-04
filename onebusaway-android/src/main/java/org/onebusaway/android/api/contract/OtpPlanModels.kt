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
package org.onebusaway.android.api.contract

import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import org.opentripplanner.api.model.AbsoluteDirection
import org.opentripplanner.api.model.EncodedPolylineBean
import org.opentripplanner.api.model.Itinerary
import org.opentripplanner.api.model.Leg
import org.opentripplanner.api.model.Place
import org.opentripplanner.api.model.RelativeDirection
import org.opentripplanner.api.model.TripPlan
import org.opentripplanner.api.model.VertexType
import org.opentripplanner.api.model.WalkStep
import org.opentripplanner.api.model.error.PlannerError
import org.opentripplanner.api.ws.Response

/**
 * kotlinx.serialization models for the OpenTripPlanner `/plan` response — the replacement for the
 * old Jackson `JacksonConfig`/`ObjectReader` path (the app's last Jackson consumer). Trip planning
 * still flows through the OTP library POJOs (`Itinerary`, `Leg`, `Place`, …) that the directions/
 * UI code reads, so — exactly like [BikeRentalStationsDto] — the DTOs here decode the wire and the
 * `to…()` mappers project onto those POJOs (public fields + no-arg ctors), leaving every downstream
 * consumer untouched. Only the fields the app actually reads are modeled; the rest are dropped via
 * `ignoreUnknownKeys`.
 *
 * Parse both call sites (the legacy `TripRequest` AsyncTask and `DefaultTripPlanRepository`) through
 * [OtpPlanParser].
 */
@Serializable
data class OtpResponseDto(
    val plan: OtpTripPlanDto? = null,
    val error: OtpErrorDto? = null,
)

@Serializable
data class OtpTripPlanDto(
    val itineraries: List<OtpItineraryDto> = emptyList(),
)

@Serializable
data class OtpErrorDto(
    val id: Int = 0,
    val msg: String? = null,
    val message: String? = null,
    val noPath: Boolean = false,
    val missing: List<String> = emptyList(),
)

@Serializable
data class OtpItineraryDto(
    val duration: Double? = null,
    @Serializable(with = WireStringSerializer::class) val startTime: String? = null,
    val legs: List<OtpLegDto> = emptyList(),
)

@Serializable
data class OtpLegDto(
    val mode: String? = null,
    val route: String? = null,
    val routeId: String? = null,
    val routeShortName: String? = null,
    val routeLongName: String? = null,
    val routeColor: String? = null,
    val agencyName: String? = null,
    val agencyTimeZoneOffset: Double? = null,
    val headsign: String? = null,
    val tripId: String? = null,
    val realTime: Boolean? = null,
    val distance: Double? = null,
    val duration: Double? = null,
    val departureDelay: Double? = null,
    val arrivalDelay: Double? = null,
    @Serializable(with = WireStringSerializer::class) val startTime: String? = null,
    @Serializable(with = WireStringSerializer::class) val endTime: String? = null,
    val from: OtpPlaceDto? = null,
    val to: OtpPlaceDto? = null,
    val intermediateStops: List<OtpPlaceDto>? = null,
    val stop: List<OtpPlaceDto>? = null,
    val steps: List<OtpWalkStepDto> = emptyList(),
    val legGeometry: OtpLegGeometryDto? = null,
)

@Serializable
data class OtpPlaceDto(
    val name: String? = null,
    val stopCode: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val vertexType: String? = null,
    val bikeShareId: String? = null,
)

@Serializable
data class OtpWalkStepDto(
    val distance: Double? = null,
    val relativeDirection: String? = null,
    val absoluteDirection: String? = null,
    val streetName: String? = null,
    val exit: String? = null,
    val stayOn: Boolean? = null,
    val bogusName: Boolean? = null,
    val lon: Double? = null,
    val lat: Double? = null,
)

@Serializable
data class OtpLegGeometryDto(
    val points: String? = null,
    val levels: String? = null,
    val length: Double? = null,
)

/**
 * Decodes a value that may arrive as either a JSON number or a quoted string into its raw String
 * form. OTP sends the epoch-millis `startTime`/`endTime` timestamps as JSON numbers, but the legacy
 * POJOs store them as `String` and the callers parse them with `Long.parseLong(...)`. The old
 * Jackson config coerced number→String transparently; this preserves that exact behavior (number or
 * string in, its literal text out) so the downstream `Long.parseLong` path is unchanged.
 */
private object WireStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WireString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Projects the decoded `/plan` response onto the OTP library [Response] the callers expect. */
fun OtpResponseDto.toResponse(): Response = Response().also {
    it.setPlan(plan?.toTripPlan())
    it.setError(error?.toPlannerError())
}

private fun OtpTripPlanDto.toTripPlan(): TripPlan = TripPlan().also {
    it.itineraries = ArrayList(itineraries.map { itinerary -> itinerary.toItinerary() })
}

private fun OtpErrorDto.toPlannerError(): PlannerError = PlannerError().also {
    it.id = id
    it.msg = msg ?: message
    it.setNoPath(noPath)
    it.setMissing(missing)
}

private fun OtpItineraryDto.toItinerary(): Itinerary = Itinerary().also {
    it.duration = duration?.toLong() ?: 0L
    it.startTime = startTime
    it.legs = ArrayList(legs.map { leg -> leg.toLeg() })
}

private fun OtpLegDto.toLeg(): Leg = Leg().also {
    it.mode = mode
    it.route = route
    it.routeId = routeId
    it.routeShortName = routeShortName
    it.routeLongName = routeLongName
    it.routeColor = routeColor
    it.agencyName = agencyName
    it.agencyTimeZoneOffset = agencyTimeZoneOffset?.toInt() ?: 0
    it.headsign = headsign
    it.tripId = tripId
    it.realTime = realTime
    it.distance = distance
    it.duration = duration?.toLong() ?: 0L
    it.departureDelay = departureDelay?.toInt() ?: 0
    it.arrivalDelay = arrivalDelay?.toInt() ?: 0
    it.startTime = startTime
    it.endTime = endTime
    it.from = from?.toPlace()
    it.to = to?.toPlace()
    it.intermediateStops = intermediateStops?.map { place -> place.toPlace() }
    it.stop = stop?.map { place -> place.toPlace() }
    it.steps = steps.map { step -> step.toWalkStep() }
    it.legGeometry = legGeometry?.toBean()
}

private fun OtpPlaceDto.toPlace(): Place = Place().also {
    it.name = name
    it.stopCode = stopCode
    it.lat = lat
    it.lon = lon
    it.vertexType = vertexType.toVertexType()
    it.bikeShareId = bikeShareId
}

private fun OtpWalkStepDto.toWalkStep(): WalkStep = WalkStep().also {
    it.distance = distance ?: 0.0
    it.relativeDirection = relativeDirection?.let { name ->
        runCatching { RelativeDirection.valueOf(name) }.getOrNull()
    }
    it.absoluteDirection = absoluteDirection?.let { name ->
        runCatching { AbsoluteDirection.valueOf(name) }.getOrNull()
    }
    it.streetName = streetName
    it.exit = exit
    it.stayOn = stayOn ?: false
    it.bogusName = bogusName ?: false
    it.lon = lon ?: 0.0
    it.lat = lat ?: 0.0
}

private fun OtpLegGeometryDto.toBean(): EncodedPolylineBean =
    EncodedPolylineBean(points, levels, length?.toInt() ?: 0)

private fun String?.toVertexType(): VertexType? =
    this?.let { runCatching { VertexType.valueOf(it) }.getOrNull() }

/**
 * The single OTP `/plan` JSON entry point, shared by the legacy Java `TripRequest` AsyncTask and the
 * coroutine [org.onebusaway.android.ui.tripplan.DefaultTripPlanRepository]. Configured like the rest
 * of the modernized stack (`ignoreUnknownKeys` + `coerceInputValues`, mirroring `NetworkModule`), and
 * exposed as a `@JvmStatic` so the remaining Java caller can invoke it directly.
 *
 * A malformed body is rethrown as [IOException] rather than the unchecked
 * [SerializationException] `decodeFromString` raises, so both call sites route it through the same
 * `IOException`-based network-failure handling (the Java AsyncTask only catches `IOException`, and the
 * repository maps `IOException` to a user-facing message).
 */
object OtpPlanParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @JvmStatic
    @Throws(IOException::class)
    fun parse(body: String): Response =
        try {
            json.decodeFromString<OtpResponseDto>(body).toResponse()
        } catch (e: SerializationException) {
            throw IOException("Malformed OTP /plan response", e)
        }
}
