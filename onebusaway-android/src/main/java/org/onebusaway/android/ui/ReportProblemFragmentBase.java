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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.UIHelp;

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
import android.widget.Toast;

import java.util.concurrent.Callable;

public abstract class ReportProblemFragmentBase extends Fragment
        implements LoaderManager.LoaderCallbacks<ObaResponse> {
    //private static final String TAG = "ReportProblemFragmentBase";

    private static final int REPORT_LOADER = 100;

    /**
     * Google Location Services
     */
    LocationClient mLocationClient;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity())
                == ConnectionResult.SUCCESS) {
            LocationUtil.LocationServicesCallback locCallback
                    = new LocationUtil.LocationServicesCallback();
            mLocationClient = new LocationClient(getActivity(), locCallback, locCallback);
            mLocationClient.connect();
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
        // Make sure LocationClient is connected, if available
        if (mLocationClient != null && !mLocationClient.isConnected()) {
            mLocationClient.connect();
        }
    }

    @Override
    public void onStop() {
        // Tear down LocationClient
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.disconnect();
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

    protected void sendReport() {
        UIHelp.showProgress(this, true);
        getLoaderManager().restartLoader(REPORT_LOADER, getArguments(), this);
    }

    @Override
    public Loader<ObaResponse> onCreateLoader(int id, Bundle args) {
        return createLoader(args);
    }

    @Override
    public void onLoadFinished(Loader<ObaResponse> loader, ObaResponse response) {
        UIHelp.showProgress(this, false);

        if ((response != null) && (response.getCode() == ObaApi.OBA_OK)) {
            Toast.makeText(getActivity(), R.string.report_problem_sent, Toast.LENGTH_LONG).show();
            mGoBackHandler.postDelayed(mGoBack, 100);
        } else {
            Toast.makeText(getActivity(), R.string.report_problem_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoaderReset(Loader<ObaResponse> loader) {
        UIHelp.showProgress(this, false);
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
