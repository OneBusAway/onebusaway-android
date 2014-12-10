package org.onebusaway.android.node;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;

import org.onebusaway.android.core.GoogleApiHelper;

/**
 * A long running background service listening for data updates.  The intention of this class is to
 * listen for datamap updates from nodes (such as a wearable).
 *
 * This class is also the entry point for any message requests from nodes to request data or execute
 * actions on the device.
 */
public class NodeService extends Service implements MessageApi.MessageListener {

    private GoogleApiHelper mGoogleApiHelper;
    private static final String TAG = "OBA::NodeService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mGoogleApiHelper = new GoogleApiHelper(getApplicationContext()) {
            @Override
            protected void handleOnResultsRetrieved(DataItemBuffer dataItems) {

            }

            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {

            }
        };
        mGoogleApiHelper.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mGoogleApiHelper.stop();
        mGoogleApiHelper = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived::" + messageEvent.getPath());
        Toast.makeText(getApplicationContext(), messageEvent.getPath(), Toast.LENGTH_SHORT).show(); // todo: poh - debug
    }
}
