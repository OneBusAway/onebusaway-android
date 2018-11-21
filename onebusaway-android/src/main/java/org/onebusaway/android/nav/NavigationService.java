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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.nav.model.Path;
import org.onebusaway.android.nav.model.PathLink;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.TripDetailsListFragment;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PreferenceUtils;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The NavigationService is started when the user begins a trip, this service listens for location
 * updates and passes the locations to its instance of NavigationServiceProvider each time.
 * NavigationServiceProvider is responsible for computing the statuses of the trips and issuing
 * notifications/TTS messages. Once the NavigationServiceProvider is completed, the
 * NavigationService will stop itself.
 */
public class NavigationService extends Service implements LocationHelper.Listener {
    public static final String TAG = "NavigationService";

    public static final String DESTINATION_ID = ".DestinationId";
    public static final String BEFORE_STOP_ID = ".BeforeId";
    public static final String TRIP_ID = ".TripId";

    public static final String LOG_DIRECTORY = "ObaNavLog";

    private static final int RECORDING_THRESHOLD = NavigationServiceProvider.DISTANCE_THRESHOLD + 100;

    private LocationHelper mLocationHelper = null;
    private Location mLastLocation = null;

    private String mDestinationStopId;              // Destination Stop ID
    private String mBeforeStopId;                   // Before Destination Stop ID
    private String mTripId;                         // Trip ID

    private int mCoordId =0;

    private boolean mGetReadyFlag = false;
    private boolean mPullTheCordFlag = false;

    private NavigationServiceProvider mNavProvider;
    private File mLogFile = null;

    private long mFinishedTime;

    private FirebaseAuth mAuth;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting Service");
        long currentTime = System.currentTimeMillis();
        if (intent != null) {
            mDestinationStopId = intent.getStringExtra(DESTINATION_ID);
            mBeforeStopId = intent.getStringExtra(BEFORE_STOP_ID);
            mTripId = intent.getStringExtra(TRIP_ID);

            ObaContract.NavStops.insert(Application.get().getApplicationContext(), currentTime,
                    1, 1, mTripId, mDestinationStopId, mBeforeStopId);


            mNavProvider = new NavigationServiceProvider(mTripId, mDestinationStopId);
        } else {
            String[] args = ObaContract.NavStops.getDetails(Application.get().getApplicationContext(), "1");
            if (args != null && args.length == 3) {
                mTripId = args[0];
                mDestinationStopId = args[1];
                mBeforeStopId = args[2];
                mNavProvider = new NavigationServiceProvider(mTripId, mDestinationStopId, 1);

            }
        }

        // Log in anonymously via Firebase
        initAnonFirebaseLogin();

        // Setup file for logging.
        if (mLogFile == null && BuildConfig.NAV_GPS_LOGGING) {
            setupLog();
        }

        if (mLocationHelper == null) {
            mLocationHelper = new LocationHelper(this, 1);
        }

        Log.d(TAG, "Requesting Location Updates");
        mLocationHelper.registerListener(this);

        Location dest = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mDestinationStopId);
        Location last = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mBeforeStopId);
        PathLink pathLink = new PathLink(currentTime, null, last, dest, mTripId);

        if (mNavProvider != null) {
            // TODO Support more than one path link
            ArrayList<PathLink> links = new ArrayList<>(1);
            links.add(pathLink);
            Path path = new Path(links);
            mNavProvider.navigate(path);
        }
        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onRebind(Intent intent) {

    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying Service.");
        mLocationHelper.unregisterListener(this);
        super.onDestroy();

        // Send Broadcast
        sendBroadcast();
    }

    /**
     * Sends broadcast so that flag of destination alert is removed from trip detail screen
     */
    private void sendBroadcast() {
        Intent intent = new Intent(TripDetailsListFragment.ACTION_SERVICE_DESTROYED);
        sendBroadcast(intent);
    }

    @Override
    public synchronized void onLocationChanged(Location location) {
        Log.d(TAG, "Location Updated");
        if (mLastLocation == null) {
            mNavProvider.locationUpdated(location);
        } else if (!LocationUtils.isDuplicate(mLastLocation, location)) {
            mNavProvider.locationUpdated(location);
        }

        if (BuildConfig.NAV_GPS_LOGGING && mNavProvider.mSectoCurDistance <= RECORDING_THRESHOLD) {
            writeToLog(location);
        }
        mLastLocation = location;

        // Is trip is finished? If so end service.
        if (mNavProvider.getFinished()) {
            if (BuildConfig.NAV_GPS_LOGGING) {
                if (mFinishedTime == 0) {
                    mFinishedTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - mFinishedTime >= 30000) {
                    stopSelf();
                }
            } else {
                stopSelf();
            }
        }
    }

    private void initAnonFirebaseLogin() {
        mAuth = FirebaseAuth.getInstance();
        int numCores = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(numCores * 2, numCores *2,
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        mAuth.signInAnonymously()
                .addOnCompleteListener(executor, task -> {
                    if (task.isSuccessful()) {
                        // Sign in success
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "signInAnonymously:success");
                    } else {
                        // Sign in failed
                        Log.w(TAG, "signInAnonymously:failure", task.getException());
                    }
                });
    }

    /**
     * Creates the log file that GPS data and navigation performance is written to - see DESTINATION_ALERTS.md
     */
    private void setupLog() {
        try {
            // Get the counter that's incremented for each test
            final String NAV_TEST_ID = getString(R.string.preference_key_nav_test_id);
            int counter = Application.getPrefs().getInt(NAV_TEST_ID, 0);
            counter++;
            PreferenceUtils.saveInt(NAV_TEST_ID, counter);

            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d yyyy, hh:mm aaa");
            String readableDate = sdf.format(Calendar.getInstance().getTime());

            mLogFile = new File(Environment.getExternalStoragePublicDirectory(LOG_DIRECTORY),
                    counter + "-" + readableDate + ".csv");
            Location dest = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mDestinationStopId);
            Location last = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mBeforeStopId);

            String header = String.format(Locale.US, "%s,%s,%f,%f,%s,%f,%f\n", mTripId, mDestinationStopId,
                    dest.getLatitude(), dest.getLongitude(), mBeforeStopId, last.getLatitude(), last.getLongitude());

            if (mLogFile != null) {
                FileUtils.write(mLogFile, header, false);
            } else {
                Log.e(TAG, "Failed to write to file - null file");
            }
            Toast toast = Toast.makeText(getApplicationContext(),
                    getString(R.string.stop_notify_test_id, counter), Toast.LENGTH_SHORT);
            toast.show();
        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    private void writeToLog(Location l) {
        try {
            String nanoTime = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                nanoTime = Long.toString(l.getElapsedRealtimeNanos());
            }

            int satellites = 0;
            if (l.getExtras() != null) {
                satellites = l.getExtras().getInt("satellites", 0);
            }

           // mGetReadyFlag =mNavProvider.getGetReady();
          //  mPullTheCordFlag = mNavProvider.getFinished();

            // TODO: Add isMockProvider
            String log = String.format(Locale.US,"%d,%s,%s,%s,%d,%f,%f,%f,%f,%f,%f,%d,%s\n",
                    mCoordId, mNavProvider.getGetReady(),mNavProvider.getFinished(), nanoTime, l.getTime(),
                    l.getLatitude(), l.getLongitude(), l.getAltitude(), l.getSpeed(),
                    l.getBearing(), l.getAccuracy(), satellites, l.getProvider());


            //Increments the id for each coordinate
            mCoordId++;

            if (mLogFile != null && mLogFile.canWrite()) {
                FileUtils.write(mLogFile, log, true);
            } else {
                Log.e(TAG, "Failed to write to file");
            }

        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }
}
