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
import androidx.core.os.BundleCompat
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.TripPlanRequest
import org.onebusaway.android.directions.model.TripMode
import org.onebusaway.android.ui.tripplan.BikePreference
import org.onebusaway.android.ui.tripplan.CyclingPreference
import org.onebusaway.android.ui.tripplan.StreetMode
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.VehicleMode
import org.onebusaway.android.ui.tripplan.WalkPreference
import org.onebusaway.android.ui.tripplan.enumValueOrDefault
import org.onebusaway.android.util.BikeshareAvailability
import org.onebusaway.android.util.RegionUtils

class TripRequestBuilder(context: Context, private val mBundle: Bundle) {

    // Application context, used to read the region / OTP-config seams (bikeshare availability, the OTP
    // base URL) that used to be reached through the Application static.
    private val mContext: Context = context.applicationContext

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
        get() = BundleCompat.getParcelable(mBundle, FROM_ADDRESS, CustomAddress::class.java)

    val to: CustomAddress?
        get() = BundleCompat.getParcelable(mBundle, TO_ADDRESS, CustomAddress::class.java)

    fun setTo(to: CustomAddress): TripRequestBuilder {
        mBundle.putParcelable(TO_ADDRESS, to)
        return this
    }

    fun setOptimizeTransfers(set: Boolean): TripRequestBuilder {
        mBundle.putBoolean(OPTIMIZE_TRANSFERS, set)
        return this
    }

    fun getOptimizeTransfers(): Boolean = mBundle.getBoolean(OPTIMIZE_TRANSFERS)

    /** The OTP `optimize` wire value, derived from [getOptimizeTransfers] only where [buildRequest]
     * needs it — never itself persisted, so it never has to cross a Bundle/Intent serialization
     * boundary. Mirrors OTP1's `OptimizeType.TRANSFERS`/`QUICK` wire names exactly (verified against
     * the vendored jar); the other five `OptimizeType` values are never requested by this app. */
    private fun getOptimizeType(): String = if (getOptimizeTransfers()) "TRANSFERS" else "QUICK"

    fun setWheelchairAccessible(wheelchair: Boolean): TripRequestBuilder {
        mBundle.putBoolean(WHEELCHAIR_ACCESSIBLE, wheelchair)
        return this
    }

    fun getWheelchairAccessible(): Boolean = mBundle.getBoolean(WHEELCHAIR_ACCESSIBLE)

    /**
     * OTP1 only. OTP2 removed `maxWalkDistance` from its routing API, so the OTP2 request path
     * ignores this and reads [getWalkPreference] instead — see [WalkPreference].
     */
    fun setMaxWalkDistance(walkDistance: Double): TripRequestBuilder {
        mBundle.putDouble(MAX_WALK_DISTANCE, walkDistance)
        return this
    }

    fun getMaxWalkDistance(): Double? {
        val d = mBundle.getDouble(MAX_WALK_DISTANCE)
        return if (d != 0.0 && d != Double.MAX_VALUE) d else null
    }

    /**
     * OTP2 only (see [WalkPreference]); the OTP1 request path uses [setMaxWalkDistance]. Stored by
     * enum *name* rather than ordinal so reordering the enum can't silently reinterpret a value
     * already sitting in a saved Bundle or in preferences.
     */
    fun setWalkPreference(preference: WalkPreference): TripRequestBuilder {
        mBundle.putString(WALK_PREFERENCE, preference.name)
        return this
    }

    fun getWalkPreference(): WalkPreference = enumValueOrDefault(mBundle.getString(WALK_PREFERENCE), WalkPreference.MEDIUM)

    /** OTP2 only — OTP1's single `optimize` parameter is already spent on [setOptimizeTransfers]
     * (see [CyclingPreference]). */
    fun setCyclingPreference(preference: CyclingPreference): TripRequestBuilder {
        mBundle.putString(CYCLING_PREFERENCE, preference.name)
        return this
    }

    fun getCyclingPreference(): CyclingPreference = enumValueOrDefault(mBundle.getString(CYCLING_PREFERENCE), CyclingPreference.DEFAULT)

    /** OTP2 only — the nearest thing to a bike-distance setting, which no OTP version has
     * (see [BikePreference]). */
    fun setBikePreference(preference: BikePreference): TripRequestBuilder {
        mBundle.putString(BIKE_PREFERENCE, preference.name)
        return this
    }

    fun getBikePreference(): BikePreference = enumValueOrDefault(mBundle.getString(BIKE_PREFERENCE), BikePreference.MEDIUM)

    /**
     * Records the rider's [TripModeSelection] and, for the OTP1 path, the `mode` query string it
     * composes to (see [otp1ModeTokens]).
     *
     * Both halves are stored in the bundle by enum *name*, so the selection survives the trip-plan
     * monitor's `copyIntoBundleSimple`/`initFromBundleSimple` round trip — the flat mode id it
     * replaced was a plain field that did not.
     */
    fun setModeSelection(selection: TripModeSelection): TripRequestBuilder {
        mBundle.putString(VEHICLE_MODE, selection.vehicle.name)
        mBundle.putString(STREET_MODE, selection.street.name)
        val tokens = otp1ModeTokens(
            selection,
            mContext.getString(R.string.traverse_mode_bicycle_rent),
            BikeshareAvailability.isTripPlanningEnabled(mContext)
        )
        mBundle.putString(MODE_SET, TextUtils.join(",", tokens))
        return this
    }

    /**
     * The stored selection, as this region can serve it — the single point where the request path
     * applies [TripModeSelection.availableIn], so everything downstream may take the result as valid.
     */
    fun getModeSelection(): TripModeSelection = TripModeSelection(
        vehicle = enumValueOrDefault(mBundle.getString(VEHICLE_MODE), VehicleMode.ALL_TRANSIT),
        street = enumValueOrDefault(mBundle.getString(STREET_MODE), StreetMode.WALK)
    ).availableIn(BikeshareAvailability.isTripPlanningEnabled(mContext), usesOtp2)

    private fun getModeString(): String? = mBundle.getString(MODE_SET)

    /**
     * Builds the OTP [TripPlanRequest] from the current bundle state. Consumed by the coroutine
     * trip-plan repository (both the UI plan path and the trip-plan monitor background plan).
     *
     * @throws IllegalArgumentException if the origin or destination is missing
     */
    fun buildRequest(): TripPlanRequest {
        val fromParam = getAddressString(from)
        val toParam = getAddressString(to)

        if (fromParam.isNullOrEmpty() || toParam.isNullOrEmpty()) {
            throw IllegalArgumentException("Must supply start and end to route between.")
        }

        val d = dateTime
            ?: throw IllegalArgumentException("Must supply a date/time to route at.")

        // OTP expects date/time in this format
        val zoned = d.atZone(ZoneId.systemDefault())

        return TripPlanRequest(
            otp1PlanParameters(
                fromPlace = fromParam,
                toPlace = toParam,
                optimize = getOptimizeType(),
                wheelchair = getWheelchairAccessible(),
                arriveBy = arriveBy,
                date = DATE_FORMATTER.format(zoned),
                time = TIME_FORMATTER.format(zoned),
                maxWalkDistanceMeters = getMaxWalkDistance(),
                // Request mode set does not work properly
                modeString = getModeString()
            )
        )
    }

    /** The OTP server this request targets; see [OtpTarget.resolve]. */
    val otpTarget: OtpTarget
        get() = OtpTarget.resolve(mContext)

    /**
     * The [otpTarget] base URL with a scheme ensured and formatted, or null if no server is
     * available.
     */
    val formattedOtpBaseUrl: String?
        get() {
            var otpBaseUrl = otpTarget.usableBaseUrl ?: return null
            try {
                // URI.parse() doesn't tell us if the scheme is missing, so use URL() instead (#126)
                URL(otpBaseUrl)
            } catch (e: MalformedURLException) {
                // Assume HTTPS scheme, since without a scheme the Uri won't parse the authority
                otpBaseUrl = mContext.getString(R.string.https_prefix) + otpBaseUrl
            }
            return RegionUtils.formatOtpBaseUrl(otpBaseUrl)
        }

    /** Whether this request goes through the OTP 2.x GraphQL path rather than OTP1 REST (#1780). */
    val usesOtp2: Boolean
        get() = otpTarget.usesOtp2

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
        target.putBoolean(OPTIMIZE_TRANSFERS, getOptimizeTransfers())
        target.putBoolean(WHEELCHAIR_ACCESSIBLE, getWheelchairAccessible())
        getMaxWalkDistance()?.let { target.putDouble(MAX_WALK_DISTANCE, it) }
        target.putString(WALK_PREFERENCE, getWalkPreference().name)
        target.putString(CYCLING_PREFERENCE, getCyclingPreference().name)
        target.putString(BIKE_PREFERENCE, getBikePreference().name)
        target.putString(VEHICLE_MODE, mBundle.getString(VEHICLE_MODE))
        target.putString(STREET_MODE, mBundle.getString(STREET_MODE))
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
        val fromAddr = checkNotNull(from) { "trip request has no origin" }
        val toAddr = checkNotNull(to) { "trip request has no destination" }
        target.putDouble(FROM_LAT, fromAddr.latitude)
        target.putDouble(FROM_LON, fromAddr.longitude)
        target.putString(FROM_NAME, fromAddr.toString())
        target.putDouble(TO_LAT, toAddr.latitude)
        target.putDouble(TO_LON, toAddr.longitude)
        target.putString(TO_NAME, toAddr.toString())
        target.putBoolean(OPTIMIZE_TRANSFERS, getOptimizeTransfers())
        target.putBoolean(WHEELCHAIR_ACCESSIBLE, getWheelchairAccessible())
        getMaxWalkDistance()?.let { target.putDouble(MAX_WALK_DISTANCE, it) }
        target.putString(WALK_PREFERENCE, getWalkPreference().name)
        target.putString(CYCLING_PREFERENCE, getCyclingPreference().name)
        target.putString(BIKE_PREFERENCE, getBikePreference().name)
        target.putString(VEHICLE_MODE, mBundle.getString(VEHICLE_MODE))
        target.putString(STREET_MODE, mBundle.getString(STREET_MODE))
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
        private const val WALK_PREFERENCE = ".WALK_PREFERENCE"
        private const val CYCLING_PREFERENCE = ".CYCLING_PREFERENCE"
        private const val BIKE_PREFERENCE = ".BIKE_PREFERENCE"
        private const val VEHICLE_MODE = ".VEHICLE_MODE"
        private const val STREET_MODE = ".STREET_MODE"
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

            target.putBoolean(OPTIMIZE_TRANSFERS, bundle.getBoolean(OPTIMIZE_TRANSFERS))
            target.putBoolean(WHEELCHAIR_ACCESSIBLE, bundle.getBoolean(WHEELCHAIR_ACCESSIBLE))
            target.putDouble(MAX_WALK_DISTANCE, bundle.getDouble(MAX_WALK_DISTANCE))
            target.putString(WALK_PREFERENCE, bundle.getString(WALK_PREFERENCE))
            target.putString(CYCLING_PREFERENCE, bundle.getString(CYCLING_PREFERENCE))
            target.putString(BIKE_PREFERENCE, bundle.getString(BIKE_PREFERENCE))

            target.putString(VEHICLE_MODE, bundle.getString(VEHICLE_MODE))
            target.putString(STREET_MODE, bundle.getString(STREET_MODE))
            target.putString(MODE_SET, bundle.getString(MODE_SET))
            target.putLong(DATE_TIME, bundle.getLong(DATE_TIME))

            return TripRequestBuilder(context, target)
        }
    }
}

/**
 * The OTP1 `mode` tokens for [selection] — vehicle then street.
 *
 * OTP1 takes one flat comma-separated mode list, so the two halves are simply concatenated. The
 * mapping reproduces exactly the strings this app sent before the mode split (`TRANSIT,WALK`,
 * `BUS,WALK`, `RAIL,TRAM,WALK`, and those plus `BICYCLE_RENT`), so no OTP1 region sees a changed
 * request for a selection it could already express — which is what `TripRequestBuilderTest` pins.
 *
 * Degrades through [TripModeSelection.availableIn] with `usesOtp2 = false`, which is what makes
 * [StreetMode.BICYCLE] walk here: the rider's own bike has no verified OTP1 equivalent. Stating it
 * that way rather than by hand keeps one copy of the availability rule.
 *
 * The one non-compositional case is a direct bikeshare trip, which historically sent `BICYCLE_RENT`
 * alone rather than `WALK,BICYCLE_RENT`. That exact string is preserved: adding WALK would arguably
 * be more correct, but it is an untested change to every OTP1 region's bikeshare request, and this
 * refactor is not the place to make it.
 */
internal fun otp1ModeTokens(
    selection: TripModeSelection,
    bikeRentalToken: String,
    bikeshareEnabled: Boolean
): List<String> {
    val street = when (selection.availableIn(bikeshareEnabled, usesOtp2 = false).street) {
        StreetMode.WALK_AND_BIKESHARE -> listOf(TripMode.WALK.name, bikeRentalToken)
        StreetMode.WALK, StreetMode.BICYCLE -> listOf(TripMode.WALK.name)
    }
    val vehicle = when (selection.vehicle) {
        VehicleMode.NONE -> emptyList()
        VehicleMode.ALL_TRANSIT -> listOf(TripMode.TRANSIT.name)
        VehicleMode.BUS -> listOf(TripMode.BUS.name)
        // TRAM is included to allow light rail.
        VehicleMode.RAIL -> listOf(TripMode.RAIL.name, TripMode.TRAM.name)
    }
    // Historical exact string for a direct bikeshare trip (see the KDoc above).
    if (vehicle.isEmpty() && street.contains(bikeRentalToken)) {
        return listOf(bikeRentalToken)
    }
    return vehicle + street
}

/**
 * The OTP 1.x REST query parameters, assembled from already-parsed values — the whole of what an OTP1
 * plan request carries. Split out of [TripRequestBuilder.buildRequest] (which stays the thin
 * `Bundle`-reading half) so the wire contract is a pure function a JVM unit test can pin, the way
 * [Otp2PlanRequestBuilder]'s `build*` helpers already are.
 *
 * Worth pinning because the two protocols share one builder while accepting disjoint settings: OTP1
 * takes [maxWalkDistanceMeters], which OTP2 removed from its routing API, and OTP2 takes the street
 * preferences ([WalkPreference]/[BikePreference]/[CyclingPreference]), which have no OTP1 equivalent
 * that doesn't collide with [optimize]. Neither protocol may leak the other's settings into a request
 * the server will reject or silently ignore.
 *
 * @param optimize OTP1's single-valued `optimize` — `TRANSFERS` or `QUICK`; the reason
 * [CyclingPreference] has no OTP1 form, since `SAFE`/`FLAT` here would displace it.
 * @param maxWalkDistanceMeters omitted when null, i.e. when the rider set no cap.
 * @param modeString the built mode-set token list, omitted when unset.
 */
internal fun otp1PlanParameters(
    fromPlace: String,
    toPlace: String,
    optimize: String,
    wheelchair: Boolean,
    arriveBy: Boolean,
    date: String,
    time: String,
    maxWalkDistanceMeters: Double?,
    modeString: String?
): Map<String, String> = buildMap {
    put("fromPlace", fromPlace)
    put("toPlace", toPlace)
    put("optimize", optimize)
    put("wheelchair", wheelchair.toString())
    put("arriveBy", arriveBy.toString())
    put("date", date)
    put("time", time)
    // Our default. This could be configurable.
    put("showIntermediateStops", true.toString())
    maxWalkDistanceMeters?.let { put("maxWalkDistance", it.toString()) }
    modeString?.let { put("mode", it) }
}
