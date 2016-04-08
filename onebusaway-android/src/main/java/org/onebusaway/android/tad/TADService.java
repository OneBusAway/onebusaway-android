package org.onebusaway.android.tad;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.LocationHelper;

import java.io.File;
import java.io.IOException;

/**
 * Created by azizmb on 2/18/16.
 */
public class TADService extends Service
        implements LocationHelper.Listener {
    public static final String TAG = "TADService";

    public static final String DESTINATION_ID = ".DestinationId";
    public static final String BEFORE_STOP_ID = ".BeforeId";
    public static final String TRIP_ID = ".TripId";

    private LocationHelper mLocationHelper = null;
    private Location mLastLocation = null;

    private String mDestinationStopId;              // Destination Stop ID
    private String mBeforeStopId;                   // Before Destination Stop ID
    private String mTripId;                         // Trip ID

    private TADNavigationServiceProvider mNavProvider;
    private File mLogFile;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting Service");
        if (mLogFile == null && BuildConfig.TAD_GPS_LOGGING) {
            mLogFile = new File(Environment.getExternalStoragePublicDirectory("TADLog"),
                    mTripId + "_" + mDestinationStopId + ".txt");
        }
        if (intent != null) {

            mDestinationStopId = intent.getStringExtra(DESTINATION_ID);
            mBeforeStopId = intent.getStringExtra(BEFORE_STOP_ID);
            mTripId = intent.getStringExtra(TRIP_ID);

            ObaContract.NavStops.insert(Application.get().getApplicationContext(),
                    1, 1, mTripId, mDestinationStopId, mBeforeStopId);

            mNavProvider = new TADNavigationServiceProvider(mTripId, mDestinationStopId);
            if (BuildConfig.TAD_GPS_LOGGING) {
                setupLog();
            }
        } else {
            String[] args = ObaContract.NavStops.getDetails(Application.get().getApplicationContext(), "1");
            if (args != null && args.length == 3) {
                mTripId = args[0];
                mDestinationStopId = args[1];
                mBeforeStopId = args[2];
                mNavProvider = new TADNavigationServiceProvider(mTripId, mDestinationStopId, 1);
            }
        }

        mLocationHelper = new LocationHelper(this, 1);

        if (mLocationHelper != null) {
            Log.i(TAG, "Requesting Location Updates");
            mLocationHelper.registerListener(this);
        }

        Location dest = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mDestinationStopId);
        Location last = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mBeforeStopId);
        Segment segment = new Segment(last, dest, null);

        if (mNavProvider != null) {
            mNavProvider.navigate(new Segment[]{segment});
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
        Log.i(TAG, "Destroying Service.");
        mLocationHelper.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location Updated");
        mLastLocation = location;

        if (BuildConfig.TAD_GPS_LOGGING) {
            writeToLog(mLastLocation);
        }

        if (mLastLocation != null) {
            mNavProvider.locationUpdated(mLastLocation);
        }

        // Trip is done? End service.
        if (mNavProvider.getFinished()) {
            this.stopSelf();
        }
    }

    private void setupLog() {
        try {
            Location dest = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mDestinationStopId);
            Location last = ObaContract.Stops.getLocation(Application.get().getApplicationContext(), mBeforeStopId);
            String hdr = String.format("%s,%s,%f,%f,%s,%f,%f\n", mTripId, mDestinationStopId, dest.getLatitude(),
                    dest.getLongitude(), mBeforeStopId, last.getLatitude(), last.getLongitude());
            if (mLogFile != null && mLogFile.canWrite()) {
                FileUtils.write(mLogFile, hdr, false);
            } else {
                Log.e(TAG, "Failed to write to file");
            }

        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    private void writeToLog(Location l) {
        try {
            String log = l.getTime() + "," + l.getLatitude() + "," +
                    l.getLongitude() + "," + l.getSpeed() + ","
                    + l.getBearing() + ","
                    + l.getProvider() + "\n";

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
