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
package org.onebusaway.android.report

/**
 * The trip context a "report a problem" launch carries, as plain scalars — every field the report
 * submission actually reads (the Open311 service-report builder + the email/OBA-API problem report).
 * Plain scalars let the whole report flow ride NavHost nav-args (process-death safe via the
 * back-stack) rather than the host activity intent. The nested vehicle/schedule status is flattened
 * ([hasTripStatus] gates the deviation + last-known-location fields, only read when a trip is
 * predicted). Built from the arrivals VM's `ArrivalInfo` via its `toTripReportContext()`.
 */
data class TripReportContext(
    val tripId: String,
    val routeId: String?,
    val shortName: String?,
    val routeLongName: String?,
    val headsign: String?,
    val vehicleId: String?,
    val stopId: String?,
    val serviceDate: Long,
    val predicted: Boolean,
    val predictedArrivalTime: Long,
    val predictedDepartureTime: Long,
    val scheduledArrivalTime: Long,
    val scheduledDepartureTime: Long,
    val hasTripStatus: Boolean,
    val scheduleDeviation: Long,
    val lastKnownLat: Double?,
    val lastKnownLon: Double?,
)

/**
 * The full stop/location/trip context for the report flow (former [org.onebusaway.android.map.MapParams] +
 * `LOCATION_STRING` + `EXTRA_TRIP_INFO`/`AGENCY_NAME`/`BLOCK_ID` host-intent extras). Carried as a
 * single nav-arg via [encode]/[decode] so each report destination reads its own back-stack args.
 */
data class ReportContext(
    val stopId: String? = null,
    val stopName: String? = null,
    val stopCode: String? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val locationString: String? = null,
    val agencyName: String? = null,
    val blockId: String? = null,
    val trip: TripReportContext? = null,
) {

    /**
     * A reversible string for a single nav-arg. Each field is written as `len|value` (or `-1|` for
     * null), so any value (commas, slashes, the report's prose) round-trips without delimiter clashes —
     * pure Kotlin so it's JVM-unit-testable. [decode] reads the fields back in this exact same order.
     * (Navigation handles the outer URL-encoding of the resulting nav-arg.)
     */
    fun encode(): String = buildString {
        fun put(value: String?) {
            if (value == null) append("-1|") else append(value.length).append('|').append(value)
        }
        put(stopId)
        put(stopName)
        put(stopCode)
        put(lat.toString())
        put(lon.toString())
        put(locationString)
        put(agencyName)
        put(blockId)
        put(trip?.tripId)
        put(trip?.routeId)
        put(trip?.shortName)
        put(trip?.routeLongName)
        put(trip?.headsign)
        put(trip?.vehicleId)
        put(trip?.stopId)
        put(trip?.serviceDate?.toString())
        put(trip?.predicted?.toString())
        put(trip?.predictedArrivalTime?.toString())
        put(trip?.predictedDepartureTime?.toString())
        put(trip?.scheduledArrivalTime?.toString())
        put(trip?.scheduledDepartureTime?.toString())
        put(trip?.hasTripStatus?.toString())
        put(trip?.scheduleDeviation?.toString())
        put(trip?.lastKnownLat?.toString())
        put(trip?.lastKnownLon?.toString())
    }

    companion object {
        /** Decodes the [encode] string, or an empty context for null/blank (a context-free report). */
        fun decode(encoded: String?): ReportContext {
            if (encoded.isNullOrEmpty()) return ReportContext()
            // Read each field in the SAME order encode() wrote it — named locals, no positional indices.
            val r = FieldReader(encoded)
            val stopId = r.next()
            val stopName = r.next()
            val stopCode = r.next()
            val lat = r.next()?.toDouble() ?: 0.0
            val lon = r.next()?.toDouble() ?: 0.0
            val locationString = r.next()
            val agencyName = r.next()
            val blockId = r.next()
            val tripId = r.next()
            val routeId = r.next()
            val shortName = r.next()
            val routeLongName = r.next()
            val headsign = r.next()
            val vehicleId = r.next()
            val tripStopId = r.next()
            val serviceDate = r.next()
            val predicted = r.next()
            val predictedArrivalTime = r.next()
            val predictedDepartureTime = r.next()
            val scheduledArrivalTime = r.next()
            val scheduledDepartureTime = r.next()
            val hasTripStatus = r.next()
            val scheduleDeviation = r.next()
            val lastKnownLat = r.next()
            val lastKnownLon = r.next()
            // A trip rides along iff its identifying tripId is present.
            val trip = tripId?.let {
                TripReportContext(
                    tripId = it,
                    routeId = routeId,
                    shortName = shortName,
                    routeLongName = routeLongName,
                    headsign = headsign,
                    vehicleId = vehicleId,
                    stopId = tripStopId,
                    serviceDate = serviceDate?.toLong() ?: 0L,
                    predicted = predicted?.toBoolean() ?: false,
                    predictedArrivalTime = predictedArrivalTime?.toLong() ?: 0L,
                    predictedDepartureTime = predictedDepartureTime?.toLong() ?: 0L,
                    scheduledArrivalTime = scheduledArrivalTime?.toLong() ?: 0L,
                    scheduledDepartureTime = scheduledDepartureTime?.toLong() ?: 0L,
                    hasTripStatus = hasTripStatus?.toBoolean() ?: false,
                    scheduleDeviation = scheduleDeviation?.toLong() ?: 0L,
                    lastKnownLat = lastKnownLat?.toDouble(),
                    lastKnownLon = lastKnownLon?.toDouble(),
                )
            }
            return ReportContext(
                stopId = stopId,
                stopName = stopName,
                stopCode = stopCode,
                lat = lat,
                lon = lon,
                locationString = locationString,
                agencyName = agencyName,
                blockId = blockId,
                trip = trip,
            )
        }
    }

    /** Sequential reader for the [encode] framing: each [next] consumes one `len|value` (or null) field. */
    private class FieldReader(private val encoded: String) {
        private var i = 0

        fun next(): String? {
            val bar = encoded.indexOf('|', i)
            val len = encoded.substring(i, bar).toInt()
            if (len < 0) {
                i = bar + 1
                return null
            }
            val start = bar + 1
            return encoded.substring(start, start + len).also { i = start + len }
        }
    }
}
