/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida
 * and individual contributors
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
package org.onebusaway.android.region;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.RegionUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AsyncTask used to refresh region info from the Regions REST API.
 * <p/>
 * Classes utilizing this task can request a callback via MapModeController.Callback.setMyLocation()
 * by passing in class implementing MapModeController.Callback in the constructor
 *
 * @author barbeau
 */
public class ObaRegionsTask extends AsyncTask<Void, Integer, ArrayList<ObaRegion>> {

    public interface Callback {

        /**
         * Called when the ObaRegionsTask is complete
         *
         * @param currentRegionChanged true if the current region changed as a result of the task,
         *                             false if it didn't change
         */
        public void onRegionTaskFinished(boolean currentRegionChanged);
    }

    private static final String TAG = "ObaRegionsTask";

    private final int CALLBACK_DELAY = 100;  //in milliseconds

    private Context mContext;

    private ProgressDialog mProgressDialog;

    private String mProgressDialogMessage;

    private List<ObaRegionsTask.Callback> mCallbacks = new ArrayList<>();

    private final boolean mForceReload;

    private final boolean mShowProgressDialog;

    /**
     * GoogleApiClient being used for Location Services
     */
    GoogleApiClient mGoogleApiClient;

    /**
     * @param callbacks a callback will be made to all interfaces in this list after the task is
     *                 complete (null if no callbacks are requested)
     */
    public ObaRegionsTask(Context context, List<ObaRegionsTask.Callback> callbacks) {
        mContext = context;
        mCallbacks = callbacks;
        mForceReload = false;
        mShowProgressDialog = true;
    }

    /**
     * @param callbacks          a callback will be made to all interfaces in this list after the
     *                           task is complete (null if no callbacks are requested)
     * @param force              true if the task should be forced to update region info from the
     *                           server, false if it can return local info
     * @param showProgressDialog true if a progress dialog should be shown to the user during the
     *                           task, false if it should not
     */
    public ObaRegionsTask(Context context, List<ObaRegionsTask.Callback> callbacks, boolean force,
                          boolean showProgressDialog) {
        mContext = context;
        mCallbacks = callbacks;
        mForceReload = force;
        mShowProgressDialog = showProgressDialog;
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(context)
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(context);
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onPreExecute() {
        if (mShowProgressDialog && UIUtils.canManageDialog(mContext)) {
            if (mProgressDialogMessage == null){
                mProgressDialogMessage = mContext.getString(R.string.region_detecting_server);
            }
            mProgressDialog = ProgressDialog.show(mContext, "",
                    mProgressDialogMessage, true);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        super.onPreExecute();
    }

    @Override
    protected ArrayList<ObaRegion> doInBackground(Void... params) {
        return RegionUtils.getRegions(mContext, mForceReload);
    }

    @Override
    protected void onPostExecute(ArrayList<ObaRegion> results) {
        if (results == null) {
            //This is a catastrophic failure to load region info from all sources
            return;
        }

        // Dismiss the dialog before calling the callbacks to avoid errors referencing the dialog later
        if (mShowProgressDialog && UIUtils.canManageDialog(mContext) && mProgressDialog
                .isShowing()) {
            mProgressDialog.dismiss();
        }

        SharedPreferences settings = Application.getPrefs();

        if (settings
                .getBoolean(mContext.getString(R.string.preference_key_auto_select_region), true)) {
            // Pass in the GoogleApiClient initialized in constructor
            Location myLocation = Application.getLastKnownLocation(mContext, mGoogleApiClient);

            ObaRegion closestRegion = RegionUtils.getClosestRegion(results, myLocation, true);

            if (Application.get().getCurrentRegion() == null) {
                if (closestRegion != null) {
                    //No region has been set, so set region application-wide to closest region
                    Application.get().setCurrentRegion(closestRegion);
                    Log.d(TAG, "Detected closest region '" + closestRegion.getName() + "'");
                    //Analytics
                    ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                            mContext.getString(R.string.analytics_action_configured_region_auto),
                            mContext.getString(R.string.analytics_label_region_auto)
                                    + closestRegion.getName() + "; Old Region: null");
                    doCallback(true);
                } else {
                    //No region has been set, and we couldn't find a usable region based on RegionUtil.isRegionUsable()
                    //or we couldn't find a closest a region, so ask the user to pick the region
                    haveUserChooseRegion(results);
                }
            } else if (Application.get().getCurrentRegion() != null && closestRegion != null
                    && !Application.get().getCurrentRegion().equals(closestRegion)) {
                //User is closer to a different region than the current region, so change to the closest region
                String oldRegionName = Application.get().getCurrentRegion().getName();
                Application.get().setCurrentRegion(closestRegion);
                Log.d(TAG, "Detected closer region '" + closestRegion.getName()
                        + "', changed to this region.");
                //Analytics
                ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.APP_SETTINGS.toString(),
                        mContext.getString(R.string.analytics_action_configured_region_auto)
                        , mContext.getString(R.string.analytics_label_region_auto)
                                + closestRegion.getName() + "; Old Region: "
                                + oldRegionName);
                doCallback(true);
            } else if (Application.get().getCurrentRegion() != null && closestRegion != null
                    && Application.get().getCurrentRegion().equals(closestRegion)) {
                // Don't change the region - just refresh with latest Regions API contents
                Application.get().setCurrentRegion(closestRegion, false);
                doCallback(false);
            } else {
                doCallback(false);
            }
        } else {
            if (Application.get().getCurrentRegion() == null) {
                //We don't have a region selected, and the user chose not to auto-select one, so make them pick one
                haveUserChooseRegion(results);
            } else {
                doCallback(false);
            }
        }

        // Tear down Location Services client
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }

        super.onPostExecute(results);
    }

    private void haveUserChooseRegion(final ArrayList<ObaRegion> result) {
        if (!UIUtils.canManageDialog(mContext)) {
            return;
        }

        // Create dialog for user to choose
        List<String> serverNames = new ArrayList<String>();
        for (ObaRegion region : result) {
            if (RegionUtils.isRegionUsable(region)) {
                serverNames.add(region.getName());
            }
        }

        Collections.sort(serverNames);

        final CharSequence[] items = serverNames
                .toArray(new CharSequence[serverNames.size()]);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.region_choose_region));
        builder.setCancelable(false);
        builder.setItems(items, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int item) {
                for (ObaRegion region : result) {
                    if (region.getName().equals(items[item])) {
                        //Set the region application-wide
                        Application.get().setCurrentRegion(region);
                        Log.d(TAG, "User chose region '" + items[item] + "'.");
                        doCallback(true);
                        break;
                    }
                }
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    private void doCallback(final boolean currentRegionChanged) {
        // If we execute on same thread immediately after setting Region, map UI may try to call
        // OBA REST API before the new region info is set in Application.  So, pause briefly.
        final Handler mPauseForCallbackHandler = new Handler();
        final Runnable mPauseForCallback = new Runnable() {
            public void run() {
                // Map may not have triggered call to OBA REST API, so we force one here
                if (mCallbacks != null) {
                    for (Callback callback : mCallbacks) {
                        if (callback != null) {
                            callback.onRegionTaskFinished(currentRegionChanged);
                        }
                    }
                }
            }
        };
        mPauseForCallbackHandler.postDelayed(mPauseForCallback,
                CALLBACK_DELAY);
    }

    public void setProgressDialogMessage(String progressDialogMessage) {
        mProgressDialogMessage = progressDialogMessage;
    }
}
