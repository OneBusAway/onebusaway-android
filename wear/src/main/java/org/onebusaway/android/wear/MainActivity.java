package org.onebusaway.android.wear;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.widget.TextView;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;

import org.onebusaway.android.core.GoogleApiHelper;

public class MainActivity extends Activity {

    private static final String TAG = "OBAWEAR";

    GoogleApiHelper mGoogleApiHelper;

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });
        mGoogleApiHelper = new GoogleApiHelper(this) {
            @Override
            protected void handleOnResultsRetrieved(DataItemBuffer dataItems) {

            }

            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {

            }
        };
    }

    @Override
    protected void onStart() {
        super.onResume();
        mGoogleApiHelper.start();
    }

    @Override
    protected void onStop() {
        mGoogleApiHelper.stop();
        super.onPause();
    }
}