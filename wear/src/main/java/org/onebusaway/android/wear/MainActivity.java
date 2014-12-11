package org.onebusaway.android.wear;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import org.onebusaway.android.R;
import org.onebusaway.android.core.GoogleApiHelper;
import org.onebusaway.android.core.StarredStops;
import org.onebusaway.android.core.StopData;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = "OBA::wear";

    GoogleApiHelper mGoogleApiHelper;

    private TextView mTextView;
    private Button mRefreshStarredStopsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mRefreshStarredStopsButton = (Button) stub.findViewById(R.id.btn_refresh_starred_stops);
                mRefreshStarredStopsButton.setOnClickListener(createRefreshStarredStopsButtonClickListener());
            }
        });
        mGoogleApiHelper = new GoogleApiHelper(this);
        mGoogleApiHelper.setDataListener(createDataListener());
    }

    private DataApi.DataListener createDataListener() {
        return new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                for(DataEvent event : dataEvents) {
                    DataEvent frozenEvent = event.freeze();
                    updateStarredStops(DataMap.fromByteArray(frozenEvent.getDataItem().getData()));
                }
            }
        };
    }

    private View.OnClickListener createRefreshStarredStopsButtonClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGoogleApiHelper.getClient().isConnected()) {
                    requestStarredStops();
                }
            }
        };
    }

    private void updateStarredStops(DataMap dataMap) {
        ArrayList<DataMap> stopDataMaps = dataMap.getDataMapArrayList(StarredStops.DATA_MAP_KEY_STARRED_STOPS_LIST);
        for(DataMap stopDataMap : stopDataMaps) {
            StopData stopData = new StopData(stopDataMap);
            Log.d(TAG, "updateStarredStops::" + stopData.getUiName());
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiHelper.start();
    }

    @Override
    protected void onStop() {
        mGoogleApiHelper.stop();
        super.onStop();
    }

    private void requestStarredStops() {
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
}