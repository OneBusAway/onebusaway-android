package org.onebusaway.android.node;

import android.app.Service;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.GoogleApiHelper;
import org.onebusaway.android.core.StarredStops;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
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
public class NodeService extends Service implements QueryUtils.StopList.Columns {

    private GoogleApiHelper mGoogleApiHelper;
    private static final String TAG = "OBA::NodeService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mGoogleApiHelper = new GoogleApiHelper(Application.get());
        mGoogleApiHelper.setMessageListener(createMessageListener());
        mGoogleApiHelper.start();
        Log.d(TAG, "onStartCommand");
        return START_STICKY; // @todo: poh - doesnt seem to work.  service is not restarting itself on nexus4
    }

    private MessageApi.MessageListener createMessageListener() {
        return new MessageApi.MessageListener() {
            @Override
            public void onMessageReceived(MessageEvent messageEvent) {
                handleOnMessageReceived(messageEvent);
            }
        };
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mGoogleApiHelper.stop();
        mGoogleApiHelper = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleOnMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived::" + messageEvent.getPath());
        if (StarredStops.URI_REQUEST_SYNC_STARRED_STOPS.equals(messageEvent.getPath())) {
            syncStarredStops();
        } else if (StarredStops.URI_REQUEST_ARRIVAL_INFO_FOR_STOP.equals(messageEvent.getPath())) {
            syncArrivalInfo(DataMap.fromByteArray(messageEvent.getData()));
        }
    }

    private void syncArrivalInfo(DataMap dataMap) {
        String stopId = dataMap.getString(StopData.Keys.ID.toString());
        final ObaArrivalInfoRequest request = ObaArrivalInfoRequest.newRequest(Application.get(), stopId);
        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                ObaArrivalInfoResponse response = request.call();
                clearExpiredArrivalInfos();
                handleObaArrivalInfoResponse(response);
            }
        });
    }

    private final static String ARRIVAL_PATH = "arrivals";
    private static final String KEY_TIME = "time";
    private static final String KEY_VEHICLE_ID = "vehicleId";
    private static final String KEY_ROUTE_NAME = "routeName";
    private final static Uri.Builder baseUriBuilder = new Uri.Builder().appendPath(ARRIVAL_PATH);

    private void clearExpiredArrivalInfos() {
        DataItemBuffer buffer = Wearable.DataApi.getDataItems(mGoogleApiHelper.getClient(), baseUriBuilder.build()).await();
        for(DataItem item : buffer) {
            DataMap dm = DataMap.fromByteArray(item.getData());
            if (dm.getLong(KEY_TIME) - System.currentTimeMillis() < 0) { // @todo: poh - confirm this is GMT
                Wearable.DataApi.deleteDataItems(mGoogleApiHelper.getClient(), item.getUri()).setResultCallback(createEmptyDeleteResultCallback());
            }
        }
        buffer.release();
    }

    private ResultCallback<DataApi.DeleteDataItemsResult> createEmptyDeleteResultCallback() {
        return new ResultCallback<DataApi.DeleteDataItemsResult>() {
            @Override
            public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {

            }
        };
    }

    private void handleObaArrivalInfoResponse(ObaArrivalInfoResponse response) {
        for(int i = 0; i < response.getArrivalInfo().length; i++) {
            ObaArrivalInfo info = response.getArrivalInfo()[i];
            Uri newUri = baseUriBuilder
                    .appendPath(info.getStopId())
                    .appendPath(info.getRouteId())
                    .build();
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create(newUri.getPath());
            dataMapRequest.getDataMap().putString(KEY_VEHICLE_ID, info.getVehicleId());
            dataMapRequest.getDataMap().putLong(KEY_TIME, info.getPredictedArrivalTime());
            dataMapRequest.getDataMap().putString(KEY_ROUTE_NAME, info.getRouteLongName());
            PutDataRequest request = dataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(mGoogleApiHelper.getClient(), request).setResultCallback(createEmptyResultCallback());
        }
    }

    private synchronized void syncStarredStops() {
        Log.d(TAG, "syncStarredStops");
        ContentProviderClient client = null;
        try {
            client = getApplicationContext().getContentResolver()
                    .acquireContentProviderClient(ObaContract.AUTHORITY);
            ObaProvider provider = (ObaProvider) client.getLocalContentProvider();

            String selection = ObaContract.Stops.FAVORITE + "=1" +
                    (Application.get().getCurrentRegion() == null ? "" : " AND " +
                            QueryUtils.getRegionWhere(ObaContract.Stops.REGION_ID,
                                    Application.get().getCurrentRegion().getId()));

            Cursor c = provider.query(
                    ObaContract.Stops.CONTENT_URI,
                    PROJECTION,
                    selection,
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
        Log.d(TAG, "copyCursorToWearData");
        ArrayList<DataMap> dataMaps = new ArrayList<DataMap>();
        for(int i = 0; i < c.getCount(); i++) {
            StopData data = StopDataCursorHelper.createStopData(c, i);
            DataMap dataMap = data.toDataMap();
            Log.d(TAG, "syncing " + data.getUiName());
            dataMaps.add(dataMap);
        }
        // @todo: poh - optimize this so it doesnt always add the whole list, maybe a way to do only the delta
        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(StarredStops.URI_STARRED_STOPS);
        dataMapRequest.getDataMap().putDataMapArrayList(StarredStops.DATA_MAP_KEY_STARRED_STOPS_LIST, dataMaps);
        PutDataRequest request = dataMapRequest.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiHelper.getClient(), request).setResultCallback(createEmptyResultCallback());
    }

    private ResultCallback<DataApi.DataItemResult> createEmptyResultCallback() {
        return new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                Log.d(TAG, "createEmptyResultCallback::" + dataItemResult.getStatus().isSuccess());
            }
        };
    }

    public static void schedule(Context context) {
        final Intent intent = new Intent(context, NodeService.class);
        context.startService(intent);
    }
}
