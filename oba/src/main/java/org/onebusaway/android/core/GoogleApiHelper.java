package org.onebusaway.android.core;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;

/**
 * Wraps boilerplate GoogleApi code.  Concrete classes implement the DataListener callbacks for data updates during application runtime.
 */
public class GoogleApiHelper {
    private final String TAG = "OBA::GoogleApiHelper";

    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    private DataApi.DataListener dataListener;
    private MessageApi.MessageListener messageListener;

    public GoogleApiHelper(Context context) {
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "google api client connected");
                        handledOnConnected();
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "google api client connection suspended code[" + cause + "]");
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

    public void setDataListener(DataApi.DataListener listener) {
        dataListener = listener;
    }

    public void setMessageListener(MessageApi.MessageListener listener) {
        messageListener = listener;
    }

    private void handledOnConnected() {
        if (dataListener != null) {
            Wearable.DataApi.addListener(mGoogleApiClient, dataListener);
        }
        if (messageListener != null) {
            Wearable.MessageApi.addListener(mGoogleApiClient, messageListener);
        }
    }

    public void start() {
        mGoogleApiClient.connect();
    }

    public void stop() {
        if (dataListener != null) {
            Wearable.DataApi.removeListener(mGoogleApiClient, dataListener);
        }
        if (messageListener != null) {
            Wearable.MessageApi.removeListener(mGoogleApiClient, messageListener);
        }
        mGoogleApiClient.disconnect();
    }

    public GoogleApiClient getClient() {
        return mGoogleApiClient;
    }
}
