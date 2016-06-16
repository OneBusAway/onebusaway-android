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
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaReportProblemWithStopRequest;

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
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ReportStopProblemFragment extends ReportProblemFragmentBase {

    public static final String STOP_ID = ".StopId";

    public static final String STOP_NAME = ".StopName";

    public static final String CODE = ".Code";

    public static final String USER_COMMENT = ".UserComment";

    public static final String TAG = "RprtStopProblemFragment";

    public static void show(AppCompatActivity activity, ObaStop stop, Integer containerViewId) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putString(STOP_ID, stop.getId());
        // We don't use the stop name map here...we want the actual stop name.
        args.putString(STOP_NAME, stop.getName());

        // Create the list fragment and add it as our sole content.
        ReportStopProblemFragment content = new ReportStopProblemFragment();
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
            Log.e(TAG, "Cannot show ReportStopProblemFragment after onSaveInstanceState has been called");
        }
    }

    private TextView mUserComment;

    @Override
    protected int getLayoutId() {
        return R.layout.report_stop_problem;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // The code spinner
        mCodeView = (Spinner) view.findViewById(R.id.report_problem_code);
        ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
                getActivity(), R.array.report_stop_problem_code,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCodeView.setAdapter(adapter);

        // Comment
        mUserComment = (TextView) view.findViewById(R.id.report_problem_comment);

        if (savedInstanceState != null) {
            int position = savedInstanceState.getInt(CODE);
            mCodeView.setSelection(position);

            CharSequence comment = savedInstanceState.getCharSequence(USER_COMMENT);
            mUserComment.setText(comment);
        }

        SPINNER_TO_CODE = new String[]{
                null,
                ObaReportProblemWithStopRequest.NAME_WRONG,
                ObaReportProblemWithStopRequest.NUMBER_WRONG,
                ObaReportProblemWithStopRequest.LOCATION_WRONG,
                ObaReportProblemWithStopRequest.ROUTE_OR_TRIP_MISSING,
                ObaReportProblemWithStopRequest.OTHER
        };

        // Dynamically change the color of the small icons
        setupIconColors();
    }

    private void setupIconColors() {
        ((ImageView) getActivity().findViewById(R.id.ic_category)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) getActivity().findViewById(R.id.ic_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CODE, mCodeView.getSelectedItemPosition());
        outState.putCharSequence(USER_COMMENT, mUserComment.getText());
    }

    @Override
    protected void sendReport() {
        // Hide the soft keyboard.
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mUserComment.getWindowToken(), 0);

        if (isReportArgumentsValid()) {
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.SUBMIT.toString(),
                    getString(R.string.analytics_action_problem), getString(R.string.analytics_label_report_stop_problem));
            super.sendReport();
        } else {
            // Show error message if report arguments is not valid
            Toast.makeText(getActivity(), getString(R.string.report_problem_invalid_argument),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected ReportLoader createLoader(Bundle args) {
        String stopId = args.getString(STOP_ID);

        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getActivity(), stopId);

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

        return new ReportLoader(getActivity(), builder.build());
    }
}
