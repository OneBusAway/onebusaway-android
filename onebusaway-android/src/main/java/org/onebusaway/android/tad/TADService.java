package org.onebusaway.android.tad;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.onebusaway.android.R;
import org.onebusaway.android.util.LocationHelper;
import org.onebusaway.android.util.RegionUtils;

import java.text.DecimalFormat;

/**
 * Created by azizmb on 2/18/16.
 */
public class TADService extends Service
    implements LocationHelper.Listener
{
    public static final String TAG = "TADService";

    private LocationHelper mLocationHelper = null;
    private Location mLastLocation = null;

    private String dName = null;
    private Location dLocation = null;      // Last stop
    private Location bLocation = null;      // Second to last stop

    private TADNavigationServiceProvider navProvider;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.dName = intent.getStringExtra("STOP_NAME");
        this.dLocation = new Location(LocationManager.GPS_PROVIDER);
        this.dLocation.setLatitude(intent.getDoubleExtra("STOP_LAT", 0));
        this.dLocation.setLongitude(intent.getDoubleExtra("STOP_LNG", 0));
        this.bLocation = new Location(LocationManager.GPS_PROVIDER);
        this.bLocation.setLatitude(intent.getDoubleExtra("BEFORE_LAT", 0));
        this.bLocation.setLongitude(intent.getDoubleExtra("BEFORE_LNG", 0));

        mLocationHelper = new LocationHelper(this);

        if (mLocationHelper != null) {
            Log.i(TAG, "Requesting Location Updates");
            mLocationHelper.registerListener(this);
        }
        this.navProvider = new TADNavigationServiceProvider();
        Segment segment = new Segment(this.bLocation, this.dLocation, null);
        this.navProvider.navigate(new org.onebusaway.android.tad.Service(), new Segment[] { segment });
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
            this.navProvider.locationUpdated(mLastLocation);
            updateNotification();
        }
    }

    // Updates on-going notification of trip with distance to stop.
    // If user's current location is unavailable, it falls back
    // to the stop name.
    private void updateNotification()
    {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.map_stop_icon)
                        .setContentTitle(getResources().getString(R.string.stop_notify_title))
                        .setContentText(dName);


        if (mLastLocation != null) {
            // Retrieve preferred unit and calculate distance.
            String unit = getString(R.string.preference_key_preferred_units);
            double distance = mLastLocation.distanceTo(dLocation);
            DecimalFormat fmt = new DecimalFormat("#.0");
            if (unit == "km") {
                distance /= 1000;
                mBuilder.setContentText(fmt.format(distance) + " kilometers away.");
            } else {
                distance *= RegionUtils.METERS_TO_MILES;
                mBuilder.setContentText(fmt.format(distance) + " miles away.");
            }
        }

        NotificationManager mNotificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(1, mBuilder.build());
    }

}
