/*
 * Copyright (C) 2024 Sean J. Barbeau (sjbarbeau@gmail.com)
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
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.Occupancy;
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaTrip;
import org.onebusaway.android.io.elements.ObaTripSchedule;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.io.elements.Status;
import org.onebusaway.android.io.request.ObaTripDetailsRequest;
import org.onebusaway.android.io.request.ObaTripDetailsResponse;
import org.onebusaway.android.util.ArrivalInfoUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

public class VehicleDebugFragment extends Fragment {

    private static final int VEHICLE_LOADER = 0;

    private String mTripId;
    private String mVehicleId;

    private final SimpleDateFormat mTimeFmt =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat mDateTimeFmt =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.vehicle_debug, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        mTripId = args.getString(VehicleDebugActivity.EXTRA_TRIP_ID);
        mVehicleId = args.getString(VehicleDebugActivity.EXTRA_VEHICLE_ID);

        TextView vehicleIdView = view.findViewById(R.id.vehicle_debug_vehicle_id);
        vehicleIdView.setText("Vehicle: " + (mVehicleId != null ? mVehicleId : "unknown"));

        // Map link hidden until we know the route ID
        view.findViewById(R.id.vehicle_debug_show_on_map).setVisibility(View.GONE);

        getLoaderManager().initLoader(VEHICLE_LOADER, null, mLoaderCallback);
    }

    private void setData(VehicleData data) {
        View view = getView();
        if (view == null) {
            return;
        }

        TextView loadingView = view.findViewById(R.id.vehicle_debug_loading);
        ViewGroup contentView = view.findViewById(R.id.vehicle_debug_content);

        if (data.status == null) {
            loadingView.setText("No vehicle status available");
            return;
        }

        loadingView.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);

        Context context = getActivity();
        ObaTripStatus status = data.status;

        // Show on map link
        if (!TextUtils.isEmpty(data.routeId)) {
            TextView mapLink = view.findViewById(R.id.vehicle_debug_show_on_map);
            mapLink.setVisibility(View.VISIBLE);
            final String routeId = data.routeId;
            mapLink.setOnClickListener(v -> HomeActivity.start(getActivity(), routeId));
        }

        // Trip Info
        setText(view, R.id.vehicle_debug_trip_id, "Trip ID: " + mTripId);
        setText(view, R.id.vehicle_debug_active_trip_id,
                "Active trip: " + nullSafe(status.getActiveTripId()));
        setText(view, R.id.vehicle_debug_service_date,
                "Service date: " + mDateTimeFmt.format(new Date(status.getServiceDate())));
        setText(view, R.id.vehicle_debug_phase, "Phase: " + nullSafe(status.getPhase()));
        Status tripStatus = status.getStatus();
        setText(view, R.id.vehicle_debug_status,
                "Status: " + (tripStatus != null ? tripStatus.toString() : "--"));

        // Real-Time
        setText(view, R.id.vehicle_debug_predicted,
                "Predicted: " + status.isPredicted());
        if (status.isPredicted()) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
            String devStr = ArrivalInfoUtils.computeArrivalLabelFromDelay(
                    context.getResources(), deviationMin);
            setText(view, R.id.vehicle_debug_deviation,
                    "Deviation: " + devStr + " (" + status.getScheduleDeviation() + "s)");
        } else {
            setText(view, R.id.vehicle_debug_deviation, "Deviation: scheduled");
        }
        setText(view, R.id.vehicle_debug_last_update,
                "Last update: " + formatTime(status.getLastUpdateTime()));
        setText(view, R.id.vehicle_debug_last_location_update,
                "Last location update: " + formatTime(status.getLastLocationUpdateTime()));
        Occupancy occ = status.getOccupancyStatus();
        setText(view, R.id.vehicle_debug_occupancy,
                "Occupancy: " + (occ != null ? occ.toString() : "--"));

        // Position
        Location pos = status.getPosition();
        setText(view, R.id.vehicle_debug_position,
                "Position: " + formatLocation(pos));
        Location lastKnown = status.getLastKnownLocation();
        setText(view, R.id.vehicle_debug_last_known_location,
                "Last known location: " + formatLocation(lastKnown));
        setText(view, R.id.vehicle_debug_orientation,
                "Orientation: " + formatDouble(status.getOrientation(), "\u00B0"));
        setText(view, R.id.vehicle_debug_last_known_orientation,
                "Last known orientation: " + formatDouble(status.getLastKnownOrientation(), "\u00B0"));

        // Block header
        setText(view, R.id.vehicle_debug_block_id,
                "Block ID: " + nullSafe(data.blockId));
        setText(view, R.id.vehicle_debug_block_trip_seq,
                "Block trip seq: " + status.getBlockTripSequence());

        // Block trips
        setBlockTrips(data.blockTrips);
    }

    private void setBlockTrips(List<BlockTripInfo> blockTrips) {
        View view = getView();
        if (view == null) {
            return;
        }

        LinearLayout container = view.findViewById(R.id.vehicle_debug_block_trips);
        container.removeAllViews();

        Context context = getActivity();

        for (BlockTripInfo info : blockTrips) {
            TextView tv = new TextView(context);
            tv.setPadding(0, 8, 0, 8);
            tv.setTextSize(13);
            tv.setBackground(ContextCompat.getDrawable(
                    context, android.R.drawable.list_selector_background));

            StringBuilder sb = new StringBuilder();
            if (info.routeShortName != null) {
                sb.append("[").append(info.routeShortName).append("] ");
            }
            if (info.headsign != null) {
                sb.append(info.headsign);
            } else {
                sb.append(info.tripId);
            }

            if (info.tripId.equals(mTripId)) {
                tv.setTypeface(null, Typeface.BOLD);
                tv.setText("\u25B6 " + sb.toString());
            } else {
                tv.setText(sb.toString());
            }

            final String tripId = info.tripId;
            tv.setOnClickListener(v -> TripDetailsActivity.start(getActivity(), tripId));

            container.addView(tv);
        }
    }

    private void setText(View root, int viewId, String text) {
        ((TextView) root.findViewById(viewId)).setText(text);
    }

    private String nullSafe(String value) {
        return TextUtils.isEmpty(value) ? "--" : value;
    }

    private String formatTime(long timeMillis) {
        if (timeMillis <= 0) {
            return "--";
        }
        String relative = DateUtils.getRelativeTimeSpanString(
                timeMillis, System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS).toString();
        return mTimeFmt.format(new Date(timeMillis)) + " (" + relative + ")";
    }

    private String formatLocation(Location loc) {
        if (loc == null) {
            return "--";
        }
        return String.format(Locale.US, "%.6f, %.6f", loc.getLatitude(), loc.getLongitude());
    }

    private String formatDouble(Double value, String suffix) {
        if (value == null) {
            return "--";
        }
        return String.format(Locale.US, "%.1f%s", value, suffix);
    }

    // -- Data classes --

    static class VehicleData {
        final ObaTripStatus status;
        final String routeId;
        final String blockId;
        final List<BlockTripInfo> blockTrips;

        VehicleData(ObaTripStatus status, String routeId, String blockId,
                List<BlockTripInfo> blockTrips) {
            this.status = status;
            this.routeId = routeId;
            this.blockId = blockId;
            this.blockTrips = blockTrips;
        }
    }

    static class BlockTripInfo {
        final String tripId;
        final String headsign;
        final String routeShortName;

        BlockTripInfo(String tripId, String headsign, String routeShortName) {
            this.tripId = tripId;
            this.headsign = headsign;
            this.routeShortName = routeShortName;
        }
    }

    // -- Loader --

    private final LoaderManager.LoaderCallbacks<VehicleData> mLoaderCallback =
            new LoaderManager.LoaderCallbacks<VehicleData>() {

                @Override
                public Loader<VehicleData> onCreateLoader(int id, Bundle args) {
                    return new VehicleDataLoader(getActivity(), mTripId);
                }

                @Override
                public void onLoadFinished(Loader<VehicleData> loader, VehicleData data) {
                    setData(data);
                }

                @Override
                public void onLoaderReset(Loader<VehicleData> loader) {
                }
            };

    /**
     * Single loader that fetches trip details and walks the block chain
     * via previousTripId/nextTripId to build the full ordered list of trips.
     */
    private static final class VehicleDataLoader extends AsyncTaskLoader<VehicleData> {

        private final String mTripId;
        private static final int MAX_CHAIN_PER_DIRECTION = 5;
        private VehicleData mCachedResult;

        VehicleDataLoader(Context context, String tripId) {
            super(context);
            mTripId = tripId;
        }

        @Override
        public void onStartLoading() {
            if (mCachedResult != null) {
                deliverResult(mCachedResult);
            } else {
                forceLoad();
            }
        }

        @Override
        public void deliverResult(VehicleData data) {
            mCachedResult = data;
            super.deliverResult(data);
        }

        @Override
        public VehicleData loadInBackground() {
            ObaTripDetailsResponse response =
                    ObaTripDetailsRequest.newRequest(getContext(), mTripId).call();
            if (response == null) {
                return new VehicleData(null, null, null,
                        Collections.<BlockTripInfo>emptyList());
            }

            ObaTripStatus status = response.getStatus();
            ObaReferences refs = response.getRefs();

            // Resolve route and block
            String routeId = null;
            String blockId = null;
            ObaTrip trip = (refs != null) ? refs.getTrip(mTripId) : null;
            if (trip != null) {
                routeId = trip.getRouteId();
                blockId = trip.getBlockId();
            }

            // Walk block chain
            List<BlockTripInfo> blockTrips = walkBlockChain(response);

            return new VehicleData(status, routeId, blockId, blockTrips);
        }

        private List<BlockTripInfo> walkBlockChain(ObaTripDetailsResponse startResponse) {
            ObaTripSchedule schedule = startResponse.getSchedule();
            if (schedule == null) {
                return Collections.singletonList(
                        makeTripInfo(mTripId, startResponse));
            }

            Set<String> seen = new HashSet<>();
            seen.add(mTripId);

            // Walk backward
            List<BlockTripInfo> before = new ArrayList<>();
            String prevId = schedule.getPreviousTripId();
            while (!TextUtils.isEmpty(prevId) && !seen.contains(prevId)
                    && before.size() < MAX_CHAIN_PER_DIRECTION) {
                seen.add(prevId);
                ObaTripDetailsResponse resp =
                        ObaTripDetailsRequest.newRequest(getContext(), prevId).call();
                if (resp == null) break;

                before.add(makeTripInfo(prevId, resp));

                ObaTripSchedule sched = resp.getSchedule();
                prevId = (sched != null) ? sched.getPreviousTripId() : null;
            }
            Collections.reverse(before);

            // Build result: before + current + forward
            List<BlockTripInfo> result = new ArrayList<>(before);
            result.add(makeTripInfo(mTripId, startResponse));

            // Walk forward
            int forwardCount = 0;
            String nextId = schedule.getNextTripId();
            while (!TextUtils.isEmpty(nextId) && !seen.contains(nextId)
                    && forwardCount < MAX_CHAIN_PER_DIRECTION) {
                seen.add(nextId);
                ObaTripDetailsResponse resp =
                        ObaTripDetailsRequest.newRequest(getContext(), nextId).call();
                if (resp == null) break;

                result.add(makeTripInfo(nextId, resp));
                forwardCount++;

                ObaTripSchedule sched = resp.getSchedule();
                nextId = (sched != null) ? sched.getNextTripId() : null;
            }

            return result;
        }

        private BlockTripInfo makeTripInfo(String tripId, ObaTripDetailsResponse resp) {
            ObaReferences refs = resp.getRefs();
            ObaTrip trip = (refs != null) ? refs.getTrip(tripId) : null;
            String headsign = null;
            String routeShortName = null;
            if (trip != null) {
                headsign = trip.getHeadsign();
                ObaRoute route = (refs != null) ? refs.getRoute(trip.getRouteId()) : null;
                if (route != null) {
                    routeShortName = route.getShortName();
                }
            }
            return new BlockTripInfo(tripId, headsign, routeShortName);
        }
    }
}
