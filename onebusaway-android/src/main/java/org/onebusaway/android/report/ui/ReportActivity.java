/*
* Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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

package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.dialog.CustomerServiceDialog;
import org.onebusaway.android.report.ui.dialog.RegionValidateDialog;
import org.onebusaway.android.ui.PreferencesActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

/**
 * Fragment Activity for handling all report
 *
 * @author Cagri Cetin
 */
public class ReportActivity extends BaseReportActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.report);

        if (savedInstanceState == null) {
            //Region Validation
            ObaRegion currentRegion = Application.get().getCurrentRegion();
            if (currentRegion != null) {
                RegionValidateDialog rvd = new RegionValidateDialog();
                rvd.show(getSupportFragmentManager(), ReportConstants.TAG_REGION_VALIDATE_DIALOG);
            } else {
                createIssueTypeListFragment();
            }
        }

        setUpProgressBar();
    }

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static void start(Context context,
                                   String focusId,
                                   double lat,
                                   double lon) {
        context.startActivity(makeIntent(context, focusId, lat, lon));
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static Intent makeIntent(Context context,
                                          String focusId,
                                          double lat,
                                          double lon) {
        Intent myIntent = new Intent(context, ReportActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, focusId);
        myIntent.putExtra(MapParams.CENTER_LAT, lat);
        myIntent.putExtra(MapParams.CENTER_LON, lon);
        return myIntent;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void createPreferencesActivity() {
        Intent preferences = new Intent(ReportActivity.this, PreferencesActivity.class);
        startActivity(preferences);
    }

    public void createIssueTypeListFragment() {
        setFragment(new ReportTypeListFragment(), R.id.r_fragment_layout).commit();
    }

    public void createInfrastructureIssueActivity() {
        finish();
        InfrastructureIssueActivity.start(this, getIntent());
    }

    public void createCustomerServiceFragment() {
//        setFragment(new CustomerServiceFragment(), R.id.r_fragment_layout).commit();
        CustomerServiceDialog csd = new CustomerServiceDialog();
        csd.show(getSupportFragmentManager(), ReportConstants.TAG_CUSTOMER_SERVICE_FRAGMENT);
    }

    public void createBusStopTutorialFragment() {
        TutorialFragment tutorialFragment = new TutorialFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(TutorialFragment.STRING_RESOURCE_ID, R.array.report_stop_issue_tutorial_desc);
        bundle.putInt(TutorialFragment.IMAGE_RESOURCE_ID, R.array.report_stop_issue_tutorial_images);
        tutorialFragment.setArguments(bundle);
        setFragment(tutorialFragment, R.id.r_fragment_layout).commit();
    }

    @Override
    public void onBackPressed() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            super.onBackPressed();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }
}