package org.onebusaway.android.node;

import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.core.GoogleApiHelper;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.provider.ObaProvider;
import org.onebusaway.android.core.StopData;
import org.onebusaway.android.ui.QueryUtils;
import org.onebusaway.android.provider.StopDataCursorHelper;

import java.util.ArrayList;

/**
 * A long running background service listening for data updates.  The intention of this class is to
 * listen for datamap updates from nodes (such as a wearable).
 *
 * This class is also the entry point for any message requests from nodes to request data or execute
 * actions on the device.
 */
public class NodeService extends Service implements MessageApi.MessageListener, QueryUtils.StopList.Columns {

    public static final String MESSAGE_SYNC_STARRED_STOPS = "sync_starred_stops";

    public static final String URI_STARRED_STOPS = "/starredstops";

    public static final String DATA_MAP_KEY_STARRED_STOPS_LIST = "starred_stops_key";

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
        if (MESSAGE_SYNC_STARRED_STOPS.equals(messageEvent.getPath())) {
            syncStarredStops();
        }
    }

    private void syncStarredStops() {
        ContentProviderClient client = null;
        try {
            client = getApplicationContext().getContentResolver()
                    .acquireContentProviderClient(ObaContract.AUTHORITY);
            ObaProvider provider = (ObaProvider) client.getLocalContentProvider();
            Cursor c = provider.query(
                    ObaContract.Stops.CONTENT_URI,
                    PROJECTION,
                    ObaContract.Stops.FAVORITE + "=1" +
                            (Application.get().getCurrentRegion() == null ? "" : " AND " +
                                    QueryUtils.getRegionWhere(ObaContract.Stops.REGION_ID,
                                            Application.get().getCurrentRegion().getId())),
                    null,
                    ObaContract.Stops.USE_COUNT + " desc");
            copyCursorToWearData(c);
        } finally {
            if (client != null) {
                client.release();
            }
        }
    }

    private void copyCursorToWearData(Cursor c) {
        ArrayList<DataMap> dataMaps = new ArrayList<DataMap>();
        for(int i = 0; i < c.getCount(); i++) {
            StopData data = StopDataCursorHelper.createStopData(c, i);
            dataMaps.add(data.toDataMap());
        }
        // @todo: poh - optimize this so it doesnt always add the whole list, maybe a way to do only the delta
        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(URI_STARRED_STOPS);
        dataMapRequest.getDataMap().putDataMapArrayList(DATA_MAP_KEY_STARRED_STOPS_LIST, dataMaps);
        PutDataRequest request = dataMapRequest.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi
                .putDataItem(mGoogleApiHelper.getClient(), request);
    }
}
