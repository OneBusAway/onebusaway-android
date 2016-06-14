/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.util.LocationUtil;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.RegionUtils;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.ViewGroup;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class TripPlanFragment extends Fragment {

    private static final int FROM_ADDRESS_GEOCODE = 1;
    private static final int TO_ADDRESS_GEOCODE = 2;

    private TextView fromAddressTextArea;
    private TextView toAddressTextArea;
    private ImageButton planMyTripButton;
    private ImageButton fromCurrentLocationImageButton;
    private ImageButton toCurrentLocationImageButton;
    private TextView date;
    private TextView time;
    private TextView leavingChoice;

    Calendar myCalendar;
    boolean leaving = true;

    protected GoogleApiClient mGoogleApiClient;
    private static final String TAG = "TripPlanFragment";

    private CustomAddress fromAddress, toAddress;

    private TripRequestBuilder builder;

    private void resetDateTimeLabels() {
        String dateText = new SimpleDateFormat(OTPConstants.TRIP_PLAN_DATE_STRING_FORMAT, Locale.getDefault())
                .format(myCalendar.getTime());
        String timeText = new SimpleDateFormat(OTPConstants.TRIP_PLAN_TIME_STRING_FORMAT, Locale.getDefault())
                .format(myCalendar.getTime());

        date.setText(dateText);
        time.setText(timeText);
    }

    // Create view, initialize state
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity())
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(getContext());
            mGoogleApiClient.connect();
        }

        Bundle bundle = getArguments();
        builder = new TripRequestBuilder(bundle);

        final View view = inflater.inflate(R.layout.fragment_trip_plan, container, false);
        setHasOptionsMenu(true);

        fromAddressTextArea = (TextView) view.findViewById(R.id.fromAddressTextArea);
        toAddressTextArea = (TextView) view.findViewById(R.id.toAddressTextArea);
        planMyTripButton = (ImageButton) view.findViewById(R.id.planMyTripButton);
        fromCurrentLocationImageButton = (ImageButton) view.findViewById(R.id.fromCurrentLocationImageButton);
        toCurrentLocationImageButton = (ImageButton) view.findViewById(R.id.toCurrentLocationImageButton);
        date = (TextView) view.findViewById(R.id.date);
        time = (TextView) view.findViewById(R.id.time);
        leavingChoice = (TextView) view.findViewById(R.id.leavingChoice);

        leavingChoice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (leaving) {
                    leaving = false;
                    leavingChoice.setText(getString(R.string.trip_plan_arriving));
                    builder.setArrivalTime(myCalendar);
                } else {
                    leaving = true;
                    leavingChoice.setText(getString(R.string.trip_plan_leaving));
                    builder.setDepartureTime(myCalendar);
                }
            }
        });

        final TimePickerDialog.OnTimeSetListener timeCallback = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hour, int minute) {
                myCalendar.set(Calendar.HOUR_OF_DAY, hour);
                myCalendar.set(Calendar.MINUTE, minute);
                resetDateTimeLabels();
                builder.setDateTime(myCalendar);
            }
        };

        time.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(v.getContext(), timeCallback, myCalendar.get(Calendar.HOUR_OF_DAY), myCalendar.get(Calendar.MINUTE), false).show();
            }
        });

        final DatePickerDialog.OnDateSetListener dateCallback = new DatePickerDialog.OnDateSetListener() {

            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear,
                                  int dayOfMonth) {
                myCalendar.set(Calendar.YEAR, year);
                myCalendar.set(Calendar.MONTH, monthOfYear);
                myCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                resetDateTimeLabels();
                builder.setDateTime(myCalendar);
            }

        };

        date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(view.getContext(), dateCallback, myCalendar
                        .get(Calendar.YEAR), myCalendar.get(Calendar.MONTH),
                        myCalendar.get(Calendar.DAY_OF_MONTH)).show();
            }
        });

        ImageButton resetTimeButton = (ImageButton) view.findViewById(R.id.resetTimeImageButton);
        resetTimeButton.setColorFilter(Color.WHITE);

        resetTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myCalendar = Calendar.getInstance();
                builder.setDateTime(myCalendar);
                resetDateTimeLabels();
            }
        });

        fromAddressTextArea.setOnClickListener(new StartAutocompleteOnClick(FROM_ADDRESS_GEOCODE));
        toAddressTextArea.setOnClickListener(new StartAutocompleteOnClick(TO_ADDRESS_GEOCODE));

        toCurrentLocationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAddressText(toAddressTextArea, getString(R.string.tripplanner_current_location));
                toAddress = makeAddressFromLocation();
            }
        });

        fromCurrentLocationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAddressText(fromAddressTextArea, getString(R.string.tripplanner_current_location));
                fromAddress = makeAddressFromLocation();
            }
        });

        planMyTripButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (fromAddress == null || toAddress == null) {
                    makeNoLocationToast();
                    return;
                }

                builder
                        .setFrom(fromAddress)
                        .setTo(toAddress);

                if (leaving)
                    builder.setDepartureTime(myCalendar);
                else
                    builder.setArrivalTime(myCalendar);


                ((TripPlanActivity) getActivity()).route();

            }
        });

        // Start: default fromAddress is Current Location, to address is unset

        return view;
    }

    // Populate data fields
    @Override
    public void onResume() {
        super.onResume();

        fromAddress = builder.getFrom();

        if (fromAddress == null)
            fromAddress = makeAddressFromLocation();
        else
            setAddressText(fromAddressTextArea, fromAddress.toString());


        toAddress = builder.getTo();
        if (toAddress != null)
            setAddressText(toAddressTextArea, toAddress.toString());


        if (myCalendar == null) {
            Date date = builder.getDateTime();
            if (date == null)
                date = new Date();
            myCalendar = Calendar.getInstance();
            myCalendar.setTime(date);
        }


        resetDateTimeLabels();

        boolean leaving = builder.getArriveBy();
        String leavingString = getString(leaving ? R.string.trip_plan_arriving : R.string.trip_plan_leaving);
        leavingChoice.setText(leavingString);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_trip_plan, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            advancedSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void advancedSettings() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity())
                .setView(R.layout.trip_plan_advanced_settings_dialog);

        final AlertDialog dialog = dialogBuilder.create();

        dialog.show();

        final TypedArray transitModeResource = getContext().getResources().obtainTypedArray(R.array.transit_mode_array);
        final CheckBox minimizeTransfersCheckbox = (CheckBox) dialog.findViewById(R.id.checkbox_minimize_transfers);
        final Spinner spinnerTravelBy = (Spinner) dialog.findViewById(R.id.spinner_travel_by);
        final CheckBox wheelchairCheckbox = (CheckBox) dialog.findViewById(R.id.checkbox_wheelchair_acccesible);
        final EditText maxWalkEditText = (EditText) dialog.findViewById(
                R.id.number_maximum_walk_distance);

        minimizeTransfersCheckbox.setChecked(builder.getOptimizeTransfers());

        wheelchairCheckbox.setChecked(builder.getWheelchairAccessible());

        ArrayAdapter adapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.transit_mode_array, R.layout.advanced_settings_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTravelBy.setAdapter(adapter);

        int modeSetId = builder.getModeSetId();
        if (modeSetId != -1) {
            for (int i = 0; i < transitModeResource.length(); i++) {
                if (transitModeResource.getResourceId(i, -1) == modeSetId) {
                    spinnerTravelBy.setSelection(i);
                    break;
                }
            }
        }

        final boolean unitsAreImperial = !RegionUtils.getUnitsAreMetric(getContext());

        Double maxWalk = builder.getMaxWalkDistance();
        if (maxWalk != null && Double.MAX_VALUE != maxWalk) {
            if (unitsAreImperial)
                maxWalk *= 3.281;
            maxWalkEditText.setText(String.format("%d", maxWalk.longValue()));
        }


        if (unitsAreImperial) {
            TextView label = (TextView) dialog.findViewById(R.id.label_minimum_walk_distance);
            label.setText(getString(R.string.distance_feet));
        }

        dialog.findViewById(R.id.advanced_settings_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();

                boolean optimizeTransfers = minimizeTransfersCheckbox.isChecked();

                int modeId = transitModeResource.getResourceId(spinnerTravelBy.getSelectedItemPosition(), 0);

                boolean wheelchair = wheelchairCheckbox.isChecked();

                String maxWalkString = maxWalkEditText.getText().toString();
                double maxWalkDistance;
                if (TextUtils.isEmpty(maxWalkString)) {
                    maxWalkDistance = Double.MAX_VALUE;
                } else {
                    double d = Double.parseDouble(maxWalkString);
                    maxWalkDistance = unitsAreImperial ? d / 3.281 : d;
                }

                builder.setOptimizeTransfers(optimizeTransfers)
                        .setModeSetById(modeId)
                        .setWheelchairAccessible(wheelchair)
                        .setMaxWalkDistance(maxWalkDistance);
            }
        });
    }

    private void makeNoLocationToast() {
        Toast.makeText(getContext(), getString(R.string.tripplanner_error_no_location), Toast.LENGTH_SHORT).show();
    }

    private CustomAddress makeAddressFromLocation() {
        Locale locale = Locale.getDefault();

        Location loc = Application.getLastKnownLocation(getContext(), mGoogleApiClient);
        if (loc == null) {
            if (getContext() != null)
                Toast.makeText(getContext(), getString(R.string.main_location_unavailable), Toast.LENGTH_SHORT).show();
            return null;
        }
        CustomAddress address = new CustomAddress(locale);
        address.setAddressLine(0, getString(R.string.tripplanner_current_location));
        address.setLatitude(loc.getLatitude());
        address.setLongitude(loc.getLongitude());
        return address;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != -1) {
            Log.e(TAG, "Error getting geocoding results");
            return;
        }

        Place place = PlaceAutocomplete.getPlace(getActivity(), intent);

        // note that onResume will run after this function. We need to put new objects in the bundle.

        if (requestCode == FROM_ADDRESS_GEOCODE) {
            fromAddress = new CustomAddress();
            builder.setFrom(fromAddress);
            handlePlaceResult(place, fromAddress, fromAddressTextArea);
        } else if (requestCode == TO_ADDRESS_GEOCODE) {
            toAddress = new CustomAddress();
            builder.setTo(toAddress);
            handlePlaceResult(place, toAddress, toAddressTextArea);
        }

    }

    private void handlePlaceResult(Place place, CustomAddress address, TextView tv) {
        address.setAddressLine(0, place.getName().toString());
        address.setAddressLine(1, place.getAddress().toString());

        LatLng loc = place.getLatLng();

        address.setLatitude(loc.latitude);
        address.setLongitude(loc.longitude);

        setAddressText(tv, place.getName());
    }

    public void setAddressText(TextView tv, CharSequence text) {
        tv.setTextColor(getResources().getColor(R.color.body_text_1));
        tv.setText(text);
    }

    private class StartAutocompleteOnClick implements View.OnClickListener {

        int requestCode;

        StartAutocompleteOnClick(int requestCode) {
            this.requestCode = requestCode;
        }

        @Override
        public void onClick(View v) {
            try {
                ObaRegion region = Application.get().getCurrentRegion();
                LatLngBounds bounds = LocationUtil.getRegionBounds(region);

                Intent intent =
                        new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY)
                                .setBoundsBias(bounds)
                                .build(getActivity());

                startActivityForResult(intent, requestCode);
            } catch (Exception e) {
                Log.e(TAG, "Error getting autocomplete: " + e.toString());
            }

        }
    }
}

