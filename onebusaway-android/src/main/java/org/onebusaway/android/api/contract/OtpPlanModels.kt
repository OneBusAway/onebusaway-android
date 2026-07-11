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
import java.io.InputStream
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

/**
 * kotlinx.serialization models for the OpenTripPlanner `/plan` response — the replacement for the
 * old Jackson `JacksonConfig`/`ObjectReader` path (the app's last Jackson consumer). These DTOs are a
 * pure mirror of the wire JSON; [org.onebusaway.android.api.adapters.toTripItinerary] (in
 * `api/adapters/TripPlanAdapters.kt`) maps them onto the app-owned trip-plan domain model
 * (`directions/model/TripItinerary.kt`) that the rest of the app actually consumes. Only the fields the
 * app reads are modeled here; the rest are dropped via `ignoreUnknownKeys`.
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
    val lon: Double? = null,
    val lat: Double? = null,
)

@Serializable
data class OtpLegGeometryDto(
    val points: String? = null,
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

/**
 * The single OTP `/plan` JSON entry point, shared by the legacy Java `TripRequest` AsyncTask and the
 * coroutine [org.onebusaway.android.ui.tripplan.DefaultTripPlanRepository]. Configured like the rest
 * of the modernized stack (`ignoreUnknownKeys` + `coerceInputValues`, mirroring `NetworkModule`), and
 * exposed as a `@JvmStatic` so the remaining Java caller can invoke it directly. Returns the decoded
 * [OtpResponseDto] as-is — callers map `.plan?.itineraries` through
 * [org.onebusaway.android.api.adapters.toTripItinerary] themselves; there's no OTP-library envelope
 * type to project onto anymore.
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
    fun parse(body: String): OtpResponseDto =
        try {
            json.decodeFromString<OtpResponseDto>(body)
        } catch (e: SerializationException) {
            throw IOException("Malformed OTP /plan response", e)
        }

    /**
     * Reads [input] fully as UTF-8 and [parse]s it — the single stream→[OtpResponseDto] entry point,
     * so callers don't each hand-roll the read (and its charset choice).
     */
    @JvmStatic
    @Throws(IOException::class)
    fun parse(input: InputStream): OtpResponseDto = parse(input.readBytes().toString(Charsets.UTF_8))
}
