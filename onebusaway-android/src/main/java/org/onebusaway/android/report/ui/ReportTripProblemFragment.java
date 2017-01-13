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
package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaReportProblemWithTripRequest;
import org.onebusaway.android.util.UIUtils;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ReportTripProblemFragment extends ReportProblemFragmentBase {
    public static final String TRIP_ID = ".TripId";

    public static final String STOP_ID = ".StopId";

    public static final String TRIP_HEADSIGN = ".TripHeadsign";

    public static final String TRIP_SERVICE_DATE = ".ServiceDate";

    public static final String TRIP_VEHICLE_ID = ".VehicleId";

    public static final String CODE = ".Code";

    public static final String USER_COMMENT = ".UserComment";

    public static final String USER_ON_VEHICLE = ".UserOnVehicle";

    public static final String USER_VEHICLE_NUM = ".UserVehicleNum";

    public static final String TAG = "RprtTripProblemFragment";

    public static void show(AppCompatActivity activity, ObaArrivalInfo arrival) {
        show(activity, arrival, null);
    }

    public static void show(AppCompatActivity activity, ObaArrivalInfo arrival,
                            Integer containerViewId) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putString(TRIP_ID, arrival.getTripId());
        args.putString(STOP_ID, arrival.getStopId());
        args.putString(TRIP_HEADSIGN, arrival.getHeadsign());
        args.putLong(TRIP_SERVICE_DATE, arrival.getServiceDate());
        args.putString(TRIP_VEHICLE_ID, arrival.getVehicleId());

        // Create the list fragment and add it as our sole content.
        ReportTripProblemFragment content = new ReportTripProblemFragment();
        content.setArguments(args);

        FragmentTransaction ft = fm.beginTransaction();
        if (containerViewId == null) {
            ft.replace(android.R.id.content, content, TAG);
        } else {
            ft.replace(containerViewId, content, TAG);
        }
        ft.addToBackStack(null);
        try {
            ft.commit();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot show ReportTripProblemFragment after onSaveInstanceState has been called");
        }
    }

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
        final TextView tripHeadsign = (TextView) view.findViewById(R.id.report_problem_headsign);
        tripHeadsign.setText(UIUtils.formatDisplayText(args.getString(TRIP_HEADSIGN)));

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
        mUserVehicle = (EditText) view.findViewById(R.id.report_problem_uservehicle);
        // Disabled by default
        mUserVehicle.setEnabled(false);

        mUserOnVehicle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean checked = mUserOnVehicle.isChecked();
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

        SPINNER_TO_CODE = new String[]{
                null,
                ObaReportProblemWithTripRequest.VEHICLE_NEVER_CAME,
                ObaReportProblemWithTripRequest.VEHICLE_CAME_EARLY,
                ObaReportProblemWithTripRequest.VEHICLE_CAME_LATE,
                ObaReportProblemWithTripRequest.WRONG_HEADSIGN,
                ObaReportProblemWithTripRequest.VEHICLE_DOES_NOT_STOP_HERE,
                ObaReportProblemWithTripRequest.OTHER
        };

        setupIconColors();
    }

    private void setupIconColors() {
        ((ImageView) getActivity().findViewById(R.id.ic_category)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) getActivity().findViewById(R.id.ic_trip_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) getActivity().findViewById(R.id.ic_headsign_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
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
        if (isReportArgumentsValid()) {
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.SUBMIT.toString(),
                    getString(R.string.analytics_action_problem), getString(R.string.analytics_label_report_trip_problem));
            super.sendReport();
        } else {
            // Show error message if report arguments is not valid
            Toast.makeText(getActivity(), getString(R.string.report_problem_invalid_argument),
                    Toast.LENGTH_LONG).show();
        }
    }

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
        Location location = Application.getLastKnownLocation(getActivity(), mGoogleApiClient);
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
