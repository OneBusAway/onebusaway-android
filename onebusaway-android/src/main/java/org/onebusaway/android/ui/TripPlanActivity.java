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

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.directions.tasks.TripRequest;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.util.UIUtils;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.ws.Message;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class TripPlanActivity extends AppCompatActivity implements TripRequest.Callback {

    private static final String TAG = "TripPlanActivity";

    TripRequestBuilder mBuilder;

    TripRequest mTripRequest = null;

    SlidingUpPanelLayout mPanel;

    AlertDialog mFeedbackDialog;

    private static final String PLAN_ERROR_CODE = "org.onebusaway.android.PLAN_ERROR_CODE";

    private static final String PLAN_ERROR_URL = "org.onebusaway.android.PLAN_ERROR_URL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_trip_plan);

        UIUtils.setupActionBar(this);

        Bundle bundle = (savedInstanceState == null) ? new Bundle() : savedInstanceState;
        mBuilder = new TripRequestBuilder(bundle);

        // Check which fragment to create
        boolean haveTripPlan = bundle.getSerializable(OTPConstants.ITINERARIES) != null;

        TripPlanFragment fragment = new TripPlanFragment();
        fragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.trip_plan_fragment_container, fragment).commit();


        if (haveTripPlan) {
            initResultsFragment();
        }

        // if planning in progress, reshow dialog
        if (mTripRequest != null) {
            mTripRequest.showProgressDialog();
        }

        // if were showing error dialog, reshow it
        int planErrorCode = bundle.getInt(PLAN_ERROR_CODE);
        String planErrorUrl = bundle.getString(PLAN_ERROR_URL);
        if (!TextUtils.isEmpty(planErrorUrl)) {
            showFeedbackDialog(planErrorCode, planErrorUrl);
        }

        mPanel = (SlidingUpPanelLayout) findViewById(R.id.trip_plan_sliding_layout);

        // Set the height of the panel after drawing occurs.
        final ViewGroup layout = (ViewGroup)findViewById(R.id.trip_plan_fragment_container);
        ViewTreeObserver vto = layout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                layout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                int viewHeight = findViewById(android.R.id.content).getHeight();
                int height = layout.getMeasuredHeight();
                mPanel.setPanelHeight(viewHeight - height);
            }
        });

    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {

        if (mBuilder != null) {
            mBuilder.copyIntoBundle(bundle);

            // We also saved the itineraries and the results state in this bundle.

            Bundle source = mBuilder.getBundle();

            ArrayList<Itinerary> itineraries = (ArrayList<Itinerary>) source
                    .getSerializable(OTPConstants.ITINERARIES);
            if (itineraries != null) {
                bundle.putSerializable(OTPConstants.ITINERARIES, itineraries);
            }

            int rank = source.getInt(OTPConstants.SELECTED_ITINERARY);
            bundle.putInt(OTPConstants.SELECTED_ITINERARY, rank);

            boolean showMap = source.getBoolean(OTPConstants.SHOW_MAP);
            bundle.putBoolean(OTPConstants.SHOW_MAP, showMap);

            int planErrorCode = source.getInt(PLAN_ERROR_CODE);
            String planErrorUrl = source.getString(PLAN_ERROR_URL);
            if (!TextUtils.isEmpty(planErrorUrl)) {
                bundle.putInt(PLAN_ERROR_CODE, planErrorCode);
                bundle.putString(PLAN_ERROR_URL, planErrorUrl);
            }
        }

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

        if (mFeedbackDialog != null && mFeedbackDialog.isShowing()) {
            mFeedbackDialog.dismiss();
        }
    }

    public void route() {
        // Remove results fragment if it exists
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.trip_results_fragment_container);
        if(fragment != null)
            getSupportFragmentManager().beginTransaction().remove(fragment).commit();

        mTripRequest = mBuilder.setListener(this).execute(this);

        // clear out selected itinerary from bundle
        Bundle bundle = mBuilder.getBundle();
        bundle.remove(OTPConstants.ITINERARIES);
        bundle.remove(OTPConstants.SELECTED_ITINERARY);
    }

    private void initResultsFragment() {

        TripResultsFragment fragment = new TripResultsFragment();
        fragment.setArguments(mBuilder.getBundle());
        getSupportFragmentManager().beginTransaction()
                .add(R.id.trip_results_fragment_container, fragment).commit();

        getSupportFragmentManager().executePendingTransactions();

        if (fragment.getView() != null) {
            fragment.getView().post(new Runnable() {
                @Override
                public void run() {
                    mPanel.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                }
            });
        }

    }

    @Override
    public void onTripRequestComplete(List<Itinerary> itineraries) {
        Log.d(TAG, "Successfully routed. Itineraries are " + itineraries);

        mTripRequest = null;

        if (itineraries.size() > 0) {

            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.trip_results_fragment_container);
            if (fragment != null && fragment instanceof TripResultsFragment) {
                Bundle bundle = fragment.getArguments();
                bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<>(itineraries));
                // bundle arguments already set
                ((TripResultsFragment) fragment).displayNewResults();
                return;
            }

            Bundle bundle = mBuilder.getBundle();
            bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<>(itineraries));

            // Commit the transaction.
            try {
                initResultsFragment();
            } catch(IllegalStateException ex) {
                // We can't save the itinerary if this method is called after onSaveInstanceState.
                // Ultimately the architecture should be changed, but for now simply tell the
                // user to try again.
                Log.e(TAG, "Error attempting to switch fragments. Likely screen rotated during trip plan.");
                Toast.makeText(this,
                        getString(R.string.tripplanner_error_not_defined),
                        Toast.LENGTH_SHORT).show();
            }

        } else {
            Toast.makeText(this,
                    getString(R.string.tripplanner_error_bogus_parameter),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTripRequestFailure(int errorCode, String url) {
        Bundle bundle = mBuilder.getBundle();
        bundle.putInt(PLAN_ERROR_CODE, errorCode);
        bundle.putString(PLAN_ERROR_URL, url);

        showFeedbackDialog(errorCode, url);
    }

    private void showFeedbackDialog(int errorCode, final String url) {

        String msg = getString(R.string.tripplanner_error_not_defined);

        if (errorCode > 0 && errorCode != Message.PLAN_OK.getId()) {
            msg = getErrorMessage(errorCode);
        }

        AlertDialog.Builder feedback = new AlertDialog.Builder(this)
                .setTitle(R.string.tripplanner_error_dialog_title)
                .setMessage(msg);

        feedback.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                clearBundleErrors();
            }
        });

        feedback.setNegativeButton(R.string.report_problem_report, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // This will become a separate OTP email address
                String email = Application.get().getCurrentRegion().getContactEmail();
                if (!TextUtils.isEmpty(email)) {
                    UIUtils.sendEmailOTP(TripPlanActivity.this, email, url);
                    ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                            getString(R.string.analytics_action_problem),
                            getString(R.string.analytics_label_app_feedback_otp));
                }
                clearBundleErrors();
            }
        });

        mFeedbackDialog = feedback.create();
        mFeedbackDialog.show();
    }

    private String getErrorMessage(int errorCode) {
        if (errorCode == Message.SYSTEM_ERROR.getId()) {
            return (getString(R.string.tripplanner_error_system));
        } else if (errorCode == Message.OUTSIDE_BOUNDS.getId()) {
            return (getString(R.string.tripplanner_error_outside_bounds));
        } else if (errorCode == Message.PATH_NOT_FOUND.getId()) {
            return (getString(R.string.tripplanner_error_path_not_found));
        } else if (errorCode == Message.NO_TRANSIT_TIMES.getId()) {
            return (getString(R.string.tripplanner_error_no_transit_times));
        } else if (errorCode == Message.REQUEST_TIMEOUT.getId()) {
            return (getString(R.string.tripplanner_error_request_timeout));
        } else if (errorCode == Message.BOGUS_PARAMETER.getId()) {
            return (getString(R.string.tripplanner_error_bogus_parameter));
        } else if (errorCode == Message.GEOCODE_FROM_NOT_FOUND.getId()) {
            return (getString(R.string.tripplanner_error_geocode_from_not_found));
        } else if (errorCode == Message.GEOCODE_TO_NOT_FOUND.getId()) {
            return (getString(R.string.tripplanner_error_geocode_to_not_found));
        } else if (errorCode == Message.GEOCODE_FROM_TO_NOT_FOUND.getId()) {
            return (getString(R.string.tripplanner_error_geocode_from_to_not_found));
        } else if (errorCode == Message.TOO_CLOSE.getId()) {
            return (getString(R.string.tripplanner_error_too_close));
        } else if (errorCode == Message.LOCATION_NOT_ACCESSIBLE.getId()) {
            return (getString(R.string.tripplanner_error_location_not_accessible));
        } else if (errorCode == Message.GEOCODE_FROM_AMBIGUOUS.getId()) {
            return (getString(R.string.tripplanner_error_geocode_from_ambiguous));
        } else if (errorCode == Message.GEOCODE_TO_AMBIGUOUS.getId()) {
            return (getString(R.string.tripplanner_error_geocode_to_ambiguous));
        } else if (errorCode == Message.GEOCODE_FROM_TO_AMBIGUOUS.getId()) {
            return (getString(R.string.tripplanner_error_geocode_from_to_ambiguous));
        } else if (errorCode == Message.UNDERSPECIFIED_TRIANGLE.getId()
                || errorCode == Message.TRIANGLE_NOT_AFFINE.getId()
                || errorCode == Message.TRIANGLE_OPTIMIZE_TYPE_NOT_SET.getId()
                || errorCode == Message.TRIANGLE_VALUES_NOT_SET.getId()) {
            return (getString(R.string.tripplanner_error_triangle));
        } else {
            return null;
        }
    }

    private void clearBundleErrors() {
        mBuilder.getBundle().remove(PLAN_ERROR_CODE);
        mBuilder.getBundle().remove(PLAN_ERROR_URL);
    }

}
