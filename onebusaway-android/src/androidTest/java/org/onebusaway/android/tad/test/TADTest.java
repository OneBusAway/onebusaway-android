package org.onebusaway.android.tad.test;

import android.location.Location;
import android.location.LocationManager;

import com.google.android.gms.location.LocationServices;

import org.apache.commons.io.IOUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.tad.TADNavigationServiceProvider;

import java.io.InputStream;
import java.util.Date;

/**
 * Created by azizmb on 3/18/16.
 */
public class TADTest extends ObaTestCase {

    static final String TRIP_ID = "";
    static final String STOP_ID = "";

    public void testTrip()
    {
        try {
            InputStream inputStream = getContext().getResources().openRawResource(R.raw.regions_v3);
            String csv = IOUtils.toString(inputStream);
            TADNavigationServiceProvider provider = new TADNavigationServiceProvider(TRIP_ID, STOP_ID);
            for (Location l : getTrip(csv)) {
                provider.locationUpdated(l);
            }
            assertEquals(provider.getFinished(), true);
        } catch (Exception e) {

        }
    }

    /**
     * Takes a CSV string and returns an array of Locations built from CSV data.
     * The first line of the csv is assumed to be a header, and the columns as follows
     * time, lat, lng, elevation, accuracy, bearing, speed, provider.
     * @param csv
     * @return
     */
    private Location[] getTrip(String csv)
    {
        String[] lines = csv.split("\n");

        Location[] locations = new Location[lines.length-1];

        // Skip header and run through csv.
        // Rows are formatted like this:
        // time,lat,lon,elevation,accuracy,bearing,speed,satellites,provider
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(",");
            double lat = Double.parseDouble(values[1]);
            double lng = Double.parseDouble(values[2]);
            double alt = Double.parseDouble(values[3]);
            float acc = Float.parseFloat(values[4]);
            float bearing = Float.parseFloat(values[5]);
            float speed = Float.parseFloat(values[6]);
            String provider = values[8];

            if (provider.equalsIgnoreCase("gps")) {
                locations[i-1] = new Location(LocationManager.GPS_PROVIDER);
            } else if (provider.equalsIgnoreCase("network")) {
                locations[i-1] = new Location(LocationManager.NETWORK_PROVIDER);
            } else {
                locations[i-1] = new Location(LocationManager.PASSIVE_PROVIDER);
            }

            locations[i-1].setLatitude(lat);
            locations[i-1].setLongitude(lng);
            locations[i-1].setBearing(bearing);
            locations[i-1].setSpeed(speed);
            locations[i-1].setAccuracy(acc);
            locations[i-1].setAltitude(alt);
        }

        return null;
    }
}
