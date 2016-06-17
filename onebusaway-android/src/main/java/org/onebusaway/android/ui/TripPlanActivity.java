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
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.util.UIUtils;
import org.opentripplanner.api.model.Itinerary;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class TripPlanActivity extends AppCompatActivity implements TripRequest.Callback {

    private static final String TAG = "TripPlanActivity";

    TripRequestBuilder mBuilder;

    TripRequest mTripRequest = null;

    boolean mPostSaveInstanceState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_trip_plan);

        UIUtils.setupActionBar(this);

        Bundle bundle = (savedInstanceState == null) ? new Bundle() : savedInstanceState;
        mBuilder = new TripRequestBuilder(bundle);

        // Check which fragment to create
        boolean haveTripPlan = bundle.getSerializable(OTPConstants.ITINERARIES) != null;

        Fragment fragment = haveTripPlan ? new TripResultsFragment() : new TripPlanFragment();

        fragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.trip_plan_fragment_container, fragment).commit();

        // if planning in progress, reshow dialog
        if (mTripRequest != null) {
            mTripRequest.showProgressDialog();
        }

        mPostSaveInstanceState = false;
    }


    @Override
    public void onSaveInstanceState(Bundle bundle) {

        if (mBuilder != null) {
            mBuilder.copyIntoBundle(bundle);
            // We also saved the itinerary into this bundle.
            ArrayList<Itinerary> itineraries = (ArrayList<Itinerary>) mBuilder.getBundle()
                    .getSerializable(OTPConstants.ITINERARIES);
            if (itineraries != null)
                bundle.putSerializable(OTPConstants.ITINERARIES, itineraries);
        }

        mPostSaveInstanceState = true;
    }

    @Override
    public void onStop() {
        super.onStop();

        // Close possible progress dialog.
        if (mTripRequest != null) {
            ProgressDialog dialog = mTripRequest.getProgressDialog();
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    public void route() {
        mTripRequest = mBuilder.setListener(this).execute(this);
    }

    @Override
    public void onTripRequestComplete(List<Itinerary> itineraries) {
        Log.d(TAG, "Successfully routed. Itineraries are " + itineraries);

        mTripRequest = null;

        // We can't save the itinerary if this method is called after onSaveInstanceState.
        // Ultimately the architecture should be changed, but for now simply tell the
        // user to try again.
        if (mPostSaveInstanceState) {
            Toast.makeText(this,
                    getString(R.string.tripplanner_error_not_defined),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (itineraries.size() > 0) {

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.trip_plan_fragment_container);
            if (fragment != null && fragment instanceof TripResultsFragment) {
                Bundle bundle = fragment.getArguments();
                bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<>(itineraries));
                // bundle arguments already set
                ((TripResultsFragment) fragment).displayNewResults();
                return;
            }

            Bundle bundle = mBuilder.getBundle();
            bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<>(itineraries));

            TripResultsFragment resultsFragment = new TripResultsFragment();
            resultsFragment.setArguments(bundle);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack so the user can navigate back
            transaction.replace(R.id.trip_plan_fragment_container, resultsFragment);
            transaction.addToBackStack(null);

            // Commit the transaction.
            transaction.commit();

        } else {
            Toast.makeText(this,
                    getString(R.string.tripplanner_error_bogus_parameter),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /* If back is pressed from results fragment, we go to plan fragment. */
    @Override
    public boolean onSupportNavigateUp() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.trip_plan_fragment_container);

        if (fragment == null || fragment instanceof TripPlanFragment) {
            return super.onSupportNavigateUp();
        }

        int nBackStack = getSupportFragmentManager().getBackStackEntryCount();

        // If we rotated we need to create new TripPlanFragment
        if (nBackStack > 0) {
            getSupportFragmentManager().popBackStack();
        }
        else {
            Fragment newFragment = new TripPlanFragment();
            newFragment.setArguments(mBuilder.getBundle());
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.trip_plan_fragment_container, newFragment).commit();
        }

        return true;
    }

}
