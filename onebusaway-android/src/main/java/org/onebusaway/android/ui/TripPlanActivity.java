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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class TripPlanActivity extends AppCompatActivity implements TripRequest.Callback {

    private static final String TAG = "TripPlanActivity";

    TripRequestBuilder builder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_plan);

        UIUtils.setupActionBar(this);

        Bundle bundle = (savedInstanceState == null) ? new Bundle() : savedInstanceState;
        builder = new TripRequestBuilder(bundle);

        if (getSupportFragmentManager().findFragmentById(R.id.trip_plan_fragment_container) != null)
            return;

        TripPlanFragment fragment = new TripPlanFragment();
        fragment.setArguments(bundle);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.trip_plan_fragment_container, fragment).commit();
    }


    @Override
    public void onSaveInstanceState(Bundle bundle) {
        if (builder != null)
            builder.copyIntoBundle(bundle);
    }

    public void route() {
        builder.setListener(this).execute(this);
    }

    @Override
    public void onTripRequestComplete(List<Itinerary> itineraries) {
        Log.i(TAG, "Successfully routed. Itineraries are " + itineraries);

        if (itineraries.size() > 0) {

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.trip_plan_fragment_container);
            if (fragment != null && fragment instanceof TripResultsFragment) {
                Bundle bundle = fragment.getArguments();
                bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<>(itineraries));
                // bundle arguments already set
                ((TripResultsFragment) fragment).displayNewResults();
                return;
            }

            TripResultsFragment resultsFragment = new TripResultsFragment();

            Bundle bundle = builder.getBundle();
            bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<>(itineraries));
            resultsFragment.setArguments(bundle);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack so the user can navigate back
            transaction.replace(R.id.trip_plan_fragment_container, resultsFragment);
            transaction.addToBackStack(null);

            // Commit the transaction
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
        if (fragment == null || fragment instanceof TripPlanFragment)
            return super.onSupportNavigateUp();

        getSupportFragmentManager().popBackStack();
        return true;
    }

}
