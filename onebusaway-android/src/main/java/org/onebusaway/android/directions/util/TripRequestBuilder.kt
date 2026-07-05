/**
 * Copyright (C) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.directions.util

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.ui.tripplan.TripModes
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.util.RegionUtils
import org.opentripplanner.api.ws.Request
import org.opentripplanner.routing.core.OptimizeType
import org.opentripplanner.routing.core.TraverseMode
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TripRequestBuilder(context: Context, private val mBundle: Bundle) {

    // Application context, used to read the region / OTP-config seams (bikeshare availability, the OTP
    // base URL) that used to be reached through the Application static.
    private val mContext: Context = context.applicationContext

    private var mModeId = 0

    fun setDepartureTime(instant: Instant): TripRequestBuilder {
        mBundle.putBoolean(ARRIVE_BY, false)
        setDateTime(instant)
        return this
    }

    fun setArrivalTime(instant: Instant): TripRequestBuilder {
        mBundle.putBoolean(ARRIVE_BY, true)
        setDateTime(instant)
        return this
    }

    // default value is false
    val arriveBy: Boolean
        get() = mBundle.getBoolean(ARRIVE_BY)

    fun setFrom(from: CustomAddress): TripRequestBuilder {
        mBundle.putParcelable(FROM_ADDRESS, from)
        return this
    }

    val from: CustomAddress?
        get() = mBundle.getParcelable(FROM_ADDRESS)

    val to: CustomAddress?
        get() = mBundle.getParcelable(TO_ADDRESS)

    fun setTo(to: CustomAddress): TripRequestBuilder {
        mBundle.putParcelable(TO_ADDRESS, to)
        return this
    }

    fun setOptimizeTransfers(set: Boolean): TripRequestBuilder {
        mBundle.putSerializable(
            OPTIMIZE_TRANSFERS, if (set) OptimizeType.TRANSFERS else OptimizeType.QUICK
        )
        return this
    }

    private fun getOptimizeType(): OptimizeType {
        val type = mBundle.getSerializable(OPTIMIZE_TRANSFERS) as? OptimizeType
        return type ?: OptimizeType.QUICK
    }

    fun getOptimizeTransfers(): Boolean = getOptimizeType() == OptimizeType.TRANSFERS

    fun setWheelchairAccessible(wheelchair: Boolean): TripRequestBuilder {
        mBundle.putBoolean(WHEELCHAIR_ACCESSIBLE, wheelchair)
        return this
    }

    fun getWheelchairAccessible(): Boolean = mBundle.getBoolean(WHEELCHAIR_ACCESSIBLE)

    fun setMaxWalkDistance(walkDistance: Double): TripRequestBuilder {
        mBundle.putDouble(MAX_WALK_DISTANCE, walkDistance)
        return this
    }

    fun getMaxWalkDistance(): Double? {
        val d = mBundle.getDouble(MAX_WALK_DISTANCE)
        return if (d != 0.0 && d != Double.MAX_VALUE) d else null
    }

    // Built in TraverseModeSet does not work properly so we cannot use request.setMode
    // This is built from examining dropdown on the OTP webapp
    // there are also airplane, bike, bike + ride, park + ride, kiss + ride, etc options
    // transit -> TRANSIT,WALK
    // bus only -> BUS,WALK
    // rail only -> RAIL,TRAM,WALK (TRAM is included to allow light rail)
    fun setModeSetById(id: Int): TripRequestBuilder {
        mModeId = id
        val modes: List<String> = when (id) {
            // Transit only
            TripModes.TRANSIT_ONLY ->
                listOf(TraverseMode.TRANSIT.toString(), TraverseMode.WALK.toString())
            // Transit & bikeshare
            TripModes.TRANSIT_AND_BIKE ->
                if (BikeshareAvailability.isEnabled(mContext)) {
                    listOf(
                        TraverseMode.TRANSIT.toString(),
                        TraverseMode.WALK.toString(),
                        mContext.getString(R.string.traverse_mode_bicycle_rent)
                    )
                } else {
                    listOf(TraverseMode.TRANSIT.toString(), TraverseMode.WALK.toString())
                }

            TripModes.BUS_ONLY ->
                listOf(TraverseMode.BUS.toString(), TraverseMode.WALK.toString())

            TripModes.RAIL_ONLY ->
                listOf(
                    TraverseMode.RAIL.toString(),
                    TraverseMode.TRAM.toString(),
                    TraverseMode.WALK.toString()
                )

            TripModes.BIKESHARE ->
                listOf(mContext.getString(R.string.traverse_mode_bicycle_rent))

            else -> {
                Log.e(TAG, "Invalid mode set ID")
                mModeId = -1
                listOf(TraverseMode.TRANSIT.toString(), TraverseMode.WALK.toString())
            }
        }

        val modeString = TextUtils.join(",", modes)
        mBundle.putString(MODE_SET, modeString)
        return this
    }

    fun getModeSetId(): Int {
        // IF bike mode is selected in the trip plan additional preferences but bikeshare is not
        // enabled use the default mode (TRANSIT)
        if (TripModes.BIKESHARE == mModeId && !BikeshareAvailability.isEnabled(mContext)) {
            return TripModes.TRANSIT_ONLY
        }
        return mModeId
    }

    private fun getModeString(): String? = mBundle.getString(MODE_SET)

    /**
     * Builds the OTP [Request] from the current bundle state. Consumed by the coroutine
     * trip-plan repository (both the UI plan path and the RealtimeService background plan).
     *
     * @throws IllegalArgumentException if the origin or destination is missing
     */
    fun buildRequest(): Request {
        val fromParam = getAddressString(from)
        val toParam = getAddressString(to)

        if (TextUtils.isEmpty(fromParam) || TextUtils.isEmpty(toParam)) {
            throw IllegalArgumentException("Must supply start and end to route between.")
        }

        val request = Request()
        request.setArriveBy(arriveBy)
        request.setFrom(fromParam)
        request.setTo(toParam)
        request.setOptimize(getOptimizeType())
        request.setWheelchair(getWheelchairAccessible())

        getMaxWalkDistance()?.let { request.setMaxWalkDistance(it) }

        val d = dateTime
            ?: throw IllegalArgumentException("Must supply a date/time to route at.")

        // OTP expects date/time in this format
        val zoned = d.atZone(ZoneId.systemDefault())
        request.setDateTime(DATE_FORMATTER.format(zoned), TIME_FORMATTER.format(zoned))

        // Request mode set does not work properly
        val modeString = mBundle.getString(MODE_SET)
        if (modeString != null) {
            request.parameters["mode"] = modeString
        }

        // Our default. This could be configurable.
        request.setShowIntermediateStops(true)

        return request
    }

    /**
     * Resolves and formats the OTP base URL (the user's custom URL if set, otherwise the current
     * region's), or null if neither is available.
     */
    val formattedOtpBaseUrl: String?
        get() {
            var otpBaseUrl: String?
            val customOtpApiUrl = PreferencesEntryPoint.get(mContext)
                .getString(mContext.getString(R.string.preference_key_otp_api_url), null as String?)
            if (!TextUtils.isEmpty(customOtpApiUrl)) {
                otpBaseUrl = customOtpApiUrl
                Log.d(TAG, "Using custom OTP API URL set by user '$otpBaseUrl'.")
            } else {
                // No custom URL and no selected region: return null so the caller
                // (TripPlanRepository) surfaces a "no server selected" error instead of crashing.
                val region = RegionEntryPoint.get(mContext).currentRegion() ?: return null
                otpBaseUrl = region.otpBaseUrl
            }
            try {
                // URI.parse() doesn't tell us if the scheme is missing, so use URL() instead (#126)
                URL(otpBaseUrl)
            } catch (e: MalformedURLException) {
                // Assume HTTPS scheme, since without a scheme the Uri won't parse the authority
                otpBaseUrl = mContext.getString(R.string.https_prefix) + otpBaseUrl
            }
            return if (otpBaseUrl != null) RegionUtils.formatOtpBaseUrl(otpBaseUrl) else null
        }

    private fun getAddressString(address: CustomAddress?): String? {
        if (address == null) {
            return null
        }

        if (address.hasLatitude() && address.hasLongitude()) {
            val lat = address.latitude
            val lon = address.longitude
            return String.format(OTPConstants.OTP_LOCALE, "%g,%g", lat, lon)
        }
        // Not set via geocoder OR via location service. Use raw string (set in TripPlanFragment to
        // first line of address).
        val line = address.getAddressLine(0)
        return try {
            URLEncoder.encode(line, ENCODING)
        } catch (ex: UnsupportedEncodingException) {
            Log.e(TAG, "Error encoding address: $ex")
            ""
        }
    }

    fun setDateTime(instant: Instant): TripRequestBuilder {
        mBundle.putLong(DATE_TIME, instant.toEpochMilli())
        return this
    }

    val dateTime: Instant?
        get() {
            val time = mBundle.getLong(DATE_TIME)
            return if (time == 0L) null else Instant.ofEpochMilli(time)
        }

    fun getBundle(): Bundle = mBundle

    /**
     * Copy all the data from this builder's bundle into another bundle
     * @param target bundle
     */
    fun copyIntoBundle(target: Bundle) {
        target.putBoolean(ARRIVE_BY, arriveBy)
        target.putParcelable(FROM_ADDRESS, from)
        target.putParcelable(TO_ADDRESS, to)
        target.putSerializable(OPTIMIZE_TRANSFERS, getOptimizeType())
        target.putBoolean(WHEELCHAIR_ACCESSIBLE, getWheelchairAccessible())
        getMaxWalkDistance()?.let { target.putDouble(MAX_WALK_DISTANCE, it) }
        target.putString(MODE_SET, getModeString())
        dateTime?.let { target.putLong(DATE_TIME, it.toEpochMilli()) }
    }

    /**
     * Copy all the data from this builder's bundle into another bundle, but only use simple data
     * types
     * @param target bundle
     */
    fun copyIntoBundleSimple(target: Bundle) {
        target.putBoolean(ARRIVE_BY, arriveBy)
        val fromAddr = from
        val toAddr = to
        target.putDouble(FROM_LAT, fromAddr!!.latitude)
        target.putDouble(FROM_LON, fromAddr.longitude)
        target.putString(FROM_NAME, fromAddr.toString())
        target.putDouble(TO_LAT, toAddr!!.latitude)
        target.putDouble(TO_LON, toAddr.longitude)
        target.putString(TO_NAME, toAddr.toString())
        target.putString(OPTIMIZE_TRANSFERS, getOptimizeType().toString())
        target.putBoolean(WHEELCHAIR_ACCESSIBLE, getWheelchairAccessible())
        getMaxWalkDistance()?.let { target.putDouble(MAX_WALK_DISTANCE, it) }
        target.putString(MODE_SET, getModeString())
        dateTime?.let { target.putLong(DATE_TIME, it.toEpochMilli()) }
    }

    /**
     * Determine whether this trip request can be submitted to an OTP server.
     * @return true if ready to submit, false otherwise
     */
    fun ready(): Boolean {
        val f = from
        val t = to
        return f != null && f.isSet && t != null && t.isSet && dateTime != null
    }

    companion object {

        private const val ENCODING = "UTF-8"
        private const val TAG = "TripRequestBuilder"

        private const val ARRIVE_BY = ".ARRIVE_BY"
        private const val FROM_ADDRESS = ".FROM_ADDRESS"
        private const val FROM_LAT = ".FROM_LAT"
        private const val FROM_LON = ".FROM_LON"
        private const val FROM_NAME = ".FROM_NAME"
        private const val TO_ADDRESS = ".TO_ADDRESS"
        private const val TO_LAT = ".TO_LAT"
        private const val TO_LON = ".TO_LON"
        private const val TO_NAME = ".TO_NAME"
        private const val OPTIMIZE_TRANSFERS = ".OPTIMIZE_TRANSFERS"
        private const val WHEELCHAIR_ACCESSIBLE = ".WHEELCHAIR_ACCESSIBLE"
        private const val MAX_WALK_DISTANCE = ".MAX_WALK_DISTANCE"
        private const val MODE_SET = ".MODE_SET"
        private const val DATE_TIME = ".DATE_TIME"

        // Pattern parsing is the expensive part of DateTimeFormatter, and the two OTP request
        // patterns are constant, so build the formatters once.
        private val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern(OTPConstants.FORMAT_OTP_SERVER_DATE_REQUEST, OTPConstants.OTP_LOCALE)
        private val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern(OTPConstants.FORMAT_OTP_SERVER_TIME_REQUEST, OTPConstants.OTP_LOCALE)

        /**
         * Initialize from a BaseBundle
         */
        @JvmStatic
        fun initFromBundleSimple(context: Context, bundle: Bundle): TripRequestBuilder {
            val target = Bundle()
            target.putBoolean(ARRIVE_BY, bundle.getBoolean(ARRIVE_BY))

            val from = CustomAddress()
            from.latitude = bundle.getDouble(FROM_LAT)
            from.longitude = bundle.getDouble(FROM_LON)
            from.setAddressLine(0, bundle.getString(FROM_NAME))
            val to = CustomAddress()
            to.latitude = bundle.getDouble(TO_LAT)
            to.longitude = bundle.getDouble(TO_LON)
            to.setAddressLine(0, bundle.getString(TO_NAME))

            target.putParcelable(FROM_ADDRESS, from)
            target.putParcelable(TO_ADDRESS, to)

            val optName = bundle.getString(OPTIMIZE_TRANSFERS)
            if (optName != null) {
                target.putSerializable(OPTIMIZE_TRANSFERS, OptimizeType.valueOf(optName))
            }

            target.putBoolean(WHEELCHAIR_ACCESSIBLE, bundle.getBoolean(WHEELCHAIR_ACCESSIBLE))
            target.putDouble(MAX_WALK_DISTANCE, bundle.getDouble(MAX_WALK_DISTANCE))

            target.putString(MODE_SET, bundle.getString(MODE_SET))
            target.putLong(DATE_TIME, bundle.getLong(DATE_TIME))

            return TripRequestBuilder(context, target)
        }
    }
}
