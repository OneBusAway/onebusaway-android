/*
 * Copyright (C) 2005-2019 University of South Florida
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
package org.onebusaway.android.nav;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.nav.model.Path;
import org.onebusaway.android.nav.model.PathLink;
import org.onebusaway.android.ui.TripDetailsActivity;
import org.onebusaway.android.util.RegionUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.text.DecimalFormat;
import java.util.Locale;

import androidx.core.app.NotificationCompat;

/**
 * This class provides the navigation functionality for the destination reminders
 */
public class NavigationServiceProvider implements TextToSpeech.OnInitListener {

    public static final String TAG = "NavServiceProvider";

    private static final int EVENT_TYPE_NO_EVENT = 0;
    private static final int EVENT_TYPE_UPDATE_DISTANCE = 1;
    private static final int EVENT_TYPE_GET_READY = 2;
    private static final int EVENT_TYPE_PULL_CORD = 3;
    private static final int EVENT_TYPE_INITIAL_STARTUP = 4;

    private static final int ALERT_STATE_NONE = -1;
    private static final int ALERT_STATE_SHOWN_TO_RIDER = 0;
    private static final int ALERT_STATE_ENDING_PATH_LINK = 1;

    public static final int NOTIFICATION_ID = 33620;
    private static final long[] VIBRATION_PATTERN = new long[]{
            2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000
    };
    public static final int DISTANCE_THRESHOLD = 200;

    // Number of times to repeat voice commands
    private static final int NUM_PULL_CORD_REPEAT = 10;
    private static final int NUM_GET_READY_REPEAT = 2;

    private ProximityCalculator mProxCalculator;

    private int mTimeout = 60;  //Timeout value for service provider action (default = 60 seconds);

    // Index that defines the current path link within the path (i.e. First link in a path will have index = 0, second link index = 1, etc.)
    private int mPathLinkIndex = 0;

    // Path links being navigated
    private Path mPath;

    private float mAlertDistance = -1;

    private boolean mWaitingForConfirm = false;

    private Location mCurrentLocation = null;

    private boolean mResuming = false;   // Is Trip being resumed?
    public boolean mFinished = false;   // Trip has finished.  //Change to public
    public boolean mGetReady = false;   // Get Ready triggered. //Change to public

    public float mSectoCurDistance = -1;

    public static TextToSpeech mTTS;          // TextToSpeech for speaking commands.

    SharedPreferences mSettings = Application.getPrefs();  // Shared Prefs

    private String mTripId;             // Trip ID
    private String mStopId;             // Stop ID

    private FirebaseAnalytics mFirebaseAnalytics;

    public NavigationServiceProvider(String tripId, String stopId) {
        Log.d(TAG, "Creating NavigationServiceProvider...");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(Application.get().getApplicationContext());
        if (mTTS == null) {
            mTTS = new TextToSpeech(Application.get().getApplicationContext(), this);
        }
        mTripId = tripId;
        mStopId = stopId;

    }

    public NavigationServiceProvider(String tripId, String stopId, int flag) {
        Log.d(TAG, "Creating NavigationServiceProvider...");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(Application.get().getApplicationContext());
        mResuming = flag == 1;
        if (mTTS == null) {
            mTTS = new TextToSpeech(Application.get().getApplicationContext(), this);
        }
        mTripId = tripId;
        mStopId = stopId;
    }

    /**
     * Initialize ProximityCalculator
     * Proximity listener will be created only upon selection of service to navigate
     */
    private void lazyProxInitialization() {
        Log.d(TAG, "ProximityCalculator initializing...");
        mProxCalculator = null;
        mProxCalculator = new ProximityCalculator(this);
    }

    /**
     * Returns true if user has been notified to get ready.
     */
    public boolean getGetReady() {
        return mGetReady;
    }

    /**
     * Returns true if trip is done.
     */
    public boolean getFinished() {
        return mFinished;
    }

    /**
     * Returns the index of the current path link
     */
    public int getPathLinkIndex() {
        return mPathLinkIndex;
    }

    /**
     * Navigates a navigation path which is composed of path links
     */
    public void navigate(Path path) {
        Log.d(TAG, "Starting navigation for service");

        // Create a new instance and rewrite the old one with a blank slate of ProximityListener
        lazyProxInitialization();
        mPath = path;
        mPathLinkIndex = 0;
        Log.d(TAG, "Number of path links: " + mPath.getPathLinks().size());

        // Create new coordinate object using the "Ring" coordinates
        Location firstLocation = mPath.getPathLinks().get(mPathLinkIndex).getOriginLocation();
        Location secondToLastLocation = mPath.getPathLinks().get(mPathLinkIndex).getSecondToLastLocation();
        Location lastLocation = mPath.getPathLinks().get(mPathLinkIndex).getDestinationLocation();

        mAlertDistance = mPath.getPathLinks().get(mPathLinkIndex).getAlertDistance();

        // Have proximity listener listen for the "Ring" location
        mProxCalculator.listenForDistance(mAlertDistance);
        mProxCalculator.listenForLocation(firstLocation, secondToLastLocation, lastLocation);
        mProxCalculator.mReady = false;
        mProxCalculator.mTrigger = false;
    }

    /**
     * Resets any current routes which might be currently navigated
     */
    public void reset() {
        mProxCalculator.listenForLocation(null, null, null);
    }

    public void setTimeout(int timeout) {
        mTimeout = timeout;
    }

    public int getTimeout() {
        return mTimeout;
    }

    /**
     * Sets the radius of detection for the ProximityListener
     */
    public void setRadius(float radius) {
        mProxCalculator.setRadius(radius);
    }

    /**
     * Returns true if there is another path link to be navigated as part of the current path that is being navigated, false if there is not another link
     *
     * @return true if there is another path link to be navigated as part of the current path that is being navigated, false if there is not another link
     */
    public boolean hasMorePathLinks() {
        Log.d(TAG, "Checking if path has more path links left to be navigated");
        if (mPath == null || (mPathLinkIndex >= (mPath.getPathLinks().size() - 1))) {
            // No more path links exist
            Log.d(TAG, "PathLink index: " + mPathLinkIndex + " Number of PathLinks: " + (mPath.getPathLinks().size()));
            Log.d(TAG, "%%%%%%N%%%%%%%%% No more PathLinks left in Path %%%%%%%%%%%%%%%%%%%%%");
            return false;
        }
        // Additional path links still need to be navigated as part of this service
        Log.d(TAG, "More path links left");
        return true;

    }

    /**
     * Tells the NavigationProvider to navigate the next PathLink in the Path
     */
    private void navigateNextPathLink() {
        Log.d(TAG, "Attempting to navigate next path link");
        if (mPath != null && mPathLinkIndex < mPath.getPathLinks().size()) {
            mPathLinkIndex++;

            // Create new location using the "Ring" coordinates
            PathLink link = mPath.getPathLinks().get(mPathLinkIndex);
            mAlertDistance = link.getAlertDistance();

            // Have proximity listener listen for the "Ring" location
            mProxCalculator.listenForDistance(mAlertDistance);
            mProxCalculator.listenForLocation(
                    link.getOriginLocation(),
                    link.getSecondToLastLocation(),
                    link.getDestinationLocation());
            Log.d(TAG, "ProxCalculator parameters were set!");
        }
    }

    /**
     * Called from LocationListener.locationUpdated() in order to supply the Navigation Provider with the most recent location
     */
    public void locationUpdated(Location l) {
        mCurrentLocation = l;
        mProxCalculator.checkProximityAll(mCurrentLocation);
    }

    private int sendCounter = 0;

    public void skipPathLink() {
        if (hasMorePathLinks()) {
            Log.d(TAG, "About to switch link - from skipPathLink");
            navigateNextPathLink();
            // Reset the "get ready" notification alert
            mProxCalculator.setReady(false);
        } else {
            Log.d(TAG, "No more path links!");
        }
        // Reset the proximity notification alert
        mProxCalculator.setTrigger(false);
    }

    /**
     * Detects proximity to a latitude and longitude to help navigate a path link
     */
    public class ProximityCalculator {

        NavigationServiceProvider mNavProvider;

        private float mRadius = 100;
        //Defines radius (in meters) for which the Proximity listener should be triggered (Default = 50)

        private float readyRadius = 300;
        //Defines radius(in meters) for which the Proximity listener should trigger "Get Ready Alert"

        private boolean mTrigger = false;
        //Defines whether the Proximity Listener has been triggered (true) or not (false)

        private Location secondToLastCoords = null;
        //Tests distance from registered location w/ ProximityListener manually

        private Location lastCoords = null; //Coordinates of the final bus stop of the link

        private Location firstCoords = null; //Coordinates of the first bus stop of the link

        private float mDistance = -1;  //Actual known traveled distance loaded from link object

        private float directDistance = -1;
        //Direct distance to second to last stop coords, used for radius detection

        private float endDistance = -1;
        //Direct distance to last bus stop coords, used for link navigation

        private boolean mReady = false; //Has get ready alert been played?

        private boolean m100_a, m50_a, m20_a, m20_d, m50_d, m100_d = false;
        // Variables for handling arrival/departure from 2nd to last stop

        ProximityCalculator(NavigationServiceProvider navProvider) {
            mNavProvider = navProvider;
            Log.d(TAG, "Initializing ProximityCalculator");
        }

        /**
         * Getter method for radius value of ProximityListener
         **/
        public float getRadius() {
            return mRadius;
        }

        /**
         * Setter method for radius value of ProximityListener
         **/
        public void setRadius(float radius) {
            mRadius = radius;
        }

        /**
         * ProximityListener Functions
         **/
        public void monitoringStateChanged(boolean value) {
            //Fired when the monitoring of the ProximityListener state changes (is or is NOT active)
            Log.d(TAG, "Fired ProximityListener.monitoringStateChanged()...");
        }

        /**
         * Resets triggers for proximityEvent
         */
        public void setTrigger(boolean t) {
            mTrigger = mReady = t;
        }

        /**
         * Updates the state of the navigation provider based on the provided eventType
         *
         * @param eventType  EVENT_TYPE_PULL_CORD for "Pull the cord now" event, or EVENT_TYPE_GET_READY for getting ready to exit the vehicle
         * @param alertState ALERT_STATE_* variable is responsible for differentiating the switch of path link and alert being played.
         */
        boolean proximityEvent(int eventType, int alertState) {
            //*******************************************************************************************************************
            //* This function is fired by the ProximityListener when it detects that it is near a set of registered coordinates *
            //*******************************************************************************************************************
            // Log.d(TAG,"Fired proximityEvent() from ProximityListener object.");

            if (eventType == EVENT_TYPE_PULL_CORD) {
                if (!mTrigger) {
                    mTrigger = true;
                    Log.d(TAG, "Proximity Event fired");
                    if (mNavProvider.hasMorePathLinks()) {
                        if (alertState == ALERT_STATE_SHOWN_TO_RIDER) {
                            Log.d(TAG, "Alert 1 Screen showed to rider");
                            mWaitingForConfirm = true;
                            // GET READY.
                            // Log.d(TAG, "Calling way point reached!");
                            //this.mNavProvider.navlistener.waypointReached(this.lastCoords);
                            return true;
                        }
                        if (alertState == ALERT_STATE_ENDING_PATH_LINK) {
                            Log.d(TAG, "About to switch path links - from Proximity Event");
                            mNavProvider.navigateNextPathLink();

                            // Reset notification alerts
                            mReady = false;
                            mTrigger = false;
                        }
                    } else {
                        Log.d(TAG, "Got to last stop");
                        if (alertState == ALERT_STATE_SHOWN_TO_RIDER) {
                            Log.d(TAG, "Alert 1 screen before last stop");
                            mWaitingForConfirm = true;
                            Log.d(TAG, "Calling destination reached...");
                            return true;
                        }
                        if (alertState == ALERT_STATE_ENDING_PATH_LINK) {
                            Log.d(TAG, "Ending navigation");
                            mNavProvider.mPath = null;
                            mNavProvider.mPathLinkIndex = 0;
                        }
                    }
                }
            } else if (eventType == EVENT_TYPE_GET_READY) {
                if (!mReady) {
                    mReady = true;
                    return true;
                }
            }

            return false;
        }

        /**
         * Test function used to register a location to detect proximity to
         */
        void listenForLocation(Location first, Location secondToLast, Location last) {
            firstCoords = first;
            secondToLastCoords = secondToLast;
            lastCoords = last;

            // Reset distance if the manual listener is reset
            if (secondToLast == null) {
                directDistance = -1;
            }
            if (last == null) {
                endDistance = -1;
            }
        }

        /**
         * Sets the "known" distance for the path link
         */
        void listenForDistance(float d) {
            mDistance = d;
        }

        /**
         * Fire proximity event to switch path link or go back to service menu
         * when the final stop of the path link or path is reached
         * stop_type = 0; -> final stop detection
         * stop_type = 1; -> second to last stop detection
         * speed = current speed of the bus;
         */
        boolean StopDetector(float distance_d, int stop_type, float speed) {
            
            /* TODO: This comment was comented to avoid path link switching when the rider is 20 meters away from the bus stop
            if ((distance_d < 20) && (distance_d != -1) && stop_type == 0) {
                Log.d(TAG,"About to fire Proximity Event from Last Stop Detected");
                this.trigger = false;
                this.proximityEvent(0, 1);
                return true;

            } else */
            float lastToSecDistance = lastCoords.distanceTo(secondToLastCoords);
            Log.d(TAG, "Detecting stop. distance_d=" +
                    distance_d + ". stop_type=" + stop_type + " speed=" + speed);
            if (stop_type == 1) {
                /* Check if the bus is on the second to last stop */
                if ((distance_d > 50) && (distance_d < 100) && (distance_d != -1) && !m100_a) {
                    m100_a = true;
                    Log.d(TAG, "Case 1: false");
                    return false;
                }
                if ((distance_d > 20) && (distance_d < 50) && (distance_d != -1) && !m50_a) {
                    m50_a = true;
                    Log.d(TAG, "Case 2: false");
                    return false;
                }
                if ((distance_d < 20) && (distance_d != -1) && !m20_a) {
                    m20_a = true;
                    if (speed > 15 && lastToSecDistance < 100) {
                        Log.d(TAG, "Case 3: true");
                        return true;
                    }
                    Log.d(TAG, "Case 3: false");
                    return false;
                }
                if ((distance_d < 20) && (distance_d != -1) && m20_a && !m20_d) {
                    m20_d = true;
                    if (speed < 10) {
                        Log.d(TAG, "Case 4: false");
                        return false;
                    } else if (speed > 15) {
                        Log.d(TAG, "Case 4: true");
                        return true;
                    }
                }
                if ((distance_d > 20) && (distance_d < 50) && (distance_d != -1) && !m50_d && (m20_d
                        || m20_a)) {
                    m50_d = true;
                    Log.d(TAG, "Case 5: true");
                    return true;
                }
            }
            return false;
        }

        /**
         * Checks the proximity to the provided location and returns the event type (EVENT_TYPE_*) that was triggered by the proximity to this location.
         *
         * @return the status update (EVENT_TYPE_*) that was triggered by the proximity to this location (if any).
         */
        private int checkProximityAll(Location currentLocation) {
            if (!mWaitingForConfirm) {
                //re-calculate the distance to the final bus stop from the current location
                endDistance = lastCoords.distanceTo(currentLocation);
                //re-calculate the distance to second to last bus stop from the current location
                directDistance = secondToLastCoords.distanceTo(currentLocation);

                mSectoCurDistance = directDistance;

                Log.d(TAG, "Second to last stop coordinates: " + secondToLastCoords.getLatitude()
                        + ", " + secondToLastCoords
                        .getLongitude());

                mNavProvider.updateUi(EVENT_TYPE_UPDATE_DISTANCE);                 // Update distance notification

                // Check if distance from 2nd-to-last stop is less than threshold.
                if (directDistance < DISTANCE_THRESHOLD) {
                    if (proximityEvent(EVENT_TYPE_GET_READY, ALERT_STATE_NONE)) {
                        mNavProvider.updateUi(EVENT_TYPE_GET_READY);
                        Log.d(TAG, "-----Get ready!");
                        ObaAnalytics.reportUiEvent(mFirebaseAnalytics, Application.get().getString(R.string.analytics_label_destination_reminder), Application.get().getString(R.string.analytics_label_destination_reminder_variant_get_ready));
                        return EVENT_TYPE_GET_READY; //Get ready alert played
                    }
                }

                // Check if pull the cord notification should be fired.
                if (StopDetector(directDistance, 1, currentLocation.getSpeed())) {
                    if (proximityEvent(EVENT_TYPE_PULL_CORD, ALERT_STATE_SHOWN_TO_RIDER)) {
                        mNavProvider.updateUi(EVENT_TYPE_PULL_CORD);
                        Log.d(TAG, "-----Get off the bus!");
                        ObaAnalytics.reportUiEvent(mFirebaseAnalytics, Application.get().getString(R.string.analytics_label_destination_reminder), Application.get().getString(R.string.analytics_label_destination_reminder_variant_exit_at_next_stop));
                        return EVENT_TYPE_PULL_CORD; // Get off bus alert played
                    }
                }
            }
            return EVENT_TYPE_NO_EVENT; //No alerts played.
        }


        public void resetVariablesAfterPathLinkSwitching() {
            Log.d(TAG, "Reseting variables after path link switching!");
            m100_a = false;
            m50_a = false;
            m20_a = false;
            m20_d = false;
            m50_d = false;
            m100_d = false;
        }

        public void setOnlyTrigger(boolean value) {
            mTrigger = value;
        }

        public void setReady(boolean ready) {
            mReady = ready;
        }
    }

    public void setWaitingForConfirm(boolean waitingForConfirm) {
        mWaitingForConfirm = waitingForConfirm;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTTS.setLanguage(Locale.getDefault());
            mTTS.setSpeechRate(0.75f);
        }
    }

    /**
     * Gets the notification to be used for starting a hosting service for the NavigationServiceProvider in the foreground
     *
     * @return the notification to be used for starting a hosting service for the NavigationServiceProvider in the foreground
     */
    public Notification getForegroundStartingNotification() {
        return updateUi(EVENT_TYPE_INITIAL_STARTUP);
    }

    /**
     * Updates the user interface (e.g., distance display, speech) based on navigation events
     * TODO - This method should be moved to a NavigationServiceListener class based on a listener interface
     *
     * @param eventType EVENT_TYPE_* variable defining the eventType update to act upon
     * @return the notification to use for the foreground service if eventType == EVENT_TYPE_INITIAL_STARTUP, otherwise returns null
     */
    private Notification updateUi(int eventType) {
        Application app = Application.get();
        TripDetailsActivity.Builder bldr = new TripDetailsActivity.Builder(
                app.getApplicationContext(), mTripId);

        bldr = bldr.setDestinationId(mStopId);
        Intent intent = bldr.getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pIntent = PendingIntent.getActivity(app.getApplicationContext(), 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Create deletion intent to stop repeated voice comands.
        Intent receiverIntent = new Intent(app.getApplicationContext(), NavigationReceiver.class);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(Application.get().getApplicationContext()
                        , Application.CHANNEL_DESTINATION_ALERT_ID)
                        .setSmallIcon(R.drawable.ic_content_flag)
                        .setContentTitle(Application.get().getResources()
                                .getString(R.string.destination_reminder_title))
                        .setContentIntent(pIntent);
        if (eventType == EVENT_TYPE_INITIAL_STARTUP) {
            // Build initial notification used to start the service in the foreground
            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM, NavigationReceiver.CANCEL_TRIP);
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID);
            PendingIntent pCancelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);
            mBuilder.addAction(R.drawable.ic_navigation_close,
                    app.getString(R.string.destination_reminder_cancel_trip), pCancelIntent);
            mBuilder.setOngoing(true);
            return mBuilder.build();
        } else if (eventType == EVENT_TYPE_UPDATE_DISTANCE) {
            // Retrieve preferred unit and calculate distance.
            String IMPERIAL = app.getString(R.string.preferences_preferred_units_option_imperial);
            String METRIC = app.getString(R.string.preferences_preferred_units_option_metric);
            String AUTOMATIC = app.getString(R.string.preferences_preferred_units_option_automatic);
            String preferredUnits = mSettings
                    .getString(app.getString(R.string.preference_key_preferred_units), AUTOMATIC);
            double distance = mProxCalculator.endDistance;
            double miles = distance * RegionUtils.METERS_TO_MILES;  // Get miles.
            distance /= 1000;                                       // Get kilometers.
            DecimalFormat fmt = new DecimalFormat("0.0");

            Locale mLocale = Locale.getDefault();

            if (preferredUnits.equalsIgnoreCase(AUTOMATIC)) {
                Log.d(TAG, "Setting units automatically");
                // If the country is set to USA, assume imperial, otherwise metric
                // TODO - Method of guessing metric/imperial can definitely be improved
                if (mLocale.getISO3Country().equalsIgnoreCase(Locale.US.getISO3Country())) {
                    mBuilder.setContentText(Application.get().getResources().getQuantityString(R.plurals.distance_miles,
                            (int) miles,
                            fmt.format(miles)));
                } else {
                    mBuilder.setContentText(Application.get().getResources().getQuantityString(R.plurals.distance_kilometers,
                            (int) distance,
                            fmt.format(distance)));
                }
            } else if (preferredUnits.equalsIgnoreCase(IMPERIAL)) {
                mBuilder.setContentText(Application.get().getResources().getQuantityString(R.plurals.distance_miles,
                        (int) miles,
                        fmt.format(miles)));
            } else {
                mBuilder.setContentText(Application.get().getResources().getQuantityString(R.plurals.distance_kilometers,
                        (int) distance,
                        fmt.format(distance)));
            }

            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM, NavigationReceiver.CANCEL_TRIP);
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID);
            PendingIntent pCancelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);

            mBuilder.addAction(R.drawable.ic_navigation_close,
                    app.getString(R.string.destination_reminder_cancel_trip), pCancelIntent);

            mBuilder.setOngoing(true);
            NotificationManager mNotificationManager = (NotificationManager)
                    Application.get().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        } else if (eventType == EVENT_TYPE_GET_READY) {   // Get ready to pack
            mGetReady = true;
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID + 1);
            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM,
                    NavigationReceiver.DISMISS_NOTIFICATION);
            PendingIntent pDelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);

            String message = Application.get().getString(R.string.destination_voice_get_ready);
            for (int i = 0; i < NUM_GET_READY_REPEAT; i++) {
                speak(message, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
                if (i < NUM_GET_READY_REPEAT - 1) {
                    silence(500, TextToSpeech.QUEUE_ADD);
                }
            }

            mBuilder.setContentText(message);
            mBuilder.setVibrate(VIBRATION_PATTERN);
            mBuilder.setDeleteIntent(pDelIntent);

            NotificationManager mNotificationManager =
                    (NotificationManager) Application.get()
                            .getSystemService(Context.NOTIFICATION_SERVICE);

            mNotificationManager.notify(NOTIFICATION_ID + 1, mBuilder.build());

        } else if (eventType == EVENT_TYPE_PULL_CORD) {   // Pull the cord
            mFinished = true;
            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM,
                    NavigationReceiver.DISMISS_NOTIFICATION);
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID + 2);
            PendingIntent pDelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);

            String message = Application.get().getString(R.string.destination_voice_request_stop);
            // TODO: Slow down voice commands, add count as property.
            for (int i = 0; i < NUM_PULL_CORD_REPEAT; i++) {
                speak(message, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
                if (i < NUM_PULL_CORD_REPEAT - 1) {
                    silence(500, TextToSpeech.QUEUE_ADD);
                }
            }
            mBuilder.setContentText(message);
            mBuilder.setVibrate(VIBRATION_PATTERN);
            mBuilder.setDeleteIntent(pDelIntent);

            NotificationManager mNotificationManager =
                    (NotificationManager) Application.get()
                            .getSystemService(Context.NOTIFICATION_SERVICE);

            mNotificationManager.cancel(NOTIFICATION_ID + 1);
            mNotificationManager.notify(NOTIFICATION_ID + 2, mBuilder.build());

            mBuilder = new NotificationCompat.Builder(Application.get().getApplicationContext()
                    , Application.CHANNEL_DESTINATION_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_content_flag)
                    .setContentTitle(
                            Application.get().getResources().getString(R.string.destination_reminder_title))
                    .setContentIntent(pIntent)
                    .setAutoCancel(true);
            message = Application.get().getString(R.string.destination_voice_arriving_destination);
            mBuilder.setContentText(message);
            mBuilder.setOngoing(false);
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
        // If we reach this point then the event_type isn't initial startup
        return null;
    }

    /**
     * Speak specified message out loud using TTS
     *
     * @param message   Message to be spoken.
     * @param queueFlag Flag to use when adding message to queue.
     */
    private void speak(String message, int queueFlag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTTS.speak(message, queueFlag, null, "TRIPMESSAGE");
        } else {
            mTTS.speak(message, queueFlag, null);
        }
    }

    /**
     * Play silence for specified duration.
     *
     * @param duration  Time in ms to play silence.
     * @param queueFlag Flag to use when adding to the queue.
     */
    private void silence(long duration, int queueFlag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTTS.playSilentUtterance(duration, queueFlag, "TRIPSILENCE");
        } else {
            mTTS.playSilence(duration, queueFlag, null);
        }
    }
}
