package org.onebusaway.android.tripservice;

import android.app.IntentService;
import android.content.Intent;

import org.onebusaway.android.io.elements.ObaStop;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Created by azizmb on 2/18/16.
 */
public class TADService extends IntentService
{
    public static final String TAG = "TADService";
    public TADService()
    {
        super(TAG);
    }

    public TADService(ObaStop stop)
    {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent workIntent) {

    }
}
