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
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.map.googlemapsv2.ProprietaryMapHelpV2;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class TripPlanFragment extends Fragment {

    public TripPlanFragment setPlanErrorUrl(String planErrorUrl) {
        mPlanErrorUrl = planErrorUrl;
        return this;
    }

    public TripPlanFragment setPlanRequestUrl(String planRequestUrl) {
        mPlanRequestUrl = planRequestUrl;
        return this;
    }

    /**
     * Allows calling activity to register to know when to send request.
     */
    interface Listener {

        /**
         * Called when the fields have been populated and a trip plan can occur.
         */
        void onTripRequestReady();
    }

    private static final int USE_FROM_ADDRESS = 1;
    private static final int USE_TO_ADDRESS = 2;

    private AutoCompleteTextView mFromAddressTextArea;
    private AutoCompleteTextView mToAddressTextArea;
    private ImageButton mFromCurrentLocationImageButton;
    private ImageButton mToCurrentLocationImageButton;
    private Spinner mDate;
    private ArrayAdapter mDateAdapter;
    private Spinner mTime;
    private ArrayAdapter mTimeAdapter;
    private Spinner mLeavingChoice;
    ArrayAdapter<CharSequence> mLeavingChoiceAdapter;

    Calendar mMyCalendar;

    protected GoogleApiClient mGoogleApiClient;
    private static final String TAG = "TripPlanFragment";

    private CustomAddress mFromAddress, mToAddress;

    private TripRequestBuilder mBuilder;

    private Listener mListener;

    private String mPlanErrorUrl;

    private String mPlanRequestUrl;

    private void resetDateTimeLabels() {
        String dateText = new SimpleDateFormat(OTPConstants.TRIP_PLAN_DATE_STRING_FORMAT, Locale.getDefault())
                .format(mMyCalendar.getTime());
        String timeText = new SimpleDateFormat(OTPConstants.TRIP_PLAN_TIME_STRING_FORMAT, Locale.getDefault())
                .format(mMyCalendar.getTime());

        mDateAdapter.insert(dateText, 0);
        mDateAdapter.notifyDataSetChanged();

        mTimeAdapter.insert(timeText, 0);
        mTimeAdapter.notifyDataSetChanged();
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
        loadAndSetAdditionalTripPreferences();

        final View view = inflater.inflate(R.layout.fragment_trip_plan, container, false);
        setHasOptionsMenu(true);

        mFromAddressTextArea = (AutoCompleteTextView) view.findViewById(R.id.fromAddressTextArea);
        mToAddressTextArea = (AutoCompleteTextView) view.findViewById(R.id.toAddressTextArea);
        mFromCurrentLocationImageButton = (ImageButton) view.findViewById(R.id.fromCurrentLocationImageButton);
        mToCurrentLocationImageButton = (ImageButton) view.findViewById(R.id.toCurrentLocationImageButton);
        mDate = (Spinner) view.findViewById(R.id.date);
        mDateAdapter = new ArrayAdapter(getActivity(), R.layout.simple_list_item);
        mDate.setAdapter(mDateAdapter);

        mTime = (Spinner) view.findViewById(R.id.time);
        mTimeAdapter = new ArrayAdapter(getActivity(), R.layout.simple_list_item);
        mTime.setAdapter(mTimeAdapter);

        mLeavingChoice = (Spinner) view.findViewById(R.id.leavingChoiceSpinner);

        mLeavingChoiceAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.trip_plan_leaving_arriving_array, R.layout.simple_list_item);
        mLeavingChoiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mLeavingChoice.setAdapter(mLeavingChoiceAdapter);

        // Set mLeavingChoice onclick adapter in onResume() so we do not fire it when setting it
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

        mDate.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    new DatePickerDialog(view.getContext(), dateCallback, mMyCalendar
                            .get(Calendar.YEAR), mMyCalendar.get(Calendar.MONTH),
                            mMyCalendar.get(Calendar.DAY_OF_MONTH)).show();
                }

                return true;
            }
        });

        mTime.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    new TimePickerDialog(view.getContext(), timeCallback,
                            mMyCalendar.get(Calendar.HOUR_OF_DAY),
                            mMyCalendar.get(Calendar.MINUTE), false).show();
                }
                return true;
            }
        });

        ImageButton resetTimeButton = (ImageButton) view.findViewById(R.id.resetTimeImageButton);

        resetTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMyCalendar = Calendar.getInstance();
                mBuilder.setDateTime(mMyCalendar);
                resetDateTimeLabels();
                checkRequestAndSubmit();
            }
        });

        setUpAutocomplete(mFromAddressTextArea, USE_FROM_ADDRESS);
        setUpAutocomplete(mToAddressTextArea, USE_TO_ADDRESS);

        mToCurrentLocationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mToAddressTextArea.setText(getString(R.string.tripplanner_current_location));
                mToAddress = makeAddressFromLocation();
                mBuilder.setTo(mToAddress);
                checkRequestAndSubmit();
            }
        });

        mFromCurrentLocationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFromAddressTextArea.setText(getString(R.string.tripplanner_current_location));
                mFromAddress = makeAddressFromLocation();
                mBuilder.setFrom(mFromAddress);
                checkRequestAndSubmit();
            }
        });

        // Start: default from address is Current Location, to address is unset
        return view;
    }

    private void loadAndSetAdditionalTripPreferences() {
        int modeId = PreferenceUtils.getInt(getString(R.string.preference_key_trip_plan_travel_by), 0);
        double maxWalkDistance = PreferenceUtils.getDouble(getString(R.string.preference_key_trip_plan_maximum_walking_distance), 0);
        boolean optimizeTransfers = PreferenceUtils.getBoolean(getString(R.string.preference_key_trip_plan_minimize_transfers), false);
        boolean wheelchair = PreferenceUtils.getBoolean(getString(R.string.preference_key_trip_plan_avoid_stairs), false);

        mBuilder.setOptimizeTransfers(optimizeTransfers)
                .setModeSetById(modeId)
                .setWheelchairAccessible(wheelchair)
                .setMaxWalkDistance(maxWalkDistance);

    }

    private void checkRequestAndSubmit() {
        if (mBuilder.ready() && mListener != null) {
            mListener.onTripRequestReady();
        }
    }

    // Populate data fields
    @Override
    public void onResume() {
        super.onResume();

        mFromAddress = mBuilder.getFrom();

        if (mFromAddress == null) {
            mFromAddress = makeAddressFromLocation();
            mBuilder.setFrom(mFromAddress);
        }

        setAddressText(mFromAddressTextArea, mFromAddress);

        mToAddress = mBuilder.getTo();

        if (mToAddress == null) {
            mToAddress = CustomAddress.getEmptyAddress();
            mBuilder.setTo(mToAddress);
        }

        setAddressText(mToAddressTextArea, mToAddress);

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

        switch (id) {
            case R.id.action_settings:
                advancedSettings();
                return true;
            case R.id.action_reverse:
                reverseTrip();
                return true;
            case R.id.action_report_trip_problem:
                reportTripPlanProblem();
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Set Listener of this fragment.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void advancedSettings() {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());

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

                final TypedArray transitModeResource = getContext().getResources().obtainTypedArray(R.array.transit_mode_array);

                int modeId = TripModes.getTripModeCodeFromSelection(transitModeResource.getResourceId(spinnerTravelBy.getSelectedItemPosition(), 0));

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

                // Save the additional trip preferences in SharePreferences so it is remembered
                // after app is closed
                PreferenceUtils.saveInt(getString(R.string.preference_key_trip_plan_travel_by), modeId);
                PreferenceUtils.saveDouble(getString(R.string.preference_key_trip_plan_maximum_walking_distance), maxWalkDistance);
                PreferenceUtils.saveBoolean(getString(R.string.preference_key_trip_plan_minimize_transfers), optimizeTransfers);
                PreferenceUtils.saveBoolean(getString(R.string.preference_key_trip_plan_avoid_stairs), wheelchair);

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

        ArrayList<String> travelByOptions = new ArrayList<String>(Arrays.asList(getResources().getStringArray(R.array.transit_mode_array)));

        // Remove opttions based on support of bikeshare enabled or not
        if (!Application.isBikeshareEnabled()) {
            travelByOptions.remove(getString(R.string.transit_mode_bikeshare));
            travelByOptions.remove(getString(R.string.transit_mode_transit_and_bikeshare));
        }

        ArrayAdapter adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, travelByOptions);


        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTravelBy.setAdapter(adapter);

        int modeSetId = mBuilder.getModeSetId();
        if (modeSetId != -1 && modeSetId < travelByOptions.size()) {
            spinnerTravelBy.setSelection(TripModes.getSpinnerPositionFromSeledctedCode(modeSetId));
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

        mBuilder.setFrom(mFromAddress).setTo(mToAddress);

        setAddressText(mFromAddressTextArea, mFromAddress);
        setAddressText(mToAddressTextArea, mToAddress);

        if (mBuilder.ready() && mListener != null) {
            mListener.onTripRequestReady();
        }
    }

    private void reportTripPlanProblem() {
        String email = Application.get().getCurrentRegion().getOtpContactEmail();
        if (!TextUtils.isEmpty(email)) {
            Location loc = Application.getLastKnownLocation(getActivity().getApplicationContext(),
                    null);
            String locString = null;
            if (loc != null) {
                locString = LocationUtils.printLocationDetails(loc);
            }
            if (mPlanErrorUrl != null) {
                UIUtils.sendEmail(getActivity(), email, locString, mPlanErrorUrl, true);
            } else if (mPlanRequestUrl != null) {
                UIUtils.sendEmail(getActivity(), email, locString, mPlanRequestUrl, false);
            } else {
                UIUtils.sendEmail(getActivity(), email, locString, null, true);
            }
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_problem),
                    getString(R.string.analytics_label_app_feedback_otp));
        }
    }

    private void setAddressText(TextView tv, CustomAddress address) {
        if (address != null && address.getAddressLine(0) != null) {
            tv.setText(address.toString());
        } else {
            tv.setText(null);
        }
    }

    private CustomAddress makeAddressFromLocation() {
        CustomAddress address = CustomAddress.getEmptyAddress();

        Location loc = Application.getLastKnownLocation(getContext(), mGoogleApiClient);
        if (loc == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), getString(R.string.main_location_unavailable), Toast.LENGTH_SHORT).show();
            }
        } else {
            address.setLatitude(loc.getLatitude());
            address.setLongitude(loc.getLongitude());
        }

        address.setAddressLine(0, getString(R.string.tripplanner_current_location));
        return address;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != -1) {
            Log.e(TAG, "Error getting geocoding results");
            return;
        }

        CustomAddress address = ProprietaryMapHelpV2.getCustomAddressFromPlacesIntent(Application.get().getApplicationContext(), intent);

        // Note that onResume will run after this function. We need to put new objects in the bundle.
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
                } else if (use == USE_TO_ADDRESS) {
                    mToAddress = addr;
                    mBuilder.setTo(mToAddress);
                }

                checkRequestAndSubmit();
            }
        });
    }

}

