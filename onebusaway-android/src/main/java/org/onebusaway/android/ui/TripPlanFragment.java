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
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.util.ConversionUtils;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.PlacesAutoCompleteAdapter;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.map.googlemapsv2.ProprietaryMapHelpV2;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
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

    private static final int USE_FROM_ADDRESS = 1;
    private static final int USE_TO_ADDRESS = 2;

    private AutoCompleteTextView mFromAddressTextArea;
    private AutoCompleteTextView mToAddressTextArea;
    private ImageButton mFromCurrentLocationImageButton;
    private ImageButton mToCurrentLocationImageButton;
    private TextView mDate;
    private TextView mTime;
    private Spinner mLeavingChoice;
    ArrayAdapter<CharSequence> mLeavingChoiceAdapter;

    Calendar mMyCalendar;

    protected GoogleApiClient mGoogleApiClient;
    private static final String TAG = "TripPlanFragment";

    private CustomAddress mFromAddress, mToAddress;

    private TripRequestBuilder mBuilder;

    private void resetDateTimeLabels() {
        String dateText = new SimpleDateFormat(OTPConstants.TRIP_PLAN_DATE_STRING_FORMAT, Locale.getDefault())
                .format(mMyCalendar.getTime());
        String timeText = new SimpleDateFormat(OTPConstants.TRIP_PLAN_TIME_STRING_FORMAT, Locale.getDefault())
                .format(mMyCalendar.getTime());

        mDate.setText(dateText);
        mTime.setText(timeText);
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
        mBuilder = new TripRequestBuilder(bundle);

        final View view = inflater.inflate(R.layout.fragment_trip_plan, container, false);
        setHasOptionsMenu(true);

        mFromAddressTextArea = (AutoCompleteTextView) view.findViewById(R.id.fromAddressTextArea);
        mToAddressTextArea = (AutoCompleteTextView) view.findViewById(R.id.toAddressTextArea);
        mFromCurrentLocationImageButton = (ImageButton) view.findViewById(R.id.fromCurrentLocationImageButton);
        mToCurrentLocationImageButton = (ImageButton) view.findViewById(R.id.toCurrentLocationImageButton);
        mDate = (TextView) view.findViewById(R.id.date);
        mTime = (TextView) view.findViewById(R.id.time);
        mLeavingChoice = (Spinner) view.findViewById(R.id.leavingChoiceSpinner);

        mLeavingChoiceAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.trip_plan_leaving_arriving_array, android.R.layout.simple_spinner_item);
        mLeavingChoiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mLeavingChoice.setAdapter(mLeavingChoiceAdapter);

        // set onclick adapter in onresume so we do not fire it when setting it.

        final TimePickerDialog.OnTimeSetListener timeCallback = new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hour, int minute) {
                mMyCalendar.set(Calendar.HOUR_OF_DAY, hour);
                mMyCalendar.set(Calendar.MINUTE, minute);
                resetDateTimeLabels();
                mBuilder.setDateTime(mMyCalendar);
                checkRequestAndSubmit();
            }
        };

        mTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(v.getContext(), timeCallback, mMyCalendar.get(Calendar.HOUR_OF_DAY), mMyCalendar.get(Calendar.MINUTE), false).show();
            }
        });

        final DatePickerDialog.OnDateSetListener dateCallback = new DatePickerDialog.OnDateSetListener() {

            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear,
                                  int dayOfMonth) {
                mMyCalendar.set(Calendar.YEAR, year);
                mMyCalendar.set(Calendar.MONTH, monthOfYear);
                mMyCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                resetDateTimeLabels();
                mBuilder.setDateTime(mMyCalendar);
                checkRequestAndSubmit();
            }

        };

        mDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(view.getContext(), dateCallback, mMyCalendar
                        .get(Calendar.YEAR), mMyCalendar.get(Calendar.MONTH),
                        mMyCalendar.get(Calendar.DAY_OF_MONTH)).show();
            }
        });

        ImageButton resetTimeButton = (ImageButton) view.findViewById(R.id.resetTimeImageButton);

        resetTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMyCalendar = Calendar.getInstance();
                mBuilder.setDateTime(mMyCalendar);
                resetDateTimeLabels();
            }
        });

        setUpAutocomplete(mFromAddressTextArea, USE_FROM_ADDRESS);
        setUpAutocomplete(mToAddressTextArea, USE_TO_ADDRESS);

        mToCurrentLocationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mToAddressTextArea.setText(getString(R.string.tripplanner_current_location));
                mToAddress = makeAddressFromLocation();
            }
        });

        mFromCurrentLocationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFromAddressTextArea.setText(getString(R.string.tripplanner_current_location));
                mFromAddress = makeAddressFromLocation();
            }
        });


        // Start: default from address is Current Location, to address is unset

        return view;
    }

    private void checkRequestAndSubmit() {
        if (mBuilder.ready()) {
            ((TripPlanActivity) getActivity()).route();
        }
    }

    // Populate data fields
    @Override
    public void onResume() {
        super.onResume();

        mFromAddress = mBuilder.getFrom();

        if (mFromAddress == null) {
            mFromAddress = makeAddressFromLocation();
        }
        else {
            mFromAddressTextArea.setText(mFromAddress.toString());
        }


        mToAddress = mBuilder.getTo();
        if (mToAddress != null) {
            mToAddressTextArea.setText(mToAddress.toString());
        }


        boolean arriving = mBuilder.getArriveBy();


        if (mMyCalendar == null) {
            Date date = mBuilder.getDateTime();
            if (date == null) {
                date = new Date();
            }
            mMyCalendar = Calendar.getInstance();
            mMyCalendar.setTime(date);

            if (arriving) {
                mBuilder.setArrivalTime(mMyCalendar);
            } else {
                mBuilder.setDepartureTime(mMyCalendar);
            }
        }

        resetDateTimeLabels();

        String leavingChoice = getString(arriving ? R.string.trip_plan_arriving : R.string.trip_plan_leaving);
        mLeavingChoice.setSelection(mLeavingChoiceAdapter.getPosition(leavingChoice), false);

        mLeavingChoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                String item = (String) parent.getItemAtPosition(position);

                if (item.equals(getString(R.string.trip_plan_arriving))) {
                    mBuilder.setArrivalTime(mMyCalendar);
                } else {
                    mBuilder.setDepartureTime(mMyCalendar);
                }
                checkRequestAndSubmit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_trip_plan, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case R.id.action_settings:
                advancedSettings();
                return true;
            case R.id.action_reverse:
                reverseTrip();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void advancedSettings() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

        final TypedArray transitModeResource = getContext().getResources().obtainTypedArray(R.array.transit_mode_array);
        final boolean unitsAreImperial = !PreferenceUtils.getUnitsAreMetricFromPreferences(getContext());

        dialogBuilder.setTitle(R.string.trip_plan_advanced_settings)
                .setView(R.layout.trip_plan_advanced_settings_dialog);

        dialogBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {

                Dialog dialog = (Dialog) dialogInterface;

                boolean optimizeTransfers = ((CheckBox) dialog.findViewById(R.id.checkbox_minimize_transfers))
                        .isChecked();

                Spinner spinnerTravelBy = (Spinner) dialog.findViewById(R.id.spinner_travel_by);

                int modeId = transitModeResource.getResourceId(spinnerTravelBy.getSelectedItemPosition(), 0);

                boolean wheelchair = ((CheckBox) dialog.findViewById(R.id.checkbox_wheelchair_acccesible))
                        .isChecked();

                String maxWalkString = ((EditText) dialog.findViewById(
                        R.id.number_maximum_walk_distance)).getText().toString();
                double maxWalkDistance;
                if (TextUtils.isEmpty(maxWalkString)) {
                    maxWalkDistance = Double.MAX_VALUE;
                } else {
                    double d = Double.parseDouble(maxWalkString);
                    maxWalkDistance = unitsAreImperial ? ConversionUtils.feetToMeters(d) : d;
                }

                mBuilder.setOptimizeTransfers(optimizeTransfers)
                        .setModeSetById(modeId)
                        .setWheelchairAccessible(wheelchair)
                        .setMaxWalkDistance(maxWalkDistance);

                checkRequestAndSubmit();
            }
        });


        final AlertDialog dialog = dialogBuilder.create();

        dialog.show();

        CheckBox minimizeTransfersCheckbox = (CheckBox) dialog.findViewById(R.id.checkbox_minimize_transfers);
        Spinner spinnerTravelBy = (Spinner) dialog.findViewById(R.id.spinner_travel_by);
        CheckBox wheelchairCheckbox = (CheckBox) dialog.findViewById(R.id.checkbox_wheelchair_acccesible);
        EditText maxWalkEditText = (EditText) dialog.findViewById(
                R.id.number_maximum_walk_distance);

        minimizeTransfersCheckbox.setChecked(mBuilder.getOptimizeTransfers());

        wheelchairCheckbox.setChecked(mBuilder.getWheelchairAccessible());

        ArrayAdapter adapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.transit_mode_array, android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTravelBy.setAdapter(adapter);

        int modeSetId = mBuilder.getModeSetId();
        if (modeSetId != -1) {
            for (int i = 0; i < transitModeResource.length(); i++) {
                if (transitModeResource.getResourceId(i, -1) == modeSetId) {
                    spinnerTravelBy.setSelection(i);
                    break;
                }
            }
        }

        Double maxWalk = mBuilder.getMaxWalkDistance();
        if (maxWalk != null && Double.MAX_VALUE != maxWalk) {
            if (unitsAreImperial) {
                maxWalk = ConversionUtils.metersToFeet(maxWalk);
            }
            maxWalkEditText.setText(String.format("%d", maxWalk.longValue()));
        }


        if (unitsAreImperial) {
            TextView label = (TextView) dialog.findViewById(R.id.label_minimum_walk_distance);
            label.setText(getString(R.string.feet_abbreviation));
        }

    }

    private void reverseTrip() {
        mFromAddress = mBuilder.getTo();
        mToAddress = mBuilder.getFrom();

        mBuilder
                .setFrom(mFromAddress)
                .setTo(mToAddress);

        mFromAddressTextArea.setText(mFromAddress.toString());
        mToAddressTextArea.setText(mToAddress.toString());

        ((TripPlanActivity) getActivity()).route();
    }

    private void makeNoLocationToast() {
        Toast.makeText(getContext(), getString(R.string.tripplanner_error_no_location), Toast.LENGTH_SHORT).show();
    }

    private CustomAddress makeAddressFromLocation() {
        Locale locale = Locale.getDefault();

        Location loc = Application.getLastKnownLocation(getContext(), mGoogleApiClient);
        if (loc == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.main_location_unavailable), Toast.LENGTH_SHORT).show();
            }
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

        CustomAddress address = ProprietaryMapHelpV2.getCustomAddressFromPlacesIntent(Application.get().getApplicationContext(), intent);

        // note that onResume will run after this function. We need to put new objects in the bundle.

        if (requestCode == USE_FROM_ADDRESS) {
            mFromAddress = address;
            mBuilder.setFrom(mFromAddress);
            mFromAddressTextArea.setText(mFromAddress.toString());
        } else if (requestCode == USE_TO_ADDRESS) {
            mToAddress = address;
            mBuilder.setTo(mToAddress);
            mToAddressTextArea.setText(mToAddress.toString());
        }

        checkRequestAndSubmit();

    }

    private void setUpAutocomplete(AutoCompleteTextView tv, final int use) {

        ObaRegion region = Application.get().getCurrentRegion();

        // Use Google widget if available

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext())
                == ConnectionResult.SUCCESS) {

            tv.setFocusable(false);
            tv.setOnClickListener(new ProprietaryMapHelpV2.StartPlacesAutocompleteOnClick(use, this, region));

            return;
        }

        // else, set up autocomplete with Android geocoder

        tv.setAdapter(new PlacesAutoCompleteAdapter(getContext(), android.R.layout.simple_list_item_1, region));

        tv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CustomAddress addr = (CustomAddress) parent.getAdapter().getItem(position);

                if (use == USE_FROM_ADDRESS) {
                    mFromAddress = addr;
                    mBuilder.setFrom(mFromAddress);
                }
                else if (use == USE_TO_ADDRESS) {
                    mToAddress = addr;
                    mBuilder.setTo(mToAddress);
                }

                checkRequestAndSubmit();
            }
        });

    }
}

