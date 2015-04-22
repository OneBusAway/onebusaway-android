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
package org.onebusaway.android.ui;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaReportProblemWithStopRequest;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.MyTextUtils;

public class ReportStopProblemFragment extends ReportProblemFragmentBase {

    private static final String STOP_ID = ".StopId";

    private static final String STOP_NAME = ".StopName";

    private static final String CODE = ".Code";

    private static final String USER_COMMENT = ".UserComment";

    static void show(ActionBarActivity activity, ObaStop stop) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Bundle args = new Bundle();
        args.putString(STOP_ID, stop.getId());
        // We don't use the stop name map here...we want the actual stop name.
        args.putString(STOP_NAME, stop.getName());

        // Create the list fragment and add it as our sole content.
        ReportStopProblemFragment content = new ReportStopProblemFragment();
        content.setArguments(args);

        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(android.R.id.content, content);
        ft.addToBackStack(null);
        ft.commit();
    }

    private TextView mUserComment;

    @Override
    protected int getLayoutId() {
        return R.layout.report_stop_problem;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Set the stop name.
        Bundle args = getArguments();
        final TextView stopName = (TextView) view.findViewById(R.id.stop_name);
        stopName.setText(MyTextUtils.toTitleCase(args.getString(STOP_NAME)));

        //
        // The code spinner
        //
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
        Location location = LocationUtil.getLocation2(getActivity(), mGoogleApiClient);
        if (location != null) {
            builder.setUserLocation(location.getLatitude(), location.getLongitude());
            if (location.hasAccuracy()) {
                builder.setUserLocationAccuracy((int) location.getAccuracy());
            }
        }

        return new ReportLoader(getActivity(), builder.build());
    }
}
