/*
 * Copyright (C) 2005-2018 University of South Florida
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

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.ui.TripDetailsActivity;
import org.onebusaway.android.util.RegionUtils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * This class provides the navigation functionality for the destination reminders
 *
 * @author Barbeau / Belov
 */
public class NavigationServiceProvider implements TextToSpeech.OnInitListener {

    public static final String TAG = "NavServiceProvider";

    public static final int NOTIFICATION_ID = 33620;
    private static final long[] VIBRATION_PATTERN = new long[]{
            2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000
    };
    private static final int DISTANCE_THRESHOLD = 200;

    // Number of times to repeat voice commands.
    private static final int PULL_CORD_REPEAT = 10;
    private static final int GET_READY_REPEAT = 2;

    private ProximityCalculator mProxCalculator;

    private int mTimeout = 60;  //Timeout value for service provider action (default = 60 seconds);

    /**
     * Navigation-specific variables
     **/
    private int mSegmentIndex = 0;
            //Index that defines the current segment within the ordered context of a service (i.e. First segment in a service will have index = 0, second segment index = 1, etc.)

    private NavigationSegment[] mSegments;  //Array of segments that are currently being navigated

    private float[] mDistances;
            //Array of floats calculated from segments traveled, segment limit = 20.

    private float mAlertDistance = -1;

    private int mDiss = 0; //relation for segmentid/distances

    private boolean mWaitingForConfirm = false;

    private Location mCurrentLocation = null;

    private boolean mResuming = false;   // Is Trip being resumed?
    public boolean mFinished = false;   // Trip has finished.  //Change to public
    public boolean mGetReady = false;   // Get Ready triggered. //Change to public

    public static TextToSpeech mTTS;          // TextToSpeech for speaking commands.

    SharedPreferences mSettings = Application.getPrefs();  // Shared Prefs

    private String mTripId;             // Trip ID
    private String mStopId;             // Stop ID

    public NavigationServiceProvider(String tripId, String stopId) {
        Log.d(TAG, "Creating NavigationServiceProvider...");
        if (mTTS == null) {
            mTTS = new TextToSpeech(Application.get().getApplicationContext(), this);
        } else {
            String message = Application.get().getString(R.string.voice_starting_trip);
            speak(message, TextToSpeech.QUEUE_FLUSH);
        }
        mTripId = tripId;
        mStopId = stopId;
    }

    public NavigationServiceProvider(String tripId, String stopId, int flag) {
        Log.d(TAG, "Creating NavigationServiceProvider...");
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
     * Adds dsegment distance to the array storing all distances
     */
    public void addDistance(float d) {
        mDistances[mDiss] = d;
        mDiss++;
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
     * Returns all stored distances for currently navigated service
     */
    public float[] getDistances() {
        return mDistances;
    }


    /**
     * Returns the ID of the currently navigated segment
     */
    public int getSegmentID() {
        try {
            if (mSegments != null) {
                return mSegments[mSegmentIndex].getSegmentId();
            } else {
                return -1;  //If a segment isn't currently being navigated, then return -1 as a default value

            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Could not get Segment ID");
            return -1;
        }
    }

    /**
     * Returns the index of the current segment
     */
    public int getSegmentIndex() {
        return mSegmentIndex;
    }


    /**
     * This method sets up the NavigationProvider to provide navigations instructions to a particular location
     */
    public void navigate(Location start, Location destination) {
        mProxCalculator.listenForCoords(destination, null,
                null);  //Set proximity listener to listen for coords
    }

    /**
     * Navigates a transit Service which is composed of these Segments
     */
    public void navigate(NavigationSegment[] segments) {

        Log.d(TAG, "Starting navigation for service");
        //Create a new instance and rewrite the old one with a blank slate of ProximityListener
        lazyProxInitialization();
        mSegments = segments;
        mSegmentIndex = 0;
        mDiss = 0;
        mDistances = new float[segments.length];
        Log.d(TAG, "Segments Length: " + segments.length);
        //Create new coordinate object using the "Ring" coordinates
        Location coords = mSegments[mSegmentIndex].getBeforeLocation();
        Location lastcoords = mSegments[mSegmentIndex].getToLocation();
        Location firstcoords = mSegments[mSegmentIndex].getFromLocation();

        mAlertDistance = mSegments[mSegmentIndex].getAlertDistance();
        //Have proximity listener listen for the "Ring" location
        mProxCalculator.listenForDistance(mAlertDistance);
        mProxCalculator.listenForCoords(coords, lastcoords, firstcoords);
        mProxCalculator.ready = false;
        mProxCalculator.trigger = false;
    }

    /**
     * Resets any current routes which might be currently navigated
     */
    public void reset() {
        mProxCalculator.listenForCoords(null, null, null);
    }

    public void setTimeout(int timeout) {
        this.mTimeout = timeout;
    }

    public int getTimeout() {
        return this.mTimeout;
    }

    /**
     * Sets the radius of detection for the ProximityListener
     */
    public void setRadius(float radius) {
        mProxCalculator.setRadius(radius);
    }

    /**
     * Determines whether or not there is another segment to be navigated as part of the current transit service that is being navigated
     */
    public boolean hasMoreSegments() {
        Log.d(TAG, "Checking if service has more segments left");
        //If there are still more segments to be navigated as part of this transit service, return true.  Otherwise return false
        if ((mSegments == null) || (mSegmentIndex >= (mSegments.length - 1))) {
            Log.d(TAG, "Segments Index: " + mSegmentIndex + " Segments Length: " + (mSegments.length
                    - 1));
            Log.d(TAG, "%%%%%%%%%%%%%%% No more Segments Left %%%%%%%%%%%%%%%%%%%%%");

            return false; //No more segments exist

        }
        Log.d(TAG, "More segments left, returning true");
        return true; //Additional segments still need to be navigated as part of this service

    }

    /**
     * Tells the NavigationProvider to navigate the next segment in the queue
     */
    private void navigateNextSegment() {
        Log.d(TAG, "Attempting to navigate next segment");
        if ((mSegments != null) && (mSegmentIndex < (mSegments.length))) {
            //Increment segment index
            Log.d(TAG, "Setting previous segment to null!");
            mSegments[mSegmentIndex]
                    = null; // - Set unused object to null to enable it for garbage collection.
            Log.d(TAG, "getting coords");
            mSegmentIndex++;
            //Create new coordinate object using the "Ring" coordinates
            NavigationSegment segment = mSegments[mSegmentIndex];
            mAlertDistance = segment.getAlertDistance();
            //Have proximity listener listen for the "Ring" location
            mProxCalculator.listenForDistance(mAlertDistance);
            mProxCalculator
                    .listenForCoords(segment.getBeforeLocation(), segment.getToLocation(),
                            segment.getFromLocation());
            Log.d(TAG, "Proximlistener parameters were set!");
        }
    }

    /**
     * Is called from LocationListener.locationUpdated() in inorder to supply the Navigation Provider with the most recent location
     */
    public void locationUpdated(Location l) {
        mCurrentLocation = l;
        mProxCalculator.checkProximityAll(mCurrentLocation);
    }

    private int sendCounter = 0;

    public void skipSegment() {
        try {
            if (hasMoreSegments()) {
                Log.d(TAG, "About to switch segment - from skipSegment");
                navigateNextSegment(); //Uncomment this line to allow navigation on multiple segments within one service (chained segments)
                mProxCalculator.setReady(false); //Reset the "get ready" notification alert
            } else {
                Log.d(TAG, "No more segments!");
            }
            mProxCalculator.setTrigger(false); //Reset the proximity notification alert
        } catch (Exception e) {
            Log.e(TAG, "Error in ProximityCalculator.proximityEvent(): " + e);
            e.printStackTrace();
        }
    }

    /**
     * This class is used to detect Proximity to a latitude and longitude location.  The JSR179 ProximityListener is not used for this implementation on iDEN phones
     * because it is not currently reliable.
     * This class was moved to an inner class of NavigationServiceProvider 2-5-2007 because it must call methods in the NavigationServiceProvider
     * that should remain private, and also because its the only proper way to get the NavigationServiceProvider and Listener to work together properly.
     *
     * @author Sean J. Barbeau, modified by Belov
     */
    public class ProximityCalculator {

        NavigationServiceProvider mNavProvider;

        private float radius = 100;
                //Defines radius (in meters) for which the Proximity listener should be triggered (Default = 50)

        private float readyRadius = 300;
                //Defines radius(in meters) for which the Proximity listener should trigger "Get Ready Alert"

        private boolean trigger = false;
                //Defines whether the Proximity Listener has been triggered (true) or not (false)

        private Location secondToLastCoords = null;
                //Tests distance from registered location w/ ProximityListener manually

        private Location lastCoords = null; //Coordinates of the final bus stop of the segment

        private Location firstCoords = null; //Coordinates of the first bus stop of the segment

        private float distance = -1;  //Actual known traveled distance loaded from segment object

        private float directDistance = -1;
                //Direct distance to second to last stop coords, used for radius detection

        private float endDistance = -1;
                //Direct distance to last bus stop coords, used for segment navigation

        private boolean ready = false; //Has get ready alert been played?

        private boolean m100_a, m50_a, m20_a, m20_d, m50_d, m100_d = false;
                // Variables for handling arrival/departure from 2nd to last stop

        public ProximityCalculator(NavigationServiceProvider navProvider) {
            this.mNavProvider = navProvider;
            Log.d(TAG, "Initializing ProximityCalculator");
        }

        /**
         * Getter method for radius value of ProximityListener
         **/
        public float getRadius() {
            return radius;
        }

        /**
         * Setter method for radius value of ProximityListener
         **/
        public void setRadius(float radius) {
            this.radius = radius;
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
            trigger = ready = t;
        }

        /**
         * Fires proximity events based on selection parameters
         *
         * @param selection - checks if the trigger or get ready notifications are called
         * @param t         - variable is responsible for differentiating the switch of segment and alert being played.
         */
        public boolean proximityEvent(int selection, int t) {
            //*******************************************************************************************************************
            //* This function is fired by the ProximityListener when it detects that it is near a set of registered coordinates *
            //*******************************************************************************************************************
            // Log.d(TAG,"Fired proximityEvent() from ProximityListener object.");

            //NEW - if statement that encompases rest of method to check if mNavProvider has triggered navListener before for this coordinate
            if (selection == 0) {
                if (trigger == false) {
                    trigger = true;
                    Log.d(TAG, "Proximity Event fired");
                    if (mNavProvider.hasMoreSegments()) {
                        if (t == 0) {
                            Log.d(TAG, "Alert 1 Screen showed to rider");
                            mWaitingForConfirm = true;
                            // GET READY.
                            Log.d(TAG, "Calling way point reached!");
                            //this.mNavProvider.navlistener.waypointReached(this.lastCoords);
                            return true;
                        }
                        if (t == 1) {
                            try {
                                Log.d(TAG, "About to switch segment - from Proximity Event");
                                mNavProvider
                                        .navigateNextSegment(); //Uncomment this line to allow navigation on multiple segments within one service (chained segments)

                                ready = false; //Reset the "get ready" notification alert

                                trigger = false; //Reset the proximity notification alert

                            } catch (Exception e) {
                                Log.e(TAG, "Error in ProximityCalculator.proximityEvent(): " + e);
                            }
                        }
                    } else {
                        Log.d(TAG, "Got to last stop ");
                        if (t == 0) {
                            Log.d(TAG, "Alert 1 screen before last stop");
                            mWaitingForConfirm = true;
                            Log.d(TAG, "Calling destination reached...");
                            return true;
                        }
                        if (t == 1) {
                            long time = System.currentTimeMillis();
                            Log.d(TAG, "Ending trip, going back to services");
                            mNavProvider.mSegments = null;
                            mNavProvider.mSegmentIndex = 0;
                        }
                    }
                }
            } else if (selection == 1) {
                if (ready == false) {
                    ready = true;
                    return true;
                }
            }

            return false;
        }

        /**
         * Test function used to register a location to detect proximity to
         */
        public void listenForCoords(Location coords, Location last, Location first) {
            secondToLastCoords = coords;
            lastCoords = last;
            firstCoords = first;
            //Reset distance if the manual listener is reset
            if (coords == null) {
                directDistance = -1;
            }
            if (last == null) {
                endDistance = -1;
            }

        }

        /**
         * Sets the "known" distance for the segment
         */
        public void listenForDistance(float d) {
            this.distance = d;
        }

        /**
         * Fire proximity event to switch segment or go back to service menu
         * when the final stop of the segment or service is reached
         * stop_type = 0; -> final stop detection
         * stop_type = 1; -> second to last stop detection
         * speed = current speed of the bus;
         */
        public boolean StopDetector(float distance_d, int stop_type, float speed) {
            
            /* TODO: This comment was comented to avoid segment switching when the rider is
             * 20 meters away from the bus stop.
            if ((distance_d < 20) && (distance_d != -1) && stop_type == 0) {

                Log.d(TAG,"About to fire Proximity Event from Last Stop Detected");
                this.trigger = false;
                this.proximityEvent(0, 1);
                return true;

            } else */
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
                    if (speed > 15) {
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
         * These method checks the proximity to the registered coordinates
         */
        private int checkProximityAll(Location currentLocation) {
            if (!mWaitingForConfirm) {
                //re-calculate the distance to the final bus stop from the current location
                endDistance = lastCoords.distanceTo(currentLocation);
                //re-calculate the distance to second to last bus stop from the current location
                directDistance = secondToLastCoords.distanceTo(currentLocation);
                Log.d(TAG, "Second to last stop coordinates: " + secondToLastCoords.getLatitude()
                        + ", " + secondToLastCoords
                        .getLongitude());

                mNavProvider.updateInterface(1);                 // Update distance notification

                // Check if distance from 2nd-to-last stop is less than threshold.
                if (directDistance < DISTANCE_THRESHOLD) {
                    if (proximityEvent(1, -1)) {
                        mNavProvider.updateInterface(2);
                        Log.d(TAG, "-----Get ready!");
                        return 2; //Get ready alert played
                    }
                }

                // Check if pull the cord notification should be fired.
                if (StopDetector(directDistance, 1, currentLocation.getSpeed())) {
                    if (proximityEvent(0, 0)) {
                        mNavProvider.updateInterface(3);
                        Log.d(TAG, "-----Get off the bus!");
                        return 1; // Get off bus alert played
                    }
                }
            }
            return 0; //No alerts played.
        }


        public void resetVariablesAfterSegmentSwitching() {
            Log.d(TAG, "Reseting variables after segment switching!");
            m100_a = false;
            m50_a = false;
            m20_a = false;
            m20_d = false;
            m50_d = false;
            m100_d = false;
        }

        public void setOnlyTrigger(boolean value) {
            this.trigger = value;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
        }
    }

    public void setWaitingForConfirm(boolean waitingForConfirm) {
        this.mWaitingForConfirm = waitingForConfirm;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTTS.setLanguage(Locale.getDefault());
            mTTS.setSpeechRate(0.75f);
            if (!mResuming) {
                speak(Application.get().getString(R.string.voice_starting_trip),
                        TextToSpeech.QUEUE_FLUSH);
            }
        }
    }

    // Update Interface
    // e.g, notifications, speak, etc.
    private void updateInterface(int status) {
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
                new NotificationCompat.Builder(Application.get().getApplicationContext())
                        .setSmallIcon(R.drawable.ic_content_flag)
                        .setContentTitle(Application.get().getResources()
                                .getString(R.string.stop_notify_title))
                        .setContentIntent(pIntent)
                        .setAutoCancel(true);
        if (status == 1) {          // General status update.
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
                    mBuilder.setContentText(fmt.format(miles) + " miles away.");
                } else {
                    mBuilder.setContentText(fmt.format(distance) + " kilometers away.");
                }
            } else if (preferredUnits.equalsIgnoreCase(IMPERIAL)) {
                mBuilder.setContentText(fmt.format(miles) + " miles away.");
            } else {
                mBuilder.setContentText(fmt.format(distance) + " kilometers away.");
            }

            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM, NavigationReceiver.CANCEL_TRIP);
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID);
            PendingIntent pCancelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);

            mBuilder.addAction(R.drawable.ic_action_cancel,
                    app.getString(R.string.stop_notify_cancel_trip), pCancelIntent);

            mBuilder.setOngoing(true);
            NotificationManager mNotificationManager = (NotificationManager)
                    Application.get().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());

        } else if (status == 2) {   // Get ready to pack
            mGetReady = true;
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID + 1);
            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM,
                    NavigationReceiver.DISMISS_NOTIFICATION);
            PendingIntent pDelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);

            String message = Application.get().getString(R.string.voice_get_ready);
            for (int i = 0; i < GET_READY_REPEAT; i++) {
                speak(message, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
                if (i < GET_READY_REPEAT - 1) {
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

        } else if (status == 3) {   // Pull the cord
            mFinished = true;
            receiverIntent.putExtra(NavigationReceiver.ACTION_NUM,
                    NavigationReceiver.DISMISS_NOTIFICATION);
            receiverIntent.putExtra(NavigationReceiver.NOTIFICATION_ID, NOTIFICATION_ID + 2);
            PendingIntent pDelIntent = PendingIntent.getBroadcast(app.getApplicationContext(),
                    0, receiverIntent, 0);

            String message = Application.get().getString(R.string.voice_pull_cord);
            // TODO: Slow down voice commands, add count as property.
            for (int i = 0; i < PULL_CORD_REPEAT; i++) {
                speak(message, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD);
                if (i < PULL_CORD_REPEAT - 1) {
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

            mBuilder = new NotificationCompat.Builder(Application.get().getApplicationContext())
                    .setSmallIcon(R.drawable.ic_content_flag)
                    .setContentTitle(
                            Application.get().getResources().getString(R.string.stop_notify_title))
                    .setContentIntent(pIntent)
                    .setAutoCancel(true);
            message = Application.get().getString(R.string.voice_arriving_destination);
            mBuilder.setContentText(message);
            mBuilder.setOngoing(false);
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
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
