/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.concurrent.Callable;

public abstract class ReportProblemFragmentBase extends Fragment
        implements LoaderManager.LoaderCallbacks<ObaResponse> {
    //private static final String TAG = "ReportProblemFragmentBase";

    private static final int REPORT_LOADER = 100;

    private ReportProblemFragmentCallback mCallback;

    /**
     * GoogleApiClient being used for Location Services
     */
    GoogleApiClient mGoogleApiClient;

    /**
     * Displays the problem categories
     */
    Spinner mCodeView;

    /**
     * Stores the problem category codes
     */
    String[] SPINNER_TO_CODE;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (api.isGooglePlayServicesAvailable(getActivity())
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(getActivity());
            mGoogleApiClient.connect();
        }

        try {
            mCallback = (ReportProblemFragmentCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("ReportProblemFragmentCallback should be implemented" +
                    " in parent activity");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup root, Bundle savedInstanceState) {
        if (root == null) {
            // Currently in a layout without a container, so no
            // reason to create our view.
            return null;
        }

        return inflater.inflate(getLayoutId(), null);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make sure GoogleApiClient is connected, if available
        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onStop() {
        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.report_problem_options, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.report_problem_send) {
            sendReport();
        }
        return false;
    }

    /**
     * Validates report arguments before submitting a problem
     *
     * @return false if the report is not valid
     */
    protected boolean isReportArgumentsValid() {
        // Check if a user selects a problem category
        String code = SPINNER_TO_CODE[mCodeView.getSelectedItemPosition()];
        return code != null;
    }

    protected void sendReport() {
        UIUtils.showProgress(this, true);
        getLoaderManager().restartLoader(REPORT_LOADER, getArguments(), this);

        ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.SUBMIT.toString(),
                getString(R.string.analytics_action_problem), getString(R.string.analytics_label_report_problem));

        //If the activity implements the callback, then notify
        if (mCallback != null) {
            mCallback.onSendReport();
        }
    }

    @Override
    public Loader<ObaResponse> onCreateLoader(int id, Bundle args) {
        return createLoader(args);
    }

    @Override
    public void onLoadFinished(Loader<ObaResponse> loader, ObaResponse response) {
        UIUtils.showProgress(this, false);

        if ((response != null) && (response.getCode() == ObaApi.OBA_OK)) {
            // Manage the fragment back stack
            mGoBackHandler.postDelayed(mGoBack, 100);
        } else {
            Toast.makeText(getActivity(), R.string.report_problem_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaResponse> loader) {
        UIUtils.showProgress(this, false);
    }

    final Handler mGoBackHandler = new Handler();

    final Runnable mGoBack = new Runnable() {
        public void run() {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    };

    //
    // Report loader
    //
    protected static final class ReportLoader extends AsyncTaskLoader<ObaResponse> {

        private final Callable<? extends ObaResponse> mRequest;

        public ReportLoader(Context context, Callable<? extends ObaResponse> request) {
            super(context);
            mRequest = request;
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }

        @Override
        public ObaResponse loadInBackground() {
            try {
                return mRequest.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    protected abstract int getLayoutId();

    protected abstract ReportLoader createLoader(Bundle args);
}
