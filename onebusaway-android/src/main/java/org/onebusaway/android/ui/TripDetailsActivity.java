/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com)
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
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import org.onebusaway.android.R;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.map.googlemapsv2.TripMapFragment;
import org.onebusaway.android.util.FragmentUtils;
import org.onebusaway.android.util.UIUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class TripDetailsActivity extends AppCompatActivity
        implements TripDetailsListFragment.TripDataCallback, TripMapFragment.Callback {

    private static final String TAG = "TripDetailsActivity";

    private ObaTripDetailsResponse mCachedResponse;
    private String mTripId;
    private String mStopId;

    public static class Builder {

        private Context mContext;

        private Intent mIntent;

        public Builder(Context context, String tripId) {
            mContext = context;
            mIntent = new Intent(context, TripDetailsActivity.class);
            mIntent.putExtra(TripDetailsListFragment.TRIP_ID, tripId);
        }

        public Builder setStopId(String stopId) {
            mIntent.putExtra(TripDetailsListFragment.STOP_ID, stopId);
            return this;
        }

        public Builder setScrollMode(String mode) {
            mIntent.putExtra(TripDetailsListFragment.SCROLL_MODE, mode);
            return this;
        }

        public Builder setActiveTrip(Boolean b) {
            mIntent.putExtra(TripDetailsListFragment.TRIP_ACTIVE, b);
            return this;
        }

        public Builder setDestinationId(String stopId) {
            mIntent.putExtra(TripDetailsListFragment.DEST_ID, stopId);
            return this;
        }

        public Builder setUpMode(String mode) {
            mIntent.putExtra(NavHelp.UP_MODE, mode);
            return this;
        }

        public Intent getIntent() {
            return mIntent;
        }

        public void start() {
            mContext.startActivity(mIntent);
        }
    }

    public static void start(Context context, String tripId) {
        new Builder(context, tripId).start();
    }

    public static void start(Context context, String tripId, String mode) {
        new Builder(context, tripId).setScrollMode(mode).start();
    }

    public static void start(Context context, String tripId, String stopId, String mode) {
        new Builder(context, tripId).setStopId(stopId).setScrollMode(mode).start();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_details);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        UIUtils.setupActionBar(this);

        mTripId = getIntent().getStringExtra(TripDetailsListFragment.TRIP_ID);
        mStopId = getIntent().getStringExtra(TripDetailsListFragment.STOP_ID);

        FragmentManager fm = getSupportFragmentManager();
        // After config change, mCachedResponse is lost (not Parcelable).
        // If the map fragment was showing, replace it with the list so the
        // loader repopulates the response via onTripDataLoaded().
        if (savedInstanceState != null
                && fm.findFragmentByTag(TripMapFragment.TAG) != null) {
            fm.beginTransaction()
                    .replace(R.id.fragment_container,
                            newListFragment(), TripDetailsListFragment.TAG)
                    .commitNow();
        }

        if (fm.findFragmentById(R.id.fragment_container) == null) {
            fm.beginTransaction()
                    .add(R.id.fragment_container,
                            newListFragment(), TripDetailsListFragment.TAG)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == TripDetailsListFragment.REQUEST_ENABLE_LOCATION) {
            TripDetailsListFragment tripDetListFrag = (TripDetailsListFragment)
                    getSupportFragmentManager().findFragmentByTag(TripDetailsListFragment.TAG);
            if(tripDetListFrag == null) {
                tripDetListFrag = newListFragment();
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, tripDetListFrag, TripDetailsListFragment.TAG)
                        .commit();
            }
            tripDetListFrag.onActivityResult(requestCode, resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // --- TripDetailsListFragment.TripDataCallback ---

    @Override
    public void onTripDataLoaded(ObaTripDetailsResponse response) {
        mCachedResponse = response;
    }

    @Override
    public void onShowMap() {
        showMap();
    }

    // --- TripMapFragment.Callback ---

    @Override
    public void onShowList() {
        showList();
    }

    // --- Fragment swapping ---

    public void showMap() {
        if (mCachedResponse == null || mTripId == null) return;

        TripMapFragment mapFragment = TripMapFragment.newInstance(mTripId);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, mapFragment, TripMapFragment.TAG)
                .commit();

        // activateTrip must be called after the fragment transaction is committed
        // and the fragment has its map ready. We post it to handle the timing.
        getSupportFragmentManager().executePendingTransactions();
        mapFragment.activateTrip(mTripId, mStopId, mCachedResponse);
    }

    public void showList() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container,
                        newListFragment(), TripDetailsListFragment.TAG)
                .commit();
    }

    private TripDetailsListFragment newListFragment() {
        TripDetailsListFragment list = new TripDetailsListFragment();
        list.setArguments(FragmentUtils.getIntentArgs(getIntent()));
        return list;
    }
}
