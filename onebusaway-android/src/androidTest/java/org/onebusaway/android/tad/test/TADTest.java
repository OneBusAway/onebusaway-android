package org.onebusaway.android.tad.test;

import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.google.android.gms.location.LocationServices;

import org.apache.commons.io.IOUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.Resources;
import org.onebusaway.android.tad.Segment;
import org.onebusaway.android.tad.TADNavigationServiceProvider;

import java.io.InputStream;
import java.io.Reader;
import java.util.Date;

/**
 * Created by azizmb on 3/18/16.
 */
public class TADTest extends ObaTestCase {

    static final String TAG = "TADTest";
    static final String TRIP_ID = "";
    static final String STOP_ID = "";

    static final double DEST_LAT = 28.059462;
    static final double DEST_LNG = -82.4120362;

    static final double BEFORE_LAT = 28.06174;
    static final double BEFORE_LNG = -82.4096792;

    public void testTrip()
    {
        TADNavigationServiceProvider provider = new TADNavigationServiceProvider(TRIP_ID, STOP_ID);
        try {
            // Construct Destination & Second-To-Last Locations
            Location dest = new Location(LocationManager.GPS_PROVIDER);
            dest.setLatitude(DEST_LAT);
            dest.setLongitude(DEST_LNG);

            Location last = new Location(LocationManager.GPS_PROVIDER);
            last.setLatitude(BEFORE_LAT);
            last.setLatitude(BEFORE_LNG);

            Segment segment = new Segment(last, dest, null);

            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_trip_coords_1"));
            String csv = IOUtils.toString(reader);

            // Begin navigation & simulation
            provider.navigate(new org.onebusaway.android.tad.Service(), new Segment[] { segment });

            for (Location l : getTrip(csv)) {
                provider.locationUpdated(l);
                Log.i(TAG, String.format("(%f, %f, %f)\tR:%s  F:%s",
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()), Boolean.toString(provider.getFinished())
                ));
            }

            assertEquals(true, provider.getGetReady() && provider.getFinished());
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        }
    }

    /**
     * Takes a CSV string and returns an array of Locations built from CSV data.
     * The first line of the csv is assumed to be a header, and the columns as follows
     * time, lat, lng, elevation, accuracy, bearing, speed, provider.
     * Generated using GPS Logger for Android (https://github.com/mendhak/gpslogger)
     * (Also, available on the play store).
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

        return locations;
    }
}
