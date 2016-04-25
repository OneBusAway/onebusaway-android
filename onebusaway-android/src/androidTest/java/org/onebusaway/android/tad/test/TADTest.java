package org.onebusaway.android.tad.test;

import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.Resources;
import org.onebusaway.android.tad.Segment;
import org.onebusaway.android.tad.TADNavigationServiceProvider;

import java.io.Reader;

/**
 * Created by azizmb9494 on 3/18/16.
 */
public class TADTest extends ObaTestCase {

    static final String TAG = "TADTest";

    static final int SPEED_UP = 4;

    public void testTripA() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_trip_coords_1"));
            String csv = IOUtils.toString(reader);

            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void testTripB() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_trip_coords_2"));
            String csv = IOUtils.toString(reader);

            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    // Class for holding relevant details for testing.
    class TADTrip {
        String mTripId;
        String mDestinationId;
        String mBeforeId;

        Location mDestinationLocation;
        Location mBeforeLocation;

        Location[] mPoints;
        long[] mTimes;

        int getReadyIndex = -1;                    //  Index which getReady should be triggered.
        int finishedIndex = -1;                    //  Index which finished should be triggered.

        /**
         * Constructor
         *
         * @param csv takes a csv string with the first row containing meta-data in the format
         *            of tripId,DestinationId,dest-lat,dest-lng,beforeDestinationId,before-lat,before-lng
         *            and all following rows holding data to construct location points of a trip in the format
         *            time,lat,lng,altitude,speed,bearing,provider.
         */
        TADTrip(String csv) {
            String[] lines = csv.split("\n");

            // Setup meta data.
            String[] details = lines[0].split(",");
            mTripId = details[0];
            mDestinationId = details[1];
            mBeforeId = details[4];

            mDestinationLocation = new Location(LocationManager.GPS_PROVIDER);
            mDestinationLocation.setLatitude(Double.parseDouble(details[2]));
            mDestinationLocation.setLongitude(Double.parseDouble(details[3]));

            mBeforeLocation = new Location(LocationManager.GPS_PROVIDER);
            mBeforeLocation.setLatitude(Double.parseDouble(details[5]));
            mBeforeLocation.setLongitude(Double.parseDouble(details[6]));


            mPoints = new Location[lines.length - 1];
            // Skip header and run through csv.
            // Rows are formatted like this:
            // time,lat,lng,altitude,speed,bearing,provider.
            for (int i = 1; i < lines.length; i++) {
                String[] values = lines[i].split(",");
                long time = Long.parseLong(values[0]);
                double lat = Double.parseDouble(values[1]);
                double lng = Double.parseDouble(values[2]);
                double altitude = Double.parseDouble(values[3]);
                float speed = Float.parseFloat(values[4]);
                float bearing = Float.parseFloat(values[5]);
                float accuracy = Float.parseFloat(values[6]);
                String provider = values[7];

                mPoints[i - 1] = new Location(provider);
                mPoints[i - 1].setTime(time);
                mPoints[i - 1].setLatitude(lat);
                mPoints[i - 1].setLongitude(lng);
                mPoints[i - 1].setAltitude(altitude);
                mPoints[i - 1].setBearing(bearing);
                mPoints[i - 1].setAccuracy(accuracy);
                mPoints[i - 1].setSpeed(speed);
            }

            // Compute index of point nearest to second to last stop.
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < mPoints.length; i++) {
                if (mPoints[i].distanceTo(mBeforeLocation) < minDist) {
                    minDist = mPoints[i].distanceTo(mBeforeLocation);
                    getReadyIndex = i;
                }
            }

            // Compute time differences between readings
            // in ms.
            mTimes = new long[mPoints.length];
            for (int i = 1; i < mPoints.length; i++) {
                mTimes[i] = mPoints[i].getTime() - mPoints[i - 1].getTime();
            }
        }

        public String getTripId() {
            return mTripId;
        }

        public String getDestinationId() {
            return mDestinationId;
        }

        public String getBeforeId() {
            return mBeforeId;
        }

        public Location getDestinationLocation() {
            return mDestinationLocation;
        }

        public Location getBeforeLocation() {
            return mBeforeLocation;
        }

        public Location[] getPoints() {
            return mPoints;
        }

        public long[] getTimes() {
            return mTimes;
        }

        public int getGetReadyIndex() {
            return getReadyIndex;
        }

        public int getFinishedIndex() {
            return getFinishedIndex();
        }

        public void runSimulation(Boolean expected1, Boolean expected2) {
            TADNavigationServiceProvider provider = new TADNavigationServiceProvider(mTripId, mDestinationId);

            // Construct Destination & Second-To-Last Location
            Segment segment = new Segment(mBeforeLocation, mDestinationLocation, null);


            // Begin navigation & simulation
            provider.navigate(new Segment[]{segment});

            for (int i = 0; i < getReadyIndex; i++) {
                Location l = mPoints[i];
                try {
                    Thread.sleep((mTimes[i] / SPEED_UP));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                provider.locationUpdated(l);
                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()), Boolean.toString(provider.getFinished()))
                );
            }
            Boolean check1 = provider.getGetReady() && !provider.getFinished();
            assertEquals(expected1, check1);

            for (int i = getReadyIndex; i < mPoints.length; i++) {
                Location l = mPoints[i];
                try {
                    Thread.sleep((mTimes[i] / SPEED_UP));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                provider.locationUpdated(l);
                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()), Boolean.toString(provider.getFinished()))
                );
            }

            Boolean check2 = provider.getGetReady() && provider.getFinished();
            assertEquals(expected2, check2);
        }

    }
}
