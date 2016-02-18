package org.onebusaway.android.tripservice;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;

import org.onebusaway.android.io.elements.ObaStop;

/**
 * Created by azizmb on 2/18/16.
 */
public class TADService extends IntentService
{
    public TADService(ObaStop destination)
    {
        super("TADService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }
}
