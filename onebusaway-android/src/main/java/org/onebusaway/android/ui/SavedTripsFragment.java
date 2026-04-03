/*
 * Copyright (C) 2026 Divesh
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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.onebusaway.android.R;
import org.onebusaway.android.database.savedtrips.SavedTripsManager;
import org.onebusaway.android.database.savedtrips.entity.SavedTripEntity;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.directions.util.ItineraryJsonConverter;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.opentripplanner.api.model.Itinerary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavedTripsFragment extends Fragment {

    private static final String TAG = "SavedTripsFragment";

    private RecyclerView mRecyclerView;
    private TextView mEmptyView;
    private SavedTripsAdapter mAdapter;
    private List<SavedTripEntity> mTrips = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved_trips, container, false);

        mRecyclerView = view.findViewById(R.id.saved_trips_list);
        mEmptyView = view.findViewById(R.id.saved_trips_empty);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new SavedTripsAdapter();
        mRecyclerView.setAdapter(mAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTrips();
    }

    private void loadTrips() {
        if (!isAdded()) {
            return;
        }
        mTrips.clear();
        mTrips.addAll(SavedTripsManager.getAllTrips(requireContext()));
        mAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (mTrips.isEmpty()) {
            mRecyclerView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mRecyclerView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }

    private void onTripClicked(SavedTripEntity trip) {
        Itinerary itinerary = ItineraryJsonConverter.INSTANCE.fromJson(trip.getItineraryJson());
        if (itinerary == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.saved_trips_data_error,
                        Toast.LENGTH_SHORT).show();
            }
            Log.w(TAG, "Failed to deserialize itinerary for trip id=" + trip.getId());
            return;
        }

        ArrayList<Itinerary> itineraries = new ArrayList<>();
        itineraries.add(itinerary);

        Bundle extras = new Bundle();
        CustomAddress from = new CustomAddress(Locale.getDefault());
        from.setAddressLine(0, trip.getFromAddress());
        from.setLatitude(trip.getFromLat());
        from.setLongitude(trip.getFromLon());

        CustomAddress to = new CustomAddress(Locale.getDefault());
        to.setAddressLine(0, trip.getToAddress());
        to.setLatitude(trip.getToLat());
        to.setLongitude(trip.getToLon());

        TripRequestBuilder builder = new TripRequestBuilder(extras);
        builder.setFrom(from);
        builder.setTo(to);

        Intent intent = new Intent(requireContext(), TripPlanActivity.class);
        intent.putExtras(extras);
        intent.putExtra(OTPConstants.ITINERARIES, itineraries);
        intent.putExtra(OTPConstants.INTENT_SOURCE, OTPConstants.Source.NOTIFICATION);
        startActivity(intent);
    }

    private void onFavoriteClicked(SavedTripEntity trip, int position) {
        SavedTripsManager.toggleFavorite(requireContext(), trip);
        mTrips.set(position, trip.withToggledFavorite());
        mAdapter.notifyItemChanged(position);
    }

    private void confirmDelete(SavedTripEntity trip) {
        if (!isAdded()) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.saved_trips_delete_confirm)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SavedTripsManager.deleteTrip(requireContext(), trip);
                        int currentPos = mTrips.indexOf(trip);
                        if (currentPos >= 0) {
                            mTrips.remove(currentPos);
                            mAdapter.notifyItemRemoved(currentPos);
                        }
                        updateEmptyState();
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.saved_trips_deleted,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private class SavedTripsAdapter extends RecyclerView.Adapter<SavedTripsAdapter.ViewHolder> {

        private final SimpleDateFormat mDateFmt = new SimpleDateFormat(
                OTPConstants.TRIP_PLAN_DATE_STRING_FORMAT, Locale.getDefault());

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_trip, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SavedTripEntity trip = mTrips.get(position);
            holder.bind(trip);
        }

        @Override
        public int getItemCount() {
            return mTrips.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView nameView;
            private final TextView fromView;
            private final TextView toView;
            private final TextView dateView;
            private final ImageView favoriteIcon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                nameView = itemView.findViewById(R.id.trip_name);
                fromView = itemView.findViewById(R.id.trip_from);
                toView = itemView.findViewById(R.id.trip_to);
                dateView = itemView.findViewById(R.id.trip_date);
                favoriteIcon = itemView.findViewById(R.id.trip_favorite_icon);
            }

            void bind(SavedTripEntity trip) {
                nameView.setText(trip.getName());
                fromView.setText(getString(R.string.saved_trips_from_address,
                        trip.getFromAddress()));
                toView.setText(getString(R.string.saved_trips_to_address,
                        trip.getToAddress()));

                dateView.setText(mDateFmt.format(new Date(trip.getCreatedAt())));

                favoriteIcon.setImageResource(trip.getFavorite()
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off);

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onTripClicked(trip);
                    }
                });

                itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        confirmDelete(trip);
                        return true;
                    }
                });

                favoriteIcon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int pos = getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            onFavoriteClicked(trip, pos);
                        }
                    }
                });
            }
        }
    }
}
