/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.seattlebusbot;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.joulespersecond.oba.ObaAnalytics;
import com.joulespersecond.oba.elements.ObaArrivalInfo;
import com.joulespersecond.oba.request.ObaReportProblemWithTripRequest;
import com.joulespersecond.seattlebusbot.util.LocationHelp;
import com.joulespersecond.seattlebusbot.util.MyTextUtils;

public class ReportTripProblemFragment extends ReportProblemFragmentBase {
    //private static final String TAG = "ReportStopProblemFragment";

    private static final String TRIP_ID = ".TripId";

    private static final String STOP_ID = ".StopId";

    private static final String TRIP_NAME = ".TripName";

    private static final String TRIP_SERVICE_DATE = ".ServiceDate";

    private static final String TRIP_VEHICLE_ID = ".VehicleId";

    private static final String CODE = ".Code";

    private static final String USER_COMMENT = ".UserComment";

    private static final String USER_ON_VEHICLE = ".UserOnVehicle";

    private static final String USER_VEHICLE_NUM = ".UserVehicleNum";

    static void show(SherlockFragmentActivity activity, ObaArrivalInfo arrival) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putString(TRIP_ID, arrival.getTripId());
        args.putString(STOP_ID, arrival.getStopId());
        // We don't use the stop name map here...we want the actual stop name.
        args.putString(TRIP_NAME, arrival.getHeadsign());
        args.putLong(TRIP_SERVICE_DATE, arrival.getServiceDate());
        args.putString(TRIP_VEHICLE_ID, arrival.getVehicleId());

        // Create the list fragment and add it as our sole content.
        ReportTripProblemFragment content = new ReportTripProblemFragment();
        content.setArguments(args);

        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(android.R.id.content, content);
        ft.addToBackStack(null);
        ft.commit();
    }

    private Spinner mCodeView;

    private TextView mUserComment;

    private CheckBox mUserOnVehicle;

    private TextView mUserVehicle;

    @Override
    protected int getLayoutId() {
        return R.layout.report_trip_problem;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Set the stop name.
        Bundle args = getArguments();
        final TextView tripName = (TextView) view.findViewById(R.id.trip_name);
        tripName.setText(MyTextUtils.toTitleCase(args.getString(TRIP_NAME)));

        // TODO: Switch this based on the trip mode
        final int tripArray = R.array.report_trip_problem_code_bus;

        //
        // The code spinner
        //
        mCodeView = (Spinner) view.findViewById(R.id.report_problem_code);
        ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
                getActivity(), tripArray, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCodeView.setAdapter(adapter);

        // Comment
        mUserComment = (TextView) view.findViewById(R.id.report_problem_comment);

        // On vehicle
        mUserOnVehicle = (CheckBox) view.findViewById(R.id.report_problem_onvehicle);
        final View label = view.findViewById(R.id.report_problem_uservehicle_label);
        mUserVehicle = (TextView) view.findViewById(R.id.report_problem_uservehicle);
        // Disabled by default
        label.setEnabled(false);
        mUserVehicle.setEnabled(false);

        mUserOnVehicle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean checked = mUserOnVehicle.isChecked();
                label.setEnabled(checked);
                mUserVehicle.setEnabled(checked);
            }
        });

        if (savedInstanceState != null) {
            int position = savedInstanceState.getInt(CODE);
            mCodeView.setSelection(position);

            CharSequence comment = savedInstanceState.getCharSequence(USER_COMMENT);
            mUserComment.setText(comment);

            boolean onVehicle = savedInstanceState.getBoolean(USER_ON_VEHICLE);
            mUserOnVehicle.setChecked(onVehicle);

            CharSequence num = savedInstanceState.getCharSequence(USER_VEHICLE_NUM);
            mUserVehicle.setText(num);
            mUserVehicle.setEnabled(onVehicle);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CODE, mCodeView.getSelectedItemPosition());
        outState.putCharSequence(USER_COMMENT, mUserComment.getText());
        outState.putBoolean(USER_ON_VEHICLE, mUserOnVehicle.isChecked());
        outState.putCharSequence(USER_VEHICLE_NUM, mUserVehicle.getText());
    }

    @Override
    protected void sendReport() {
        // Hide the soft keyboard.
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mUserComment.getWindowToken(), 0);
        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.SUBMIT.toString(),
                getString(R.string.analytics_action_problem), getString(R.string.analytics_label_report_trip_problem));
        super.sendReport();
    }

    private static final String[] SPINNER_TO_CODE = new String[]{
            null,
            ObaReportProblemWithTripRequest.VEHICLE_NEVER_CAME,
            ObaReportProblemWithTripRequest.VEHICLE_CAME_EARLY,
            ObaReportProblemWithTripRequest.VEHICLE_CAME_LATE,
            ObaReportProblemWithTripRequest.WRONG_HEADSIGN,
            ObaReportProblemWithTripRequest.VEHICLE_DOES_NOT_STOP_HERE,
            ObaReportProblemWithTripRequest.OTHER
    };

    @Override
    protected ReportLoader createLoader(Bundle args) {
        // Trip ID
        String tripId = args.getString(TRIP_ID);

        ObaReportProblemWithTripRequest.Builder builder =
                new ObaReportProblemWithTripRequest.Builder(getActivity(), tripId);

        builder.setStopId(args.getString(STOP_ID));
        builder.setVehicleId(args.getString(TRIP_VEHICLE_ID));
        builder.setServiceDate(args.getLong(TRIP_SERVICE_DATE));

        // Code
        String code = SPINNER_TO_CODE[mCodeView.getSelectedItemPosition()];
        if (code != null) {
            builder.setCode(code);
        }

        // Comment
        CharSequence comment = mUserComment.getText();
        if (!TextUtils.isEmpty(comment)) {
            builder.setUserComment(comment.toString());
        }

        // Location / Location accuracy
        Location location = LocationHelp.getLocation2(getActivity(), mLocationClient);
        if (location != null) {
            builder.setUserLocation(location.getLatitude(), location.getLongitude());
            if (location.hasAccuracy()) {
                builder.setUserLocationAccuracy((int) location.getAccuracy());
            }
        }

        // User on vehicle?
        builder.setUserOnVehicle(mUserOnVehicle.isChecked());

        // User Vehicle Number
        CharSequence vehicleNum = mUserVehicle.getText();
        if (!TextUtils.isEmpty(vehicleNum)) {
            builder.setUserVehicleNumber(vehicleNum.toString());
        }

        return new ReportLoader(getActivity(), builder.build());
    }
}
