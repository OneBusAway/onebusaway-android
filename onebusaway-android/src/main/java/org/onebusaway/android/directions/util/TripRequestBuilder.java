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

package org.onebusaway.android.directions.util;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.util.RegionUtils;
import org.opentripplanner.api.ws.Request;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.TraverseMode;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TripRequestBuilder {

    private static final String ENCODING = "UTF-8";
    private static final String TAG = "TripRequestBuilder";

    private static final String ARRIVE_BY = ".ARRIVE_BY";
    private static final String FROM_ADDRESS = ".FROM_ADDRESS";
    private static final String TO_ADDRESS = ".TO_ADDRESS";
    private static final String OPTIMIZE_TRANSFERS = ".OPTIMIZE_TRANSFERS";
    private static final String WHEELCHAIR_ACCESSIBLE = ".WHEELCHAIR_ACCESSIBLE";
    private static final String MAX_WALK_DISTANCE = ".MAX_WALK_DISTANCE";
    private static final String MODE_SET = ".MODE_SET";
    private static final String DATE_TIME = ".DATE_TIME";

    private TripRequest.Callback mListener;

    private Bundle mBundle;

    public TripRequestBuilder(Bundle bundle) {
        this.mBundle = bundle;
    }

    public TripRequestBuilder setDepartureTime(Calendar calendar) {
        mBundle.putBoolean(ARRIVE_BY, false);
        setDateTime(calendar.getTime());
        return this;
    }

    public TripRequestBuilder setArrivalTime(Calendar calendar) {
        mBundle.putBoolean(ARRIVE_BY, true);
        setDateTime(calendar.getTime());
        return this;
    }

    // default value is false
    public boolean getArriveBy() {
        Boolean b = mBundle.getBoolean(ARRIVE_BY);
        return b == null ? false : b;
    }

    public TripRequestBuilder setFrom(CustomAddress from) {
        mBundle.putParcelable(FROM_ADDRESS, from);
        return this;
    }

    public CustomAddress getFrom() {
        return (CustomAddress) mBundle.getParcelable(FROM_ADDRESS);
    }

    public CustomAddress getTo() {
        return (CustomAddress) mBundle.getParcelable(TO_ADDRESS);
    }

    public TripRequestBuilder setTo(CustomAddress to) {
        mBundle.putParcelable(TO_ADDRESS, to);
        return this;
    }

    public TripRequestBuilder setListener(TripRequest.Callback listener) {
        this.mListener = listener;
        return this;
    }

    public TripRequestBuilder setOptimizeTransfers(boolean set) {
        mBundle.putSerializable(OPTIMIZE_TRANSFERS, set ? OptimizeType.TRANSFERS : OptimizeType.QUICK);
        return this;
    }

    private OptimizeType getOptimizeType() {
        OptimizeType type = (OptimizeType) mBundle.getSerializable(OPTIMIZE_TRANSFERS);
        return type == null ? OptimizeType.QUICK : type;
    }

    public boolean getOptimizeTransfers() {
        return getOptimizeType() == OptimizeType.TRANSFERS;
    }

    public TripRequestBuilder setWheelchairAccessible(boolean wheelchair) {
        mBundle.putBoolean(WHEELCHAIR_ACCESSIBLE, wheelchair);
        return this;
    }

    public boolean getWheelchairAccessible() {
        return mBundle.getBoolean(WHEELCHAIR_ACCESSIBLE);
    }

    public TripRequestBuilder setMaxWalkDistance(double walkDistance) {
        mBundle.putDouble(MAX_WALK_DISTANCE, walkDistance);
        return this;
    }

    public Double getMaxWalkDistance() {
        Double d = mBundle.getDouble(MAX_WALK_DISTANCE);
        return d != 0 ? d : null;
    }


    // Built in TraverseModeSet does not work properly so we cannot use request.setMode
    // This is built from examining dropdown on the OTP webapp
    // there are also airplane, bike, bike + ride, park + ride, kiss + ride, etc options
    // transit -> TRANSIT,WALK
    //  bus only -> BUSISH,WALK
    // rail only -> TRAINISH,WALK
    public TripRequestBuilder setModeSetById(int id) {
        List<TraverseMode> modes;

        switch (id) {
            case R.string.transit_mode_transit:
                modes = Arrays.asList(TraverseMode.TRANSIT, TraverseMode.WALK);
                break;
            case R.string.transit_mode_bus:
                modes = Arrays.asList(TraverseMode.BUSISH, TraverseMode.WALK);
                break;
            case R.string.transit_mode_rail:
                modes = Arrays.asList(TraverseMode.TRAINISH, TraverseMode.WALK);
                break;
            default:
                Log.e(TAG, "Invalid mode set ID");
                modes = Arrays.asList(TraverseMode.TRANSIT, TraverseMode.WALK);
        }

        String modeString = TextUtils.join(",", modes);

        mBundle.putString(MODE_SET, modeString);

        return this;
    }

    public int getModeSetId() {

        List<TraverseMode> modes = getModes();

        if (modes.contains(TraverseMode.BUSISH)) {
            return R.string.transit_mode_bus;
        }

        if (modes.contains(TraverseMode.TRAINISH)) {
            return R.string.transit_mode_rail;
        }

        if (modes.contains(TraverseMode.TRANSIT)) {
            return R.string.transit_mode_transit;
        }

        return -1;
    }

    private List<TraverseMode> getModes() {
        List<TraverseMode> modes = new ArrayList<>();

        String modeString = mBundle.getString(MODE_SET);

        if (modeString == null) {
            return Arrays.asList(TraverseMode.TRANSIT, TraverseMode.WALK);
        }

        String[] tokens = modeString.split(",");
        for (String tok : tokens) {
            TraverseMode mode = TraverseMode.valueOf(tok);
            modes.add(mode);
        }

        return modes;
    }

    private String getModeString() {
        return mBundle.getString(MODE_SET);
    }

    public TripRequest execute(Activity activity) {


        String from = getAddressString(getFrom());
        String to = getAddressString(getTo());

        if (TextUtils.isEmpty(from) || TextUtils.isEmpty(to)) {
            throw new IllegalArgumentException("Must supply start and end to route between.");
        }

        Request request = new Request();

        request.setArriveBy(getArriveBy());
        request.setFrom(from);
        request.setTo(to);
        request.setOptimize(getOptimizeType());
        request.setWheelchair(getWheelchairAccessible());

        Double maxWalkDistance = getMaxWalkDistance();
        if (maxWalkDistance != null) {
            request.setMaxWalkDistance(maxWalkDistance);
        }

        Date d = getDateTime();
        String date = new SimpleDateFormat(OTPConstants.FORMAT_OTP_SERVER_DATE_REQUEST).format(d);
        String time = new SimpleDateFormat(OTPConstants.FORMAT_OTP_SERVER_TIME_REQUEST).format(d);
        request.setDateTime(date, time);

        // request mode set does not work properly
        String modeString = mBundle.getString(MODE_SET);
        if (modeString != null) {
            request.getParameters().put("mode", modeString);
        }

        // Our default. This could be configurable.
        request.setShowIntermediateStops(true);

        // TripRequest will accept a null value and give a user-friendly error
        String otpBaseUrl = Application.get().getCurrentRegion().getOtpBaseUrl();
        String fmtOtpBaseUrl = otpBaseUrl != null ? RegionUtils.formatOtpBaseUrl(otpBaseUrl) : null;

        TripRequest tripRequest;

        if (activity == null) {
            tripRequest = new TripRequest(fmtOtpBaseUrl, mListener);
        }
        else {
            WeakReference<Activity> ref = new WeakReference<Activity>(activity);
            tripRequest = new TripRequest(fmtOtpBaseUrl, mListener);
        }

        tripRequest.execute(request);

        return tripRequest;
    }

    public void execute() {
        execute(null);
    }

    private String getAddressString(CustomAddress address) {
        if (address == null) {
            return null;
        }

        if (address.hasLatitude() && address.hasLongitude()) {
            double lat = address.getLatitude();
            double lon = address.getLongitude();
            return String.format("%g,%g", lat, lon);
        }
        // Not set via geocoder OR via location service. Use raw string (set in TripPlanFragment to first line of address).
        String line = address.getAddressLine(0);
        try {
            return URLEncoder.encode(line, ENCODING);
        } catch (UnsupportedEncodingException ex) {
            Log.e(TAG, "Error encoding address: " + ex);
            return "";
        }
    }

    public TripRequestBuilder setDateTime(Date d) {
        mBundle.putLong(DATE_TIME, d.getTime());
        return this;
    }

    public TripRequestBuilder setDateTime(Calendar cal) {
        return setDateTime(cal.getTime());
    }

    public Date getDateTime() {
        Long time = mBundle.getLong(DATE_TIME);
        if (time == null || time == 0L) {
            return null;
        }
        else {
            return new Date(time);
        }
    }

    public Bundle getBundle() {
        return mBundle;
    }

    /**
     * Copy all the data from this builder's bundle into another bundle
     * @param target bundle
     */
    public void copyIntoBundle(Bundle target) {
        target.putBoolean(ARRIVE_BY, getArriveBy());
        target.putParcelable(FROM_ADDRESS, getFrom());
        target.putParcelable(TO_ADDRESS, getTo());
        target.putSerializable(OPTIMIZE_TRANSFERS, getOptimizeType());
        target.putBoolean(WHEELCHAIR_ACCESSIBLE, getWheelchairAccessible());
        if (getMaxWalkDistance() != null) {
            target.putDouble(MAX_WALK_DISTANCE, getMaxWalkDistance());
        }
        target.putString(MODE_SET, getModeString());
        if (getDateTime() != null) {
            target.putLong(DATE_TIME, getDateTime().getTime());
        }
    }

    /**
     * Determine whether this trip request can be submitted to an OTP server.
     * @return true if ready to submit, false otherwise
     */
    public boolean ready() {
        return getFrom() != null && getTo() != null && getDateTime() != null;
    }
}

