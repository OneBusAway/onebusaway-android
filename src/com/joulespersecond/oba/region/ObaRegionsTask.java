/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com) 
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
package com.joulespersecond.oba.region;

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * AsyncTask used to refresh region info from the Regions REST API.
 * 
 * Classes utilizing this task can request a callback via MapModeController.Callback.setMyLocation()
 * by passing in class implementing MapModeController.Callback in the constructor
 * 
 * @author barbeau
 *
 */
public class ObaRegionsTask extends AsyncTask<Void, Integer, ArrayList<ObaRegion>> {    
    public interface Callback {
        /**
         * Be aware that if mProgressDialog = true, the user will continue to see
         * the indeterminate progress bar until the implementing method returns
         */
        public void onRegionUpdated();
    }
    
    private static final String TAG = "ObaRegionsTask";
    
    private Context mContext;
    private ProgressDialog mProgressDialog;
    private ObaRegionsTask.Callback mCallback;
    
    private final boolean mForceReload;
    private final boolean mShowProgressDialog;
    
    /** 
     * @param context
     * @param callback a callback will be made via the interface after the task is complete and a new region has been selected
     */
    public ObaRegionsTask(Context context, ObaRegionsTask.Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        mForceReload = false;
        mShowProgressDialog = true;
    }
    
    /** 
     * @param context
     * @param callback a callback will be made via interface after the task is complete and a new region has been selected
     * @param force true if the task should be forced to update region info from the server, false if it can return local info
     * @param showProgressDialog true if a progress dialog should be shown to the user during the task, false if it should not
     */
    public ObaRegionsTask(Context context, ObaRegionsTask.Callback callback, boolean force, boolean showProgressDialog) {
        this.mContext = context;
        this.mCallback = callback;
        mForceReload = force;
        mShowProgressDialog = showProgressDialog;
    }
    
    @Override
    protected void onPreExecute() {
        if(mShowProgressDialog){
            mProgressDialog = ProgressDialog.show(mContext, "",
                    mContext.getString(R.string.region_detecting_server), true);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }
        
        super.onPreExecute();
    }

    @Override
    protected ArrayList<ObaRegion> doInBackground(Void... params) {
        ArrayList<ObaRegion> results;
        if (!mForceReload) {
            //
            // Check the DB
            //
            results = RegionUtils.getRegionsFromProvider(mContext);
            if (results != null) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions from database."); }
                return results;
            }
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from database was null."); }
        }

        results = RegionUtils.getRegionsFromServer(mContext);
        if (results == null || results.isEmpty()) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from server was null or empty."); }
            
            if (mForceReload) {
                //If we tried to force a reload from the server, then we haven't tried to reload from local provider yet
                results = RegionUtils.getRegionsFromProvider(mContext);
                if (results != null) {
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions from database."); }
                    return results;
                }else{
                    if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from database was null."); }
                }
            }
            
            //If we reach this point, the call to the Regions REST API failed and no results were
            //available locally from a prior server request.        
            //Fetch regions from local resource file as last resort (otherwise user can't use app)
            results = RegionUtils.getRegionsFromResources(mContext);
            
            if (results == null) {
                //This is a complete failure to load region info from all sources, app will be useless
                if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from local resource file was null."); }                
                return results;
            }            
            
            if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions from local resource file."); }
        }else{
            if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions list from server."); }
            //Update local time for when the last region info was retrieved from the server
            Application.get().setLastRegionUpdateDate(new Date().getTime());
        }       
        
        //If the region info came from the server or local resource file, we need to save it to the local provider
        RegionUtils.saveToProvider(mContext, results);
        return results;
    }
    
     @Override
    protected void onPostExecute(ArrayList<ObaRegion> results) {
         if (results == null) {
             //This is a catastrophic failure to load region info from all sources
             return;
         }
         
        //TODO - Make new request from NETWORK_PROVIDER asynchronously, since LocationManager.getLastKnownLocation() 
        //is buggy, and NETWORK_PROVIDER should return with a new coarse location (WiFi or cell) quickly
        Location myLocation = UIHelp.getLocation2(mContext);
                
        ObaRegion closestRegion = RegionUtils.getClosestRegion(results, myLocation); 
       
        if (Application.get().getCurrentRegion() == null) {
            if(closestRegion != null){
                //No region has been set, so set region application-wide to closest region
                Application.get().setCurrentRegion(closestRegion);
                if (BuildConfig.DEBUG) { Log.d(TAG, "Detected closest region '" + closestRegion.getName() + "'"); }
                doCallback();
            }else{
                //No region has been set, and we couldn't find a usable regions based on RegionUtil.isRegionUsable()
                //or we couldn't find a closest a region, so ask the user to pick the region                      
                haveUserChooseRegion(results);
            }
        }else if (Application.get().getCurrentRegion() != null && closestRegion != null && !Application.get().getCurrentRegion().equals(closestRegion)){
                //User is closer to a different region than the current region, so change to the closest region
                Application.get().setCurrentRegion(closestRegion);
                if (BuildConfig.DEBUG) { Log.d(TAG, "Detected closer region '" + closestRegion.getName() + "', changed to this region."); }
                doCallback();
        }
        
        if (mShowProgressDialog && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
         
        super.onPostExecute(results);
    }
     
    private void haveUserChooseRegion(final ArrayList<ObaRegion> result){
        // Create dialog for user to choose
        List<String> serverNames = new ArrayList<String>();
        for (ObaRegion region : result) {
            serverNames.add(region.getName());
        }

        final CharSequence[] items = serverNames
                .toArray(new CharSequence[serverNames.size()]);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.region_choose_region));
        builder.setItems(items, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int item) {
                for (ObaRegion region : result) {                    
                    if (region.getName().equals(items[item])) {
                        //Set the region application-wide
                        Application.get().setCurrentRegion(region);                                                
                        if (BuildConfig.DEBUG) { Log.d(TAG, "User chose region '" + items[item] + "'."); }
                        doCallback();
                        break;
                    }
                }                
            }
        });
        
        AlertDialog alert = builder.create();
        alert.show();
    }
    
    private void doCallback(){
        
        //If we execute on same thread immediately after setting Region, map UI may try to call
        //OBA REST API before the new region info is set in Application.  So, pause briefly.
        final Handler mPauseForCallbackHandler = new Handler();
        final Runnable mPauseForCallback = new Runnable() {
            public void run() {
                //Map may not have triggered call to OBA REST API, so we force one here
                mCallback.onRegionUpdated();            
            }
        };        
        mPauseForCallbackHandler.postDelayed(mPauseForCallback,
                100);
    }
}
