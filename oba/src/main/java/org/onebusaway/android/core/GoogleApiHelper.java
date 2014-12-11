package org.onebusaway.android.core;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.Wearable;

/**
 * Wraps boilerplate GoogleApi code.  Concrete classes implement the DataListener callbacks for data updates during application runtime.
 */
public abstract class GoogleApiHelper implements DataApi.DataListener {
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
        Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                handleOnResultsRetrieved(dataItems);
            }
        });
    }

    // called when the google api connects to load initial data items.
    protected abstract void handleOnResultsRetrieved(DataItemBuffer dataItems);

    public void start() {
        mGoogleApiClient.connect();
    }

    public void stop() {
        mGoogleApiClient.disconnect();
    }

    public GoogleApiClient getClient() {
        return mGoogleApiClient;
    }
}
