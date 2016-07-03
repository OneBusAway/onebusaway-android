package org.onebusaway.android.tad.test;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.Resources;
import org.onebusaway.android.tad.Segment;
import org.onebusaway.android.tad.TADNavigationServiceProvider;
import org.onebusaway.android.tad.TADService;


import java.io.Reader;

/**
 * Created by azizmb9494 on 3/18/16.
 */
public class TADTest extends ObaTestCase {

    static final String TAG = "TADTest";

    static final int SPEED_UP = 4;
    private int i = 0;
    private int getReadyID;
    private int pullCordID;


      /* Started Stop: Mckinley Dr @ DOT Bldg
       Destination Stop: University Area Transit Center
       Recorded In: Bus (Route 5) 9 stops
       Device Used: Nexus 5 */

    public void testTripA() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_tripa"));
            String csv = IOUtils.toString(reader);
            getReadyID = 858;
            pullCordID = 978;

            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /* Started Stop: Alumni Dr @ Leroy Collins @ Eng Bldg
       Destination Stop: Mckinley Dr @ Fowler Ave
       Recorded In: Car following Route 5 - 1 stops
       Device Used: Nexus 5
    */

    public void testTripB() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_tripb"));
            String csv = IOUtils.toString(reader);
            getReadyID = 1;
            pullCordID = 14;

            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }



    /* Started Stop: Dale Mabry Hwy @ Linebaugh Av
       Destination Stop: Dale Mabry Hwy @ Hudson Ln @ Taco Bell
       Recorded In: Bus (Route 36) 4 stops
       Device Used: Nexus 5 */
    public void testTripC() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_tripc"));
            String csv = IOUtils.toString(reader);
            getReadyID = 95;
            pullCordID = 109;

            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }


    /* Started Stop: Linebaugh @ Henderson Av
       Destination Stop: Anderson Rd @ 8110
       Recorded In: Bus (Route 7) 4 stops
       Device Used: Nexus 5 */
   public void testTripE() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_tripe"));
            String csv = IOUtils.toString(reader);
            getReadyID = 372;
            pullCordID = 660;
            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /*Started Stop: Himes Av @ Colwell Av
      Destination Stop: Himes Av @ Hillsborough Av
      Recorded In: Bus (Route 36) 12 stops
      Device Used: Nexus 5 */

    public void testTripF() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_tripf"));
            String csv = IOUtils.toString(reader);
            getReadyID = 801;
            pullCordID = 837;
            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /*Started Stop: Gunn Hwy @ Premier North Dr
     Destination Stop: Bush Blvd @ Armenia Av
     Recorded In: Bus (Route 39) 3 stops
     Device Used: Nexus 5 */
   public void testTripG() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_tripg"));
            String csv = IOUtils.toString(reader);
            getReadyID = 183;
            pullCordID = 208;
            TADTrip trip = new TADTrip(csv);
            trip.runSimulation(true, true);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    /*Started Stop: Alumni Dr @ Beard
    Destination Stop: Magnolia Dr @ Alumni Dr
    Recorded In: Car following Route 5 (1 stop)
    Device Used: Nexus 5 */
    public void testTripH() {
        try {
            // Read test CSV.
            Reader reader = Resources.read(getContext(), Resources.getTestUri("tad_triph"));
            String csv = IOUtils.toString(reader);
            getReadyID = 1;
            pullCordID = 18;
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

        boolean useElapsedNanos = false;           // Should use elapsed nanos instead of time.


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
            // realtime Nanos Elapsed,time,lat,lng,altitude,speed,bearing,accurarcy,satellites,provider.
            for (int i = 1; i < lines.length; i++) {



                String[] values = lines[i].split(",");
                int coordinateIndex = Integer.parseInt(values[0]);
                String getReadyValue = values[1];
                String pullTheCordValue = values[2];
                String nanosStr = values[3];
                long time = Long.parseLong(values[4]);
                double lat = Double.parseDouble(values[5]);
                double lng = Double.parseDouble(values[6]);
                double altitude = Double.parseDouble(values[7]);
                float speed = Float.parseFloat(values[8]);
                float bearing = Float.parseFloat(values[9]);
                float accuracy = Float.parseFloat(values[10]);
                int sats = Integer.parseInt(values[11]);
                String provider = values[12];




                mPoints[i - 1] = new Location(provider);

                // Check if we can use elapsed nano seconds. Else, we'll use time.
                if (!nanosStr.equals("") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                {
                    useElapsedNanos = true;
                    mPoints[i - 1].setElapsedRealtimeNanos(Long.parseLong(nanosStr));
                }

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

            // Compute time differences between readings in ms, if realtime ns is available, use it
            // else use getTime.
            mTimes = new long[mPoints.length];
            if (useElapsedNanos && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                for (int i = 1; i < mPoints.length; i++) {
                    mTimes[i] = (mPoints[i].getElapsedRealtimeNanos() - mPoints[i-1].getElapsedRealtimeNanos());
                    mTimes[i] /= 1000000;
                }
            } else {
                for (int i = 1; i < mPoints.length; i++) {
                    mTimes[i] = mPoints[i].getTime() - mPoints[i - 1].getTime();
                }
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
            return finishedIndex;
        }

        public void runSimulation(Boolean expected1, Boolean expected2) {
            TADNavigationServiceProvider provider = new TADNavigationServiceProvider(mTripId, mDestinationId);

            // Construct Destination & Second-To-Last Location
            Segment segment = new Segment(mBeforeLocation, mDestinationLocation, null);


            // Begin navigation & simulation
            provider.navigate(new Segment[]{segment});

            for (int i = 0; i <=  getReadyID; i++) {
                Location l = mPoints[i];

                try {
                    Thread.sleep((mTimes[i] / SPEED_UP));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                provider.locationUpdated(l);

                    if(provider.getGetReady() && i < getReadyID)
                    {
                        fail("Get ready triggered too soon");
                    }

                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()), Boolean.toString(provider.getFinished())));

            }


          Boolean check1 = provider.getGetReady() && !provider.getFinished();
          assertEquals(expected1, check1);


            for (int i = getReadyID; i <= pullCordID; i++) {
                Location l = mPoints[i];
                try {
                    Thread.sleep((mTimes[i] / SPEED_UP));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                provider.locationUpdated(l);

                if(provider.getFinished() && i < pullCordID)
                {
                    fail("Pull the Cord triggered too soon");
                }


                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()), Boolean.toString(provider.getFinished())));


            }

            Boolean check2 = provider.getGetReady() && provider.getFinished();
            assertEquals(expected2, check2);
        }

    }
}
