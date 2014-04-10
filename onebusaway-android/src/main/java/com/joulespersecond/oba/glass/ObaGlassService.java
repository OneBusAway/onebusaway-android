/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.joulespersecond.oba.glass;

import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.TimelineManager;

import com.joulespersecond.oba.elements.ObaStop;
import com.joulespersecond.oba.region.ObaRegionsTask;
import com.joulespersecond.oba.request.ObaStopsForLocationResponse;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;
import com.joulespersecond.seattlebusbot.GlassArrivalsListActivity;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.Date;

/**
 * The main application service that manages the lifetime of the compass live card and the objects
 * that help out with orientation tracking and landmarks.
 */
public class ObaGlassService extends Service
        implements ObaRegionsTask.Callback, ObaStopsForLocationTask.Callback {

    private static final String LIVE_CARD_ID = "onebusaway";

    private static final long REGION_UPDATE_THRESHOLD = 1000 * 60 * 60 * 24 * 7;
    //One week, in milliseconds

    private static final String TAG = "ObaGlassService";

    ObaRegionsTask mObaRegionsTask;

    ObaStopsForLocationTask mObaStopsForLocationTask;

    /**
     * A binder that gives other components access to the speech capabilities provided by the
     * service.
     */
    public class GlassBinder extends Binder {

        /**
         * Read the current heading aloud using the text-to-speech engine.
         */
        public void readHeadingAloud() {
            // float heading = mOrientationManager.getHeading();

            Resources res = getResources();
            String[] spokenDirections = res.getStringArray(R.array.spoken_directions);
            String directionName = spokenDirections[0];

            int roundedHeading = 1;
            int headingFormat;
            if (roundedHeading == 1) {
                headingFormat = R.string.spoken_heading_format_one;
            } else {
                headingFormat = R.string.spoken_heading_format;
            }

            String headingText = res.getString(headingFormat, roundedHeading, directionName);
            mSpeech.speak(headingText, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private final GlassBinder mBinder = new GlassBinder();

    private TextToSpeech mSpeech;

    private TimelineManager mTimelineManager;

    private LiveCard mLiveCard;

    @Override
    public void onCreate() {
        super.onCreate();

        mTimelineManager = TimelineManager.from(this);

        // Even though the text-to-speech engine is only used in response to a menu action, we
        // initialize it when the application starts so that we avoid delays that could occur
        // if we waited until it was needed to start it up.
        mSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // Do nothing.
            }
        });

        SensorManager sensorManager =
                (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);

//        mOrientationManager = new OrientationManager(sensorManager, locationManager);
//        mLandmarks = new Landmarks(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        publishCard(this);

        boolean forceReload = false;
        boolean showProgressDialog = true;

        SharedPreferences settings = Application.getPrefs();

        //If we don't have region info selected, or if enough time has passed since last region info update AND user has selected auto-refresh,
        //force contacting the server again
        if (Application.get().getCurrentRegion() == null ||
                (settings.getBoolean(getString(R.string.preference_key_auto_refresh_regions), true)
                        &&
                        new Date().getTime() - Application.get().getLastRegionUpdateDate()
                                > REGION_UPDATE_THRESHOLD)
                ) {
            forceReload = true;
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "Region info has expired (or does not exist), forcing a reload from the server...");
            }
        }

        if (Application.get().getCurrentRegion() != null) {
            //We already have region info locally, so just check current region status quietly in the background
            showProgressDialog = false;

            //Get stop information
            mObaStopsForLocationTask = new ObaStopsForLocationTask(this, this);
            mObaStopsForLocationTask.execute();
        }

        //Check region status, possibly forcing a reload from server and checking proximity to current region
        //Normal progress dialog doesn't work, so hard-code false as last argument
        mObaRegionsTask = new ObaRegionsTask(this, this, true, false);
        mObaRegionsTask.execute();

        return START_STICKY;
    }

    private void publishCard(Context context) {
        if (mLiveCard == null) {
            TimelineManager tm = TimelineManager.from(context);
            mLiveCard = tm.createLiveCard(LIVE_CARD_ID);

            mLiveCard.setViews(new RemoteViews(context.getPackageName(),
                    R.layout.glass_please_wait_card));
            Intent intent = new Intent(context, GlassMenuActivity.class);
            mLiveCard.setAction(PendingIntent.getActivity(context, 0,
                    intent, 0));
            mLiveCard.publish(LiveCard.PublishMode.REVEAL);
        } else {
            // Card is already published.
            return;
        }
    }

    @Override
    public void onDestroy() {
        unpublishCard(this);

        mSpeech.shutdown();

        mSpeech = null;
//        mOrientationManager = null;
//        mLandmarks = null;

        super.onDestroy();
    }

    private void unpublishCard(Context context) {
        if (mLiveCard != null) {
            mLiveCard.unpublish();
            mLiveCard = null;
        }
    }

    /*
     * Callbacks
     */

    // For Oba Regions Task
    @Override
    public void onTaskFinished(boolean currentRegionChanged) {
        Log.d(TAG, "Got regions, now looking for stops...");
        if (currentRegionChanged) {
            // The current region has changed since last startup, so abort any existing stop
            // request in progress (which would be using the previous region API) and start a new one
            if (mObaStopsForLocationTask != null) {
                mObaStopsForLocationTask.cancel(true);
            }

            // Find the closest stops
            mObaStopsForLocationTask = new ObaStopsForLocationTask(this, this);
            mObaStopsForLocationTask.execute();
        }
    }

    // For StopsForLocation Request
    @Override
    public void onTaskFinished(ObaStopsForLocationResponse response) {
        Log.d(TAG, "Found stops.");
        // Find closest stop
        ObaStop closestStop = UIHelp.getClosestStop(this, response.getStops());

        if (closestStop != null) {
            Log.d(TAG, "Closest stop is: " + closestStop.getName());
            GlassArrivalsListActivity.start(this, closestStop);
        } else {
            Log.d(TAG, "No stops returned");
        }

        // Show stop info on screen
        //mLiveCard.setViews()

        // Set up options menu showing next 5 closest stops, ordered by distance

    }
}
