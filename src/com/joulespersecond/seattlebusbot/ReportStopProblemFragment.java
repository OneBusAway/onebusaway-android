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

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.joulespersecond.oba.ObaApi;
import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.request.ObaReportProblemWithStopRequest;
import com.joulespersecond.oba.request.ObaReportProblemWithStopResponse;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ReportStopProblemFragment extends SherlockFragment
            implements LoaderManager.LoaderCallbacks<ObaReportProblemWithStopResponse> {
    private static final String TAG = "ReportStopProblemFragment";
    private static final int REPORT_LOADER = 100;

    private static final String STOP_ID = ".StopId";
    private static final String STOP_NAME = ".StopName";

    static void show(SherlockFragmentActivity activity, ObaStop stop) {
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

    private Spinner mCodeView;
    private TextView mUserComment;
    private View mSendButton;

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }
        return inflater.inflate(R.layout.report_stop_problem, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Set the stop name.
        Bundle args = getArguments();
        final TextView stopName = (TextView)view.findViewById(R.id.stop_name);
        stopName.setText(MyTextUtils.toTitleCase(args.getString(STOP_NAME)));

        //
        // The code spinner
        //
        mCodeView = (Spinner) view.findViewById(R.id.report_problem_code);
        ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
                getActivity(), R.array.report_stop_problem_code, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCodeView.setAdapter(adapter);

        // Comment
        mUserComment = (TextView)view.findViewById(R.id.report_problem_comment);

        //
        // Set up buttons
        //
        View cancel = view.findViewById(android.R.id.button1);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        mSendButton = view.findViewById(android.R.id.button2);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendReport();
            }
        });
    }

    void sendReport() {
        // Hide the soft keyboard.
        InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mUserComment.getWindowToken(), 0);

        mSendButton.setClickable(false);
        UIHelp.showProgress(this, true);

        getLoaderManager().initLoader(REPORT_LOADER, getArguments(), this);
    }

    private static final String[] SPINNER_TO_CODE = new String[] {
        null,
        ObaReportProblemWithStopRequest.NAME_WRONG,
        ObaReportProblemWithStopRequest.NUMBER_WRONG,
        ObaReportProblemWithStopRequest.LOCATION_WRONG,
        ObaReportProblemWithStopRequest.ROUTE_OR_TRIP_MISSING,
        ObaReportProblemWithStopRequest.OTHER
    };

    @Override
    public Loader<ObaReportProblemWithStopResponse> onCreateLoader(int id, Bundle args) {
        // Stop ID.
        // Code
        // Comment
        // Location
        // Location accuracy
        String stopId = args.getString(STOP_ID);

        ObaReportProblemWithStopRequest.Builder builder =
                new ObaReportProblemWithStopRequest.Builder(getActivity(), stopId);

        String code = SPINNER_TO_CODE[mCodeView.getSelectedItemPosition()];

        if (code != null) {
            builder.setCode(code);
        }

        CharSequence comment = mUserComment.getText();
        if (!TextUtils.isEmpty(comment)) {
            builder.setUserComment(comment.toString());
        }

        Location location = UIHelp.getLocation2(getActivity());
        if (location != null) {
            builder.setUserLocation(location.getLatitude(), location.getLongitude());
            if (location.hasAccuracy()) {
                builder.setUserLocationAccuracy((int)location.getAccuracy());
            }
        }

        return new ReportLoader(getActivity(), builder.build());
    }

    @Override
    public void onLoadFinished(Loader<ObaReportProblemWithStopResponse> loader,
            ObaReportProblemWithStopResponse response) {
        Log.d(TAG, "Load finished!");
        mSendButton.setClickable(true);
        UIHelp.showProgress(this, false);

        if (response.getCode() == ObaApi.OBA_OK) {
            Toast.makeText(getActivity(), R.string.report_problem_sent, Toast.LENGTH_LONG).show();
            getActivity().getSupportFragmentManager().popBackStack();
        } else {
            Toast.makeText(getActivity(), R.string.report_problem_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaReportProblemWithStopResponse> loader) {
        mSendButton.setClickable(true);
        UIHelp.showProgress(this, false);
    }

    private static final class ReportLoader extends AsyncTaskLoader<ObaReportProblemWithStopResponse> {
        private final ObaReportProblemWithStopRequest mRequest;

        public ReportLoader(Context context, ObaReportProblemWithStopRequest request) {
            super(context);
            mRequest = request;
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        @Override
        public ObaReportProblemWithStopResponse loadInBackground() {
            return mRequest.call();
        }
    }
}
