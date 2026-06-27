/*
 * Copyright (C) 2014 University of South Florida,
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
package org.onebusaway.android.ui.report.open311

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import edu.usf.cutr.open311client.Open311
import edu.usf.cutr.open311client.models.Open311User
import edu.usf.cutr.open311client.models.Service
import edu.usf.cutr.open311client.models.ServiceDescription
import edu.usf.cutr.open311client.models.ServiceDescriptionRequest
import edu.usf.cutr.open311client.models.ServiceRequest
import edu.usf.cutr.open311client.utils.Open311Validator
import org.onebusaway.android.R
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.report.TripReportContext
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.report.constants.ReportConstants
import org.onebusaway.android.report.ui.util.ServiceUtils
import org.onebusaway.android.util.BitmapUtils
import org.onebusaway.android.util.MyTextUtils
import org.onebusaway.android.util.PreferenceUtils
import org.onebusaway.android.util.getRouteDisplayName

/** The current map location/address/stop for the issue, read fresh at load and submit time. */
data class Open311IssueContext(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val obaStop: ObaStop?
)

/** Fixed trip context when reporting a transit-trip Open311 issue. */
data class Open311TripContext(
    val trip: TripReportContext,
    val agencyName: String?,
    val blockId: String?
)

/** Loads an Open311 service's dynamic form and submits the completed report. */
interface Open311Repository {

    suspend fun loadForm(): Result<Open311FormState>

    suspend fun submit(form: Open311FormState): Open311SubmitState
}

/**
 * Default implementation quarantining the Open311 client library (edu.usf.cutr.*). Wraps the
 * blocking getServiceDescription/postServiceRequest calls on [Dispatchers.IO], maps via
 * [Open311FormMapper], validates with [Open311Validator], and appends the transit issue
 * parameters — replacing ServiceDescriptionTask + ServiceRequestTask + the fragment's submit code.
 */
class DefaultOpen311Repository(
    private val context: Context,
    private val service: Service,
    private val open311: Open311,
    private val tripContext: Open311TripContext?,
    private val issueProvider: () -> Open311IssueContext
) : Open311Repository {

    /** Cached for submit-time validation (matches the legacy mServiceDescription). */
    private var serviceDescription: ServiceDescription? = null

    override suspend fun loadForm(): Result<Open311FormState> = withContext(Dispatchers.IO) {
        val issue = issueProvider()
        val request = ServiceDescriptionRequest(
            issue.latitude, issue.longitude, open311.jurisdiction, service.service_code
        )
        val description = open311.getServiceDescription(request)
        if (description == null || description.isSuccess != true) {
            return@withContext Result.failure(IOException("Service description unavailable"))
        }
        serviceDescription = description

        val headsign = tripContext?.trip
            ?.takeIf { ServiceUtils.isTransitTripServiceByType(service.type) }
            ?.let { MyTextUtils.formatDisplayText(it.headsign) }

        val mapped = Open311FormMapper.mapForm(
            description = description,
            serviceDescription = service.description,
            isTransitService = ServiceUtils.isTransitServiceByType(service.type),
            stopCode = issue.obaStop?.stopCode,
            isStopIdField = { ServiceUtils.isStopIdField(context, it) }
        )
        Result.success(
            Open311FormState(
                headsign = headsign,
                descriptionLines = mapped.descriptionLines,
                fields = mapped.fields,
                values = mapped.values,
                contact = loadContact()
            )
        )
    }

    override suspend fun submit(form: Open311FormState): Open311SubmitState =
        withContext(Dispatchers.IO) {
            saveContact(form.contact)
            val issue = issueProvider()
            val user = if (form.anonymous) anonymousUser() else form.contact.toUser()

            val builder = ServiceRequest.Builder()
                .setJurisdiction_id(open311.jurisdiction)
                .setService_code(service.service_code)
                .setService_name(service.service_name)
                .setLatitude(issue.latitude)
                .setLongitude(issue.longitude)
                .setSummary(null)
                .setDescription(form.mainDescription)
                .setEmail(user.email)
                .setFirst_name(user.name)
                .setLast_name(user.lastName)
                .setPhone(user.phone)
                .setAddress_string(issue.address?.takeIf { it.isNotEmpty() })
                .setDevice_id(PreferenceUtils.getString(ObaApi.APP_UID))
            form.imagePath?.let { builder.setMedia(downsampleImage(it)) }

            val serviceRequest = builder.createServiceRequest()
            serviceRequest.attributes = Open311FormMapper.toAttributePairs(form.fields, form.values)

            val errorCode = Open311Validator.validateServiceRequest(
                serviceRequest, open311.open311Option.open311Type, serviceDescription
            )
            if (!Open311Validator.isValid(errorCode)) {
                return@withContext Open311SubmitState.ValidationError(
                    Open311Validator.getErrorMessageForServiceRequestByErrorCode(errorCode)
                )
            }

            if (ServiceUtils.isTransitServiceByType(service.type)) {
                serviceRequest.description = form.mainDescription + transitIssueParameters(issue.obaStop)
            }

            val response = open311.postServiceRequest(serviceRequest)
            if (response != null && response.isSuccess == true) {
                Open311SubmitState.Sent
            } else {
                val message = response?.errorMessage?.takeIf { it.isNotEmpty() }
                    ?: context.getString(R.string.ri_unsuccessful_submit)
                Open311SubmitState.ServerError(message)
            }
        }

    private fun loadContact() = ContactInfo(
        firstName = PreferenceUtils.getString(ReportConstants.PREF_NAME).orEmpty(),
        lastName = PreferenceUtils.getString(ReportConstants.PREF_LAST_NAME).orEmpty(),
        email = PreferenceUtils.getString(ReportConstants.PREF_EMAIL).orEmpty(),
        phone = PreferenceUtils.getString(ReportConstants.PREF_PHONE).orEmpty()
    )

    private fun saveContact(contact: ContactInfo) {
        PreferenceUtils.saveString(ReportConstants.PREF_NAME, contact.firstName)
        PreferenceUtils.saveString(ReportConstants.PREF_LAST_NAME, contact.lastName)
        PreferenceUtils.saveString(ReportConstants.PREF_EMAIL, contact.email)
        PreferenceUtils.saveString(ReportConstants.PREF_PHONE, contact.phone)
    }

    private fun ContactInfo.toUser() = Open311User(firstName, lastName, email, phone)

    private fun anonymousUser() = Open311User(
        context.getString(R.string.ri_static_user_name),
        context.getString(R.string.ri_static_user_last_name),
        context.getString(R.string.ri_static_user_email),
        context.getString(R.string.ri_static_user_phone)
    )

    /**
     * Downsamples the captured/picked image to keep uploads small (max ~800px, SeeClickFix limit).
     * Falls back to the full-size file if resizing fails. Ported from the legacy attachImage().
     */
    private fun downsampleImage(imagePath: String): File {
        val target = try {
            BitmapUtils.createImageFile(context, "-small")
        } catch (e: IOException) {
            null
        } ?: return File(imagePath)

        return try {
            val small = BitmapUtils.decodeSampledBitmapFromFile(imagePath, 800, 800)
            FileOutputStream(target).use { out ->
                small.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            target
        } catch (e: IOException) {
            File(imagePath)
        }
    }

    /**
     * Appends GTFS stop/trip parameters to a transit issue description. Returns "" when there is no
     * stop context. Ported verbatim from the legacy getTransitIssueParameters().
     */
    private fun transitIssueParameters(obaStop: ObaStop?): String {
        if (obaStop == null) return ""
        val res = context.resources
        val sb = StringBuilder(res.getString(R.string.ri_append_start))

        if (ServiceUtils.isTransitStopServiceByType(service.type)) {
            sb.append(res.getString(R.string.ri_append_gtfs_stop_id, obaStop.id))
            sb.append(res.getString(R.string.ri_append_stop_name, obaStop.name))
        } else if (ServiceUtils.isTransitTripServiceByType(service.type)) {
            val arrival = tripContext?.trip ?: return sb.toString()
            sb.append(
                res.getString(
                    R.string.ri_append_service_date,
                    SimpleDateFormat("MM-dd-yyyy", Locale.US).format(Date(arrival.serviceDate))
                )
            )
            tripContext.agencyName?.let { sb.append(res.getString(R.string.ri_append_agency_name, it)) }
            sb.append(res.getString(R.string.ri_append_gtfs_stop_id, obaStop.id))
            sb.append(res.getString(R.string.ri_append_stop_name, obaStop.name))
            sb.append(res.getString(R.string.ri_append_route_id, arrival.routeId))
            getRouteDisplayName(arrival.shortName, arrival.routeLongName).takeIf { it.isNotEmpty() }?.let {
                sb.append(res.getString(R.string.ri_append_route_display_name, it))
            }
            tripContext.blockId?.let { sb.append(res.getString(R.string.ri_append_block_id, it)) }
            sb.append(res.getString(R.string.ri_append_trip_id, arrival.tripId))
            sb.append(res.getString(R.string.ri_append_trip_name, arrival.headsign))
            sb.append(res.getString(R.string.ri_append_predicted, arrival.predicted))

            if (arrival.hasTripStatus && arrival.predicted) {
                sb.append(res.getString(R.string.ri_append_vehicle_id, arrival.vehicleId))
                if (arrival.lastKnownLat != null && arrival.lastKnownLon != null) {
                    sb.append(
                        res.getString(
                            R.string.ri_append_vehicle_location,
                            "${arrival.lastKnownLat} ${arrival.lastKnownLon}"
                        )
                    )
                }
                val numberFormat = DecimalFormat("#.000")
                val deviation = arrival.scheduleDeviation / 60.0
                when {
                    deviation == 0.0 ->
                        sb.append(res.getString(R.string.ri_append_schedule_deviation, "0"))

                    deviation < 0 -> sb.append(
                        res.getString(
                            R.string.ri_append_schedule_deviation_early,
                            numberFormat.format(deviation * -1.0)
                        )
                    )

                    else -> sb.append(
                        res.getString(
                            R.string.ri_append_schedule_deviation_late,
                            numberFormat.format(deviation)
                        )
                    )
                }
            }

            val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
            if (arrival.predicted) {
                sb.append(res.getString(R.string.ri_append_arrival_time, timeFormat.format(Date(arrival.predictedArrivalTime))))
                sb.append(res.getString(R.string.ri_append_departure_time, timeFormat.format(Date(arrival.predictedDepartureTime))))
            } else {
                sb.append(res.getString(R.string.ri_append_arrival_time, timeFormat.format(Date(arrival.scheduledArrivalTime))))
                sb.append(res.getString(R.string.ri_append_departure_time, timeFormat.format(Date(arrival.scheduledDepartureTime))))
            }
        }
        return sb.toString()
    }
}
