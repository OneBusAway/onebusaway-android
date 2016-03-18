package org.onebusaway.android.tad.test;

import android.location.Location;

import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.tad.TADNavigationServiceProvider;

/**
 * Created by azizmb on 3/18/16.
 */
public class TADTest extends ObaTestCase {

    static final String TRIP_ID = "";
    static final String STOP_ID = "";

    public void testTrip()
    {
        TADNavigationServiceProvider provider = new TADNavigationServiceProvider(TRIP_ID, STOP_ID);
        for (Location l : getTrip()) {
            provider.locationUpdated(l);
        }
    }

    private Location[] getTrip()
    {
        return null;
    }
}
