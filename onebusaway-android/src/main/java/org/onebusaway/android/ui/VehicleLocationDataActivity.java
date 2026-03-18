/*
 * Copyright (C) 2024 Open Transit Software Foundation
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
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.ObaTripStatusExtensionsKt;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaTripDetailsRequest;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.extrapolation.math.SpeedDistribution;
import org.onebusaway.android.extrapolation.data.TripDataManager;
import org.onebusaway.android.extrapolation.math.speed.VehicleTrajectoryTracker;
import org.onebusaway.android.util.UIUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Debug activity that displays all collected location data for a vehicle's trip
 * in a scrollable table and a distance-time graph. Data collection is managed by
 * VehicleTrajectoryTracker's polling infrastructure; this activity only refreshes its UI display.
 */
public class VehicleLocationDataActivity extends AppCompatActivity {

    private static final String EXTRA_TRIP_ID = ".TripId";
    private static final String EXTRA_VEHICLE_ID = ".VehicleId";
    private static final String EXTRA_STOP_ID = ".StopId";

    private static final String TAG = "VehicleLocationDataAct";
    private static final int PAD_H = 12;
    private static final int PAD_V = 6;
    private static final int TEXT_SIZE = 12;
    private static final long UI_REFRESH_PERIOD = 1_000;
    private static final long POLL_INTERVAL_MS = 30_000;
    private static final double MPS_TO_MPH = 2.23694;

    private String mTripId;
    private String mStopId;
    private final Handler mRefreshHandler = new Handler(Looper.getMainLooper());
    private final Handler mPollHandler = new Handler(Looper.getMainLooper());
    private int mLastRowCount = -1;

    private View mTableContainer;
    private TrajectoryGraphView mGraphView;

    private final Runnable mRefresh = new Runnable() {
        @Override
        public void run() {
            refreshData();
            mRefreshHandler.postDelayed(mRefresh, UI_REFRESH_PERIOD);
        }
    };

    private final Runnable mPoll = new Runnable() {
        @Override
        public void run() {
            pollTrip();
            mPollHandler.postDelayed(mPoll, POLL_INTERVAL_MS);
        }
    };

    public static void start(Context context, String tripId, String vehicleId) {
        start(context, tripId, vehicleId, null);
    }

    public static void start(Context context, String tripId, String vehicleId, String stopId) {
        Intent intent = new Intent(context, VehicleLocationDataActivity.class);
        intent.putExtra(EXTRA_TRIP_ID, tripId);
        intent.putExtra(EXTRA_VEHICLE_ID, vehicleId);
        intent.putExtra(EXTRA_STOP_ID, stopId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.setupActionBar(this);
        setContentView(R.layout.activity_vehicle_location_data);

        mTripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        mStopId = getIntent().getStringExtra(EXTRA_STOP_ID);
        String vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Trip Trajectory");
            if (vehicleId != null) {
                getSupportActionBar().setSubtitle("Vehicle: " + vehicleId);
            }
        }

        mTableContainer = findViewById(R.id.location_data_table_container);
        mGraphView = findViewById(R.id.location_data_graph);
        mGraphView.setHighlightedStopId(mStopId);

        TabLayout tabs = findViewById(R.id.location_data_tabs);
        tabs.addTab(tabs.newTab().setText("Table"));
        tabs.addTab(tabs.newTab().setText("Graph"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mTableContainer.setVisibility(View.GONE);
                mGraphView.setVisibility(View.GONE);
                switch (tab.getPosition()) {
                    case 0:
                        mTableContainer.setVisibility(View.VISIBLE);
                        break;
                    case 1:
                        mGraphView.setVisibility(View.VISIBLE);
                        break;
                }
                refreshData();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        refreshData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollTrip();
        mPollHandler.postDelayed(mPoll, POLL_INTERVAL_MS);
        refreshData();
        mRefreshHandler.postDelayed(mRefresh, UI_REFRESH_PERIOD);
    }

    @Override
    protected void onPause() {
        mRefreshHandler.removeCallbacks(mRefresh);
        mPollHandler.removeCallbacks(mPoll);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshData() {
        TripDataManager dm = TripDataManager.getInstance();
        List<ObaTripStatus> history = dm.getHistory(mTripId);

        // Check if the vehicle is still serving this trip
        String activeTripId = dm.getLastActiveTripId(mTripId);
        boolean tripEnded = activeTripId != null && !mTripId.equals(activeTripId);

        // Update header
        int currentCount = history.size();
        TextView header = findViewById(R.id.location_data_header);
        if (tripEnded) {
            header.setText(String.format(Locale.US,
                    "Trip: %s  |  Samples: %d  |  Vehicle no longer serving trip",
                    mTripId, currentCount));
        } else {
            header.setText(String.format(Locale.US, "Trip: %s  |  Samples: %d",
                    mTripId, currentCount));
        }

        // Refresh table only if row count changed
        if (currentCount != mLastRowCount) {
            mLastRowCount = currentCount;
            TableLayout table = findViewById(R.id.location_data_table);
            table.removeAllViews();
            buildTable(table, history);
        }

        // Refresh graph only when visible
        if (mGraphView.getVisibility() == View.VISIBLE) {
            refreshGraph(tripEnded);
        }
    }

    private void refreshGraph(boolean tripEnded) {
        TripDataManager dm = TripDataManager.getInstance();
        List<ObaTripStatus> history = dm.getHistory(mTripId);
        ObaTripSchedule schedule = dm.getSchedule(mTripId);
        Long serviceDate = dm.getServiceDate(mTripId);
        SpeedDistribution distribution = null;
        if (!tripEnded) {
            // getEstimatedSpeed populates distribution as a side effect
            VehicleTrajectoryTracker tracker = VehicleTrajectoryTracker.getInstance();
            tracker.getEstimatedSpeed(mTripId);
            distribution = tracker.getLastDistribution();
        }
        mGraphView.setData(history, schedule, serviceDate != null ? serviceDate : 0,
                distribution);
    }

    private void pollTrip() {
        final String tripId = mTripId;
        if (tripId == null) return;
        new Thread(() -> {
            try {
                ObaTripDetailsResponse response =
                        ObaTripDetailsRequest.newRequest(getApplicationContext(), tripId).call();
                if (response != null && response.getCode() == ObaApi.OBA_OK) {
                    TripDataManager.getInstance()
                            .recordTripDetailsResponse(tripId, response);
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "Failed to poll trip details for " + tripId, e);
            }
        }).start();
    }

    private void buildTable(TableLayout table, List<ObaTripStatus> history) {
        // Header row
        String[] headers = {"#", "AVL time", "Lat", "Lon",
                "Dist (m)", "\u0394t (s)",
                "\u0394dist (m)", "Speed (mph)", "Geo \u0394 (m)"};
        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(0xFF424242);
        for (String h : headers) {
            headerRow.addView(createCell(h, true));
        }
        table.addView(headerRow);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(0xFF888888);
        table.addView(divider);

        if (history.isEmpty()) {
            TableRow emptyRow = new TableRow(this);
            TextView cell = createCell("No data collected yet \u2014 waiting for updates...", false);
            TableRow.LayoutParams params = new TableRow.LayoutParams();
            params.span = headers.length;
            cell.setLayoutParams(params);
            emptyRow.addView(cell);
            table.addView(emptyRow);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);

        ObaTripStatus prev = null;
        for (int i = 0; i < history.size(); i++) {
            ObaTripStatus entry = history.get(i);
            Location pos = entry.getLastKnownLocation();

            TableRow row = new TableRow(this);
            row.setBackgroundColor(i % 2 == 0 ? 0xFF1A1A1A : 0xFF262626);

            // #
            row.addView(createCell(String.valueOf(i + 1), false));

            // AVL time (when the vehicle actually reported)
            long avlTime = entry.getLastLocationUpdateTime();
            row.addView(createCell(
                    avlTime > 0 ? sdf.format(new Date(avlTime)) : "\u2014", false));

            // Lat, Lon
            if (pos != null) {
                row.addView(createCell(
                        String.format(Locale.US, "%.6f", pos.getLatitude()), false));
                row.addView(createCell(
                        String.format(Locale.US, "%.6f", pos.getLongitude()), false));
            } else {
                row.addView(createCell("\u2014", false));
                row.addView(createCell("\u2014", false));
            }

            // Distance along trip
            Double entryDist = ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(entry);
            if (entryDist != null) {
                row.addView(createCell(
                        String.format(Locale.US, "%.1f", entryDist), false));
            } else {
                row.addView(createCell("\u2014", false));
            }

            // Delta columns
            if (prev != null) {
                // Δt from AVL timestamps
                long prevAvl = prev.getLastLocationUpdateTime();
                long dtMs = (avlTime > 0 && prevAvl > 0)
                        ? avlTime - prevAvl
                        : entry.getLastUpdateTime() - prev.getLastUpdateTime();
                row.addView(createCell(
                        String.format(Locale.US, "%.1f", dtMs / 1000.0), false));

                // Δdist
                Double prevDist = ObaTripStatusExtensionsKt.getBestDistanceAlongTrip(prev);
                if (prevDist != null && entryDist != null) {
                    double dd = entryDist - prevDist;
                    row.addView(createCell(
                            String.format(Locale.US, "%.1f", dd), false));

                    // Speed
                    if (dtMs > 0) {
                        double speedDist = dd < 0 ? 0 : dd;
                        double speedMph = (speedDist / (dtMs / 1000.0)) * MPS_TO_MPH;
                        row.addView(createCell(
                                String.format(Locale.US, "%.1f", speedMph), false));
                    } else {
                        row.addView(createCell("\u2014", false));
                    }
                } else {
                    row.addView(createCell("\u2014", false));
                    row.addView(createCell("\u2014", false));
                }

                // Geo distance
                if (prev.getLastKnownLocation() != null && pos != null) {
                    float geoDistM = prev.getLastKnownLocation().distanceTo(pos);
                    row.addView(createCell(
                            String.format(Locale.US, "%.1f", geoDistM), false));
                } else {
                    row.addView(createCell("\u2014", false));
                }
            } else {
                row.addView(createCell("", false));
                row.addView(createCell("", false));
                row.addView(createCell("", false));
                row.addView(createCell("", false));
            }

            table.addView(row);
            prev = entry;
        }
    }

    private TextView createCell(String text, boolean isHeader) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(PAD_H, PAD_V, PAD_H, PAD_V);
        tv.setTextSize(TEXT_SIZE);
        tv.setGravity(Gravity.END);
        tv.setSingleLine(true);
        tv.setTextColor(Color.WHITE);
        if (isHeader) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
        }
        return tv;
    }
}
