package org.onebusaway.android.tad;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.RegionUtils;

import java.text.DecimalFormat;

/**
 * Created by azizmb on 2/18/16.
 */
public class TADService extends Service
    implements LocationHelper.Listener
{
    public static final String TAG = "TADService";

    public static final String DESTINATION_ID = ".DestinationId";
    public static final String BEFORE_STOP_ID = ".BeforeId";
    public static final String TRIP_ID = ".TripId";

    private LocationHelper mLocationHelper = null;
    private Location mLastLocation = null;

    private Location mDestLocation = null;          // Destination stop location
    private Location mBeforeLocation = null;        // Second to last stop location
    private String mStopId;                         // Destination Stop ID
    private String mTripId;                         // Trip ID

    private TADNavigationServiceProvider mNavProvider;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mDestLocation = new Location(LocationManager.GPS_PROVIDER);
            mDestLocation.setLatitude(intent.getDoubleExtra("STOP_LAT", 0));
            mDestLocation.setLongitude(intent.getDoubleExtra("STOP_LNG", 0));
            mBeforeLocation = new Location(LocationManager.GPS_PROVIDER);
            mBeforeLocation.setLatitude(intent.getDoubleExtra("BEFORE_LAT", 0));
            mBeforeLocation.setLongitude(intent.getDoubleExtra("BEFORE_LNG", 0));
            mStopId = intent.getStringExtra("STOP_ID");
            mTripId = intent.getStringExtra("TRIP_ID");
        } else {
            // Load from disk
        }

        mLocationHelper = new LocationHelper(this, 1);

        if (mLocationHelper != null) {
            Log.i(TAG, "Requesting Location Updates");
            mLocationHelper.registerListener(this);
        }

        mNavProvider = new TADNavigationServiceProvider(mTripId, mStopId);
        Segment segment = new Segment(this.mBeforeLocation, this.mDestLocation, null);
        mNavProvider.navigate(new org.onebusaway.android.tad.Service(), new Segment[] { segment });
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
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location Updated");
        mLastLocation = location;
        if (mLastLocation != null) {
            mNavProvider.locationUpdated(mLastLocation);
        }

        // Trip is done? End service.
        if (mNavProvider.getFinished()) {
            this.stopSelf();
        }
    }

    // Saves location details for TAD to disk.
    private void saveTrip()
    {

    }

    // Loads trip details for TAD to disk.
    private void loadTrip()
    {

    }

}
