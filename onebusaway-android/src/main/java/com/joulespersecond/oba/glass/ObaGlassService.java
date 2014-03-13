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

import com.joulespersecond.oba.region.ObaRegionsTask;
import com.joulespersecond.seattlebusbot.R;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.widget.RemoteViews;

/**
 * The main application service that manages the lifetime of the compass live card and the objects
 * that help out with orientation tracking and landmarks.
 */
public class ObaGlassService extends Service implements ObaRegionsTask.Callback {

    private static final String LIVE_CARD_ID = "onebusaway";

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

        new ObaRegionsTask(this, this, true, false).execute();

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

    @Override
    public void onTaskFinished(boolean currentRegionChanged) {
        // Find the closest stops

        // Initial load of initial stop info

        // Show stop info on screen
        //mLiveCard.setViews()

        // Set up options menu showing next 5 closest stops, ordered by distance

    }
}
