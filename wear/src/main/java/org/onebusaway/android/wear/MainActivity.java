package org.onebusaway.android.wear;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import org.onebusaway.android.R;
import org.onebusaway.android.core.GoogleApiHelper;
import org.onebusaway.android.core.StarredStops;
import org.onebusaway.android.core.StopData;

import java.util.ArrayList;

public class MainActivity extends Activity implements GoogleApiHelper.OnConnectionCompleteCallback, WearableListView.ClickListener {

    private static final String TAG = "OBA::wear::MainActivity";

    private ArrayList<StopData> stops = new ArrayList<>();
    private StopListAdapter mStopListAdapter;

    GoogleApiHelper mGoogleApiHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupView();
        mGoogleApiHelper = new GoogleApiHelper(this);
        mGoogleApiHelper.setDataListener(createDataListener());
    }

    private void setupView() {
        mStopListAdapter = new StopListAdapter(this, stops);
        setContentView(R.layout.activity_starred_stops);
        setupListView();
    }

    private void setupListView() {
        WearableListView view = (WearableListView)findViewById(R.id.stop_list);
        view.setAdapter(mStopListAdapter);
        view.setClickListener(this);
    }

    private DataApi.DataListener createDataListener() {
        return new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                Log.d(TAG, "createDataListener::onDataChanged::" + dataEvents.getCount());
                for(DataEvent event : dataEvents) {
                    DataEvent frozenEvent = event.freeze();
                    if (frozenEvent.getDataItem().getUri().getPath().equals(StarredStops.URI_STARRED_STOPS)) {
                        if (frozenEvent.getType() == DataEvent.TYPE_CHANGED) {
                            updateStarredStops(DataMap.fromByteArray(frozenEvent.getDataItem().getData()));
                        } else if (frozenEvent.getType() == DataEvent.TYPE_DELETED) {
                            removeStarredStops(DataMap.fromByteArray(frozenEvent.getDataItem().getData()));
                        }
                    }
                }
            }
        };
    }

    private void removeStarredStops(DataMap dataMap) {
        Log.d(TAG, "removeStarredStops");
        ArrayList<DataMap> stopDataMaps = dataMap.getDataMapArrayList(StarredStops.DATA_MAP_KEY_STARRED_STOPS_LIST);
        for(int i = 0; i < stopDataMaps.size(); i++) {
            String id = stopDataMaps.get(i).getString(StopData.Keys.ID.getValue());
            removeStop(id);
            Log.d(TAG, "removeStarredStops::" + stops.get(i).getUiName());
        }
        Log.d(TAG, "removeStarredStops::" + stops.size());
    }

    private void notifyStopsListAdapterItemRemoved(final int position) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStopListAdapter.notifyItemRemoved(position);
            }
        });
    }

    private boolean removeStop(String id) {
        for(int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getId().equals(id)) {
                stops.remove(i);
                notifyStopsListAdapterItemRemoved(i);
                return true;
            }
        }
        return false;
    }

    private void updateStarredStops(DataMap dataMap) {
        Log.d(TAG, "updateStarredStops");
        ArrayList<DataMap> stopDataMaps = dataMap.getDataMapArrayList(StarredStops.DATA_MAP_KEY_STARRED_STOPS_LIST);
        for(int i = 0; i < stopDataMaps.size(); i++) {
            StopData updatedStop = new StopData(stopDataMaps.get(i));
            updateStop(updatedStop);
            Log.d(TAG, "updateStarredStops::" + stops.get(i).getUiName());
        }
        Log.d(TAG, "updateStarredStops::" + stops.size());
    }

    private void updateStop(StopData stop) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getId().equals(stop.getId())) {
                stops.set(i, stop);
                notifyStopsListAdapterItemUpdated(i);
                return;
            }
        }
        stops.add(stop);
    }

    private void notifyStopsListAdapterItemUpdated(final int position) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStopListAdapter.notifyItemChanged(position);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiHelper.setSingleShotConnectionCompleteCallback(this);
        mGoogleApiHelper.start();
    }

    @Override
    protected void onStop() {
        mGoogleApiHelper.stop();
        super.onStop();
    }

    private void requestStarredStops() {
        Log.d(TAG, "requestStarredStops");
        PendingResult<NodeApi.GetConnectedNodesResult> getConnectedNodesResultPendingResult = Wearable.NodeApi.getConnectedNodes(mGoogleApiHelper.getClient());
        getConnectedNodesResultPendingResult.setResultCallback(createConnectedNodesCallback());

    }

    private ResultCallback<NodeApi.GetConnectedNodesResult> createConnectedNodesCallback() {
        return new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                Log.d(TAG, "createConnectedNodesCallback::gettingConnectedNodes");
                for(Node n : getConnectedNodesResult.getNodes()) {
                    Log.d(TAG, "createConnectedNodesCallback::sendingMessageResult for " + n.getId());
                    PendingResult<MessageApi.SendMessageResult> sendMessageResult =
                            Wearable.MessageApi.sendMessage(
                                    mGoogleApiHelper.getClient(),
                                    n.getId(),
                                    StarredStops.URI_REQUEST_SYNC_STARRED_STOPS,
                                    null);
                    sendMessageResult.setResultCallback(createSendMessageResultCallback());

                }
            }
        };
    }

    private ResultCallback<MessageApi.SendMessageResult> createSendMessageResultCallback() {
        return new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                Log.d(TAG, "createSendMessageResultCallback");
            }
        };
    }

    @Override
    public void onConnectionComplete() {
        Log.d(TAG, "onConnectionComplete");
        reloadStops();
        requestStarredStops();
    }

    private void reloadStops() {
        Uri uri = new Uri.Builder().scheme("wear").path(StarredStops.URI_STARRED_STOPS).build();
        Log.d(TAG, "refreshDataItems::uri::" + uri);
        Wearable.DataApi.getDataItems(mGoogleApiHelper.getClient(), uri).setResultCallback(createRefreshDataItemsResultCallback());
    }

    private ResultCallback<DataItemBuffer> createRefreshDataItemsResultCallback() {
        return new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer buffer) {
                Log.d(TAG, "createRefreshDataItemsResultCallback::onResult");
                for (DataItem item : buffer) {
                    DataMap dm = DataMap.fromByteArray(item.getData());
                    ArrayList<DataMap> stopsDataMap = dm.getDataMapArrayList(StarredStops.DATA_MAP_KEY_STARRED_STOPS_LIST);
                    reloadStopsAdapter(stopsDataMap);
                }
                buffer.release();
            }
        };
    }

    private void reloadStopsAdapter(ArrayList<DataMap> dataMaps) {
        Log.d(TAG, "reloadStopsAdapter");
        stops.clear();
        for(DataMap dm : dataMaps) {
            StopData stop = new StopData(dm);
            stops.add(stop);
            Log.d(TAG, "reloadStopsAdapter::" + stop.getUiName());
        }
        notifyStopsListAdapterDataSetChanged();
    }

    private void notifyStopsListAdapterDataSetChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStopListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void handleStopDataClick(StopData stopData) {
        Log.d(TAG, "handleStopDataClick::" + stopData.getUiName());
    }

    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {
        int position = (int) viewHolder.itemView.getTag();
        if (position >= stops.size()) {
            requestStarredStops();
        } else {
            handleStopDataClick(stops.get(position));
        }
    }

    @Override
    public void onTopEmptyRegionClick() {

    }
}