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

    private String mDestinationStopId;              // Destination Stop ID
    private String mBeforeStopId;                   // Before Destination Stop ID
    private String mTripId;                         // Trip ID

    private TADNavigationServiceProvider mNavProvider;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting Service");
        if (intent != null) {
            mDestinationStopId = intent.getStringExtra(DESTINATION_ID);
            mBeforeStopId = intent.getStringExtra(BEFORE_STOP_ID);
            mTripId = intent.getStringExtra(TRIP_ID);

            ObaContract.NavStops.insert(Application.get().getApplicationContext(),
                    1, 1, mTripId, mDestinationStopId, mBeforeStopId);

            mNavProvider = new TADNavigationServiceProvider(mTripId, mDestinationStopId);
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
            mNavProvider.navigate(new org.onebusaway.android.tad.Service(), new Segment[] { segment });
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
        if (mLastLocation != null) {
            mNavProvider.locationUpdated(mLastLocation);
        }

        // Trip is done? End service.
        if (mNavProvider.getFinished()) {
            this.stopSelf();
        }
    }
}
