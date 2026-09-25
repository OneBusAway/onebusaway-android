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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.onebusaway.android.util.GeoPoint

/**
 * Wire model of an `/api/ondemand` service (the entry of `service/{id}` and each element of the two
 * list endpoints). [serviceKind] and [matchReason] stay wire strings here; the adapter mints the enums
 * with an `UNKNOWN` fallback so a newer server can add a value without breaking decode.
 * [matchReason] is present only on `services-for-location` elements.
 */
@Serializable
data class OnDemandServiceDto(
    val id: String = "",
    val agencyId: String = "",
    val routeId: String? = null,
    val name: String = "",
    val serviceKind: String = "unknown",
    val description: String? = null,
    val url: String? = null,
    val rules: List<AvailabilityRuleDto> = emptyList(),
    val matchReason: String? = null
)

/** One availability rule (wiki §2.2). Times are `"HH:MM:SS"` service-day strings and may exceed 24h. */
@Serializable
data class AvailabilityRuleDto(
    val fromIds: List<String> = emptyList(),
    val toIds: List<String> = emptyList(),
    val startPickupTime: String? = null,
    val endPickupTime: String? = null,
    val endDropOffTime: String? = null,
    val calendarIds: List<String> = emptyList(),
    val pickupType: Int = 0,
    val dropOffType: Int = 0,
    val pickupBookingRuleId: String? = null,
    val dropOffBookingRuleId: String? = null,
    val safeDurationFactor: Double? = null,
    val safeDurationOffset: Double? = null
)

/**
 * A `references.serviceAreas` element. [bbox] is `[minLon, minLat, maxLon, maxLat]` and always present;
 * [geometry] is the raw GeoJSON geometry object, absent at `geometryDetail=none`, and decoded on demand
 * by [polygons] rather than eagerly — a county zone can be hundreds of KB. [distanceToArea] /
 * [nearestPointOnBoundary] (`[lon, lat]`) are non-null only in point-mode `services-for-location`.
 */
@Serializable
data class ServiceAreaDto(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val bbox: List<Double> = emptyList(),
    val geometry: JsonElement? = null,
    val distanceToArea: Double? = null,
    val nearestPointOnBoundary: List<Double>? = null
) {
    /**
     * The geometry as polygons: `[polygon][ring][point]`, ring 0 the exterior and the rest holes, in
     * the ring order the feed published. A `Polygon` yields one polygon, a `MultiPolygon` one per
     * member; a missing or non-polygonal geometry yields none. Malformed coordinates throw, which the
     * data source's failure path reports rather than drawing a half-decoded zone.
     */
    fun polygons(): List<List<List<GeoPoint>>> {
        val geometryObject = geometry as? JsonObject ?: return emptyList()
        val coordinates = geometryObject["coordinates"] as? JsonArray ?: return emptyList()
        return when (geometryObject["type"]?.jsonPrimitive?.contentOrNull) {
            "Polygon" -> listOf(coordinates.toRings())
            "MultiPolygon" -> coordinates.map { it.jsonArray.toRings() }
            else -> emptyList()
        }
    }

    private fun JsonArray.toRings(): List<List<GeoPoint>> = map { ring -> ring.jsonArray.map { it.jsonArray.toGeoPoint() } }

    // GeoJSON positions are [lon, lat].
    private fun JsonArray.toGeoPoint(): GeoPoint = GeoPoint(latitude = this[1].jsonPrimitive.double, longitude = this[0].jsonPrimitive.double)
}

/** A `references.locationGroups` element; members also appear in `references.stops`. */
@Serializable
data class LocationGroupDto(
    val id: String = "",
    val name: String? = null,
    val stopIds: List<String> = emptyList()
)

/** A `references.bookingRules` element — the GTFS-Flex booking vocabulary camelCased (wiki §2.4). */
@Serializable
data class BookingRuleDto(
    val id: String = "",
    val bookingType: Int = 0,
    val priorNoticeDurationMin: Int? = null,
    val priorNoticeDurationMax: Int? = null,
    val priorNoticeLastDay: Int? = null,
    val priorNoticeLastTime: String? = null,
    val priorNoticeStartDay: Int? = null,
    val priorNoticeStartTime: String? = null,
    val priorNoticeCalendarId: String? = null,
    val message: String? = null,
    val pickupMessage: String? = null,
    val dropOffMessage: String? = null,
    val phoneNumber: String? = null,
    val infoUrl: String? = null,
    val bookingUrl: String? = null
)

/** A `references.calendars` element: [days] from `mon..sun`, dates as `YYYY-MM-DD`. */
@Serializable
data class FlexCalendarDto(
    val id: String = "",
    val days: List<String> = emptyList(),
    val startDate: String = "",
    val endDate: String = "",
    val exceptedDates: List<String> = emptyList()
)
