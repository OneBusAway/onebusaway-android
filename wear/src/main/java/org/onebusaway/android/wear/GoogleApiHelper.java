package org.onebusaway.android.wear;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Wearable;

public class GoogleApiHelper implements DataApi.DataListener {
    private final String TAG = "GoogleApiHelper";

    private GoogleApiClient mGoogleApiClient;
    private Context mContext;

    public GoogleApiHelper(Context context) {
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.i(TAG, "google api client connected");
                        handledOnConnected();
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.i(TAG, "google api client connection suspended code[" + cause + "]");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.e(TAG, "failed to connect to google api client code[" + result.getErrorCode() +"]");
                        Log.e(TAG, result.toString());
                    }
                })
                .addApi(Wearable.API)
                .build();
    }

    private void handledOnConnected() {
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        PendingResult<DataItemBuffer> dataItems = Wearable.DataApi.getDataItems(mGoogleApiClient);
        dataItems.setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                handleOnResultsRetrieved(dataItems);
            }
        });
    }

    private void handleOnResultsRetrieved(DataItemBuffer dataItems) {
        for(DataItem item : dataItems) {
            DataMap map = DataMap.fromByteArray(item.getData());
            // todo: do something with the datamap
        }
    }

    public void start() {
        mGoogleApiClient.connect();
    }

    public void stop() {
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for(DataEvent event : dataEvents) {
            // todo: do something with the event
        }
    }
}
