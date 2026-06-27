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
package org.onebusaway.android.ui.tripinfo

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.format.DateUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.onebusaway.android.R
import org.onebusaway.android.app.Application
import org.onebusaway.android.api.contract.ReminderWebService
import org.onebusaway.android.provider.ObaContract
import org.onebusaway.android.provider.ProviderQueries
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.ReminderUtils

/** The reminder lead times (minutes) backing each spinner position, as stored in the Trips table. */
internal val REMINDER_MINUTES = listOf(1, 3, 5, 10, 15, 20, 25, 30)

/**
 * The launch parameters of the Trip Info screen. The arrivals "set reminder" action passes the full
 * set; editing an existing reminder passes only [tripId]/[stopId] and the rest is read from the
 * Trips table by [TripInfoRepository.load].
 */
data class TripInfoArgs(
    val tripId: String,
    val stopId: String,
    val routeId: String? = null,
    val routeName: String? = null,
    val stopName: String? = null,
    val headsign: String? = null,
    val departTime: Long = 0,
    val stopSequence: Int = 0,
    val serviceDate: Long = 0,
    val vehicleId: String? = null
) {

    val tripUri: Uri = ObaContract.Trips.buildUri(tripId, stopId)
}

/**
 * The resolved trip-reminder data: [TripInfoArgs] merged with any existing Trips row (args win),
 * plus the display strings for the form header and the valid reminder options for this departure.
 */
data class TripInfoData(
    // Raw values, persisted back to the Trips table on save.
    val routeId: String?,
    val headsign: String?,
    val departTime: Long,
    val stopSequence: Int,
    val serviceDate: Long,
    val vehicleId: String?,
    val tripName: String,
    val reminderMinutes: Int,
    val isNewTrip: Boolean,
    // Display-ready strings for the form.
    val stopNameText: String,
    val routeText: String,
    val headsignText: String,
    val departureText: String,
    val reminderOptions: List<String>
)

interface TripInfoRepository {

    /** Loads and merges the reminder data for [args] (see [TripInfoData]). */
    suspend fun load(args: TripInfoArgs): TripInfoData

    /**
     * Registers the reminder alarm with the region's server and, on success, saves the trip row.
     * Replaces any existing alarm for this trip first. Returns false if the alarm could not be set.
     */
    suspend fun save(args: TripInfoArgs, data: TripInfoData, reminderMinutes: Int, tripName: String): Boolean
}

class DefaultTripInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val reminderService: ReminderWebService,
) : TripInfoRepository {

    override suspend fun load(args: TripInfoArgs): TripInfoData = withContext(Dispatchers.IO) {
        // If the launcher passed a route name, refresh it in the Routes table (legacy behavior).
        if (args.routeId != null && args.routeName != null) {
            val values = ContentValues().apply { put(ObaContract.Routes.SHORTNAME, args.routeName) }
            ObaContract.Routes.insertOrUpdate(context, args.routeId, values, false)
        }
        val fromDb = context.contentResolver
            .query(args.tripUri, PROJECTION, null, null, null)
            ?.use { if (it.moveToFirst()) it.toExistingTrip(args) else null }
        fromDb ?: newTrip(args)
    }

    /** Merges an existing Trips row with [args] (args win), looking up any still-missing names. */
    private fun Cursor.toExistingTrip(args: TripInfoArgs): TripInfoData {
        val routeId = args.routeId ?: getString(COL_ROUTE_ID)
        val departTime = if (args.departTime != 0L) {
            args.departTime
        } else {
            ObaContract.Trips.convertDBToTime(getInt(COL_DEPARTURE))
        }
        val routeName = args.routeName ?: ReminderUtils.getRouteShortName(context, routeId)
        val stopName = args.stopName ?: ProviderQueries.stringForQuery(
            context,
            Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, args.stopId),
            ObaContract.Stops.NAME
        )
        return toTripInfoData(
            routeId = routeId,
            routeName = routeName,
            stopName = stopName,
            headsign = args.headsign ?: getString(COL_HEADSIGN),
            departTime = departTime,
            stopSequence = if (args.stopSequence != 0) args.stopSequence else getInt(COL_STOP_SEQUENCE),
            serviceDate = if (args.serviceDate != 0L) args.serviceDate else getLong(COL_SERVICE_DATE),
            vehicleId = args.vehicleId ?: getString(COL_VEHICLE_ID),
            tripName = getString(COL_NAME).orEmpty(),
            reminderMinutes = getInt(COL_REMINDER),
            isNewTrip = false
        )
    }

    private fun newTrip(args: TripInfoArgs): TripInfoData = toTripInfoData(
        routeId = args.routeId,
        routeName = args.routeName,
        stopName = args.stopName,
        headsign = args.headsign,
        departTime = args.departTime,
        stopSequence = args.stopSequence,
        serviceDate = args.serviceDate,
        vehicleId = args.vehicleId,
        tripName = "",
        reminderMinutes = PreferenceUtils.getInt(
            context.getString(R.string.preference_key_default_reminder_time), DEFAULT_REMINDER_MINUTES
        ),
        isNewTrip = true
    )

    private fun toTripInfoData(
        routeId: String?,
        routeName: String?,
        stopName: String?,
        headsign: String?,
        departTime: Long,
        stopSequence: Int,
        serviceDate: Long,
        vehicleId: String?,
        tripName: String,
        reminderMinutes: Int,
        isNewTrip: Boolean
    ) = TripInfoData(
        routeId = routeId,
        headsign = headsign,
        departTime = departTime,
        stopSequence = stopSequence,
        serviceDate = serviceDate,
        vehicleId = vehicleId,
        tripName = tripName,
        reminderMinutes = reminderMinutes,
        isNewTrip = isNewTrip,
        stopNameText = MyTextUtils.formatDisplayText(stopName.orEmpty()).orEmpty(),
        routeText = MyTextUtils.formatDisplayText(context.getString(R.string.trip_info_route, routeName.orEmpty())).orEmpty(),
        headsignText = MyTextUtils.formatDisplayText(headsign.orEmpty()).orEmpty(),
        departureText = context.getString(
            R.string.trip_info_depart,
            DateUtils.formatDateTime(
                context, departTime,
                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_NO_NOON or DateUtils.FORMAT_NO_MIDNIGHT
            )
        ),
        reminderOptions = ReminderUtils.getReminderTimes(context, departTime).toList()
    )

    override suspend fun save(
        args: TripInfoArgs,
        data: TripInfoData,
        reminderMinutes: Int,
        tripName: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Replace any existing alarm for this trip before registering the new one.
        if (ReminderUtils.isAlarmExist(context, args.tripUri)) {
            ReminderUtils.requestDeleteAlarm(context, args.tripUri)
        }
        PreferenceUtils.saveInt(
            context.getString(R.string.preference_key_default_reminder_time), reminderMinutes
        )
        val alarmDeletePath = requestAlarm(args, data, reminderMinutes * 60) ?: return@withContext false
        saveTrip(args, data, reminderMinutes, tripName, alarmDeletePath)
        true
    }

    /** Registers the alarm with the region's server; returns its delete URL, or null on failure. */
    private suspend fun requestAlarm(
        args: TripInfoArgs,
        data: TripInfoData,
        reminderSeconds: Int
    ): String? {
        val region = regionRepository.region.value ?: return null
        val base = region.sidecarBaseUrl ?: return null
        // The push id is the one required field not guaranteed by the typed args (it's absent until
        // push registration completes); the legacy builder returned null in that case.
        val userPushId = Application.getUserPushID()
        if (userPushId.isNullOrEmpty()) return null
        val url = base + context.getString(R.string.arrivals_reminders_api_endpoint) +
            region.id + "/alarms"
        return runCatching {
            reminderService.createAlarm(
                url = url,
                stopId = args.stopId,
                serviceDate = data.serviceDate,
                stopSequence = data.stopSequence,
                tripId = args.tripId,
                userPushId = userPushId,
                secondsBefore = reminderSeconds,
                vehicleId = data.vehicleId,
            ).url
        }.getOrNull()
    }

    private fun saveTrip(
        args: TripInfoArgs,
        data: TripInfoData,
        reminderMinutes: Int,
        tripName: String,
        alarmDeletePath: String
    ) {
        val values = ContentValues().apply {
            put(ObaContract.Trips._ID, args.tripId)
            put(ObaContract.Trips.TRIP_ID, args.tripId)
            put(ObaContract.Trips.STOP_ID, args.stopId)
            put(ObaContract.Trips.ROUTE_ID, data.routeId)
            put(ObaContract.Trips.DEPARTURE, ObaContract.Trips.convertTimeToDB(data.departTime))
            put(ObaContract.Trips.HEADSIGN, data.headsign)
            put(ObaContract.Trips.NAME, tripName)
            put(ObaContract.Trips.REMINDER, reminderMinutes)
            put(ObaContract.Trips.ALARM_DELETE_PATH, alarmDeletePath)
            put(ObaContract.Trips.SERVICE_DATE, data.serviceDate)
            put(ObaContract.Trips.STOP_SEQUENCE, data.stopSequence)
            put(ObaContract.Trips.VEHICLE_ID, data.vehicleId)
        }
        context.contentResolver.insert(ObaContract.Trips.CONTENT_URI, values)
    }

    companion object {

        private const val DEFAULT_REMINDER_MINUTES = 10

        private val PROJECTION = arrayOf(
            ObaContract.Trips.NAME,
            ObaContract.Trips.REMINDER,
            ObaContract.Trips.ROUTE_ID,
            ObaContract.Trips.HEADSIGN,
            ObaContract.Trips.DEPARTURE,
            ObaContract.Trips.STOP_SEQUENCE,
            ObaContract.Trips.SERVICE_DATE,
            ObaContract.Trips.VEHICLE_ID
        )

        private const val COL_NAME = 0
        private const val COL_REMINDER = 1
        private const val COL_ROUTE_ID = 2
        private const val COL_HEADSIGN = 3
        private const val COL_DEPARTURE = 4
        private const val COL_STOP_SEQUENCE = 5
        private const val COL_SERVICE_DATE = 6
        private const val COL_VEHICLE_ID = 7
    }
}
