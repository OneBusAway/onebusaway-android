package tad;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;

import org.onebusaway.android.R;
import org.onebusaway.android.io.elements.ObaStop;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Created by azizmb on 2/18/16.
 */
public class TADService extends Service
    implements LocationListener
{
    public static final String TAG = "TADService";

    private int UPDATE_TIME_INTERVAL = 5 * 1000; // 5 secs
    private int UPDATE_DISTANCE_INTERVAL = 5;   // 5m

    private LocationManager mLocationManager = null;
    private Location mLastLocation = null;

    private String dName = null;
    private Location dLocation = null;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.dName = intent.getStringExtra("STOP_NAME");
        this.dLocation = new Location(LocationManager.GPS_PROVIDER);
        this.dLocation.setLatitude(intent.getDoubleExtra("STOP_LAT", 0));
        this.dLocation.setLongitude(intent.getDoubleExtra("STOP_LNG", 0));

        final ProximityProvider provider = new ProximityProvider();
        mLocationManager = (LocationManager) getApplicationContext().getSystemService(LOCATION_SERVICE);
        if (mLocationManager != null) {
            Log.i(TAG, "Requesting Location Updates");
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UPDATE_TIME_INTERVAL, UPDATE_DISTANCE_INTERVAL, this);
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
        super.onDestroy();
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location Updated");
        mLastLocation = location;
        if (mLastLocation != null) {
            updateNotification();
            // TODO: Run TAD
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    private void updateNotification()
    {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.map_stop_icon)
                        .setContentTitle(getResources().getString(R.string.stop_notify_title))
                        .setContentText(dName);

        if (mLastLocation != null) {
            mBuilder.setContentText(mLastLocation.distanceTo(dLocation)*0.000621371 + " miles away.");
        }

        NotificationManager mNotificationManager =
                (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(1, mBuilder.build());
    }

}
