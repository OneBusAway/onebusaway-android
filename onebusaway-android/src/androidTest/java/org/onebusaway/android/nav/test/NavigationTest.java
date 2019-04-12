/*
 * Copyright (C) 2005-2018 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.nav.test;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.Resources;
import org.onebusaway.android.nav.NavigationServiceProvider;
import org.onebusaway.android.nav.model.Path;
import org.onebusaway.android.nav.model.PathLink;
import org.onebusaway.android.util.LocationUtils;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;

import androidx.test.runner.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Loads previously recorded trips (GPS data) and replays them through the NavigationServiceProvider
 * via the NavigationSimulation class.
 *
 * For creating new test methods - see the file DESTINATION_REMINDERS.md
 */
@RunWith(AndroidJUnit4.class)
public class NavigationTest extends ObaTestCase {

    private static final String TAG = "NavigationTest";

    private static final long SPEED_UP = 1000000L;

    /**
     * Started Stop: Mckinley Dr @ DOT Bldg
     * Destination Stop: University Area Transit Center
     * Recorded In: Bus (Route 5) 9 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip1() throws IOException {
        runSimulation("nav_trip1", 848, 978);
    }

    /**
     * Started Stop: Mckinley Dr @ DOT Bldg
     * Destination Stop: University Area Transit Center
     * Recorded In: car following Bus (Route 5) 9 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip1C() throws IOException {
        runSimulation("nav_trip1c", 665, 929);
    }

    /**
     * Started Stop: Alumni Dr @ Leroy Collins @ Eng Bldg
     * Destination Stop: Mckinley Dr @ Fowler Ave
     * Recorded In: Bus Route 5 - 1 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip2() throws IOException {
         runSimulation("nav_trip2", 0, 15);
    }

    /**
     * Started Stop: Alumni Dr @ Leroy Collins @ Eng Bldg
     * Destination Stop: Mckinley Dr @ Fowler Ave
     * Recorded In: Car following Route 5 - 1 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip2C() throws IOException {
        runSimulation("nav_trip2c", 0, 64);
    }

    /**
     * Started Stop: Dale Mabry Hwy @ Linebaugh Av
     * Destination Stop: Dale Mabry Hwy @ Hudson Ln @ Taco Bell
     * Recorded In: Bus (Route 36) 4 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip3() throws IOException {
        runSimulation("nav_trip3", 95, 111);
    }

    /**
     * Started Stop: Dale Mabry Hwy @ Linebaugh Av
     * Destination Stop: Dale Mabry Hwy @ Hudson Ln @ Taco Bell
     * Recorded In: car following Bus (Route 36) 4 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip3C() throws IOException {
        runSimulation("nav_trip3c", 87, 109);
    }

    /**
     * Started Stop: Gunn Hwy @ Mullis City Way
     * Destination Stop: Gunn Hwy @ Anderson Rd
     * Recorded In: Bus (Route 39)
     * Number of Stops = 7
     * Device Used: Kyocera
     */
    @Test
    public void testTrip4() throws IOException {
        runSimulation("nav_trip4", 294, 329);
    }

    /**
     * Started Stop: Gunn Hwy @ Mullis City Way
     * Destination Stop: Gunn Hwy @ Anderson Rd
     * Recorded In: car following Bus (Route 39)
     * Number of Stops = 7
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip4C() throws IOException {
        runSimulation("nav_trip4c", 253, 329);
    }

    /**
     * Started Stop: Linebaugh @ Henderson Av
     * Destination Stop: Anderson Rd @ 8110
     * Recorded In: Bus (Route 7) 4 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip5() throws IOException {
        runSimulation("nav_trip5", 372, 660);
    }

    /**
     * Started Stop: Linebaugh @ Henderson Av
     * Destination Stop: Anderson Rd @ 8110
     * Recorded In: Car following Bus (Route 7) 4 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip5C() throws IOException {
        runSimulation("nav_trip5c", 284, 492);
    }

    /**
     * Started Stop: Himes Av @ Colwell Av
     * Destination Stop: Himes Av @ Hillsborough Av
     * Recorded In: Bus (Route 36) 12 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip6() throws IOException {
        runSimulation("nav_trip6", 801, 837);
    }

    /**
     * Started Stop: Himes Av @ Colwell Av
     * Destination Stop: Himes Av @ Hillsborough Av
     * Recorded In: car following Bus (Route 36) 12 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip6C() throws IOException {
        runSimulation("nav_trip6c", 676, 704);
    }

    /**
     * Started Stop: Gunn Hwy @ Premier North Dr
     * Destination Stop: Bush Blvd @ Armenia Av
     * Recorded In: Bus (Route 39) 3 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip7() throws IOException {
        runSimulation("nav_trip7", 183, 208);
    }

    /**
     * Started Stop: Gunn Hwy @ Premier North Dr
     * Destination Stop: Bush Blvd @ Armenia Av
     * Recorded In: Car following Bus (Route 39) 3 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip7C() throws IOException {
        runSimulation("nav_trip7c", 172, 285);
    }

    /**
     * Started Stop: Alumni Dr @ Beard
     * Destination Stop: Magnolia Dr @ Alumni Dr
     * Recorded In: Route 5 (1 stop)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip8() throws IOException {
        runSimulation("nav_trip8", 0, 18);
    }

    /**
     * Started Stop: Alumni Dr @ Beard
     * Destination Stop: Magnolia Dr @ Alumni Dr
     * Recorded In: Car following Route 5 (1 stop)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip8C() throws IOException {
        runSimulation("nav_trip8c", 0, 20);
    }

    /**
     * Started Stop: 40 St @ E Hamilton
     * Destination Stop: Mckinley Dr @ Bougainvillea Av
     * Recorded In: Bus Route 5 (10 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip9() throws IOException {
        runSimulation("nav_trip9", 1041, 1071);
    }

    /**
     * Started Stop: 40 St @ E Hamilton
     * Destination Stop: Mckinley Dr @ Bougainvillea Av
     * Recorded In: car following Route 5 (10 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip9C() throws IOException {
        runSimulation("nav_trip9c", 570, 634);
    }

    /**
     * Started Stop: Mckinley Dr @ Bougainvillea Av
     * Destination Stop: 40 St @ Miller Ave
     * Recorded In: Bus Route 5 (6 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip10() throws IOException {
        runSimulation("nav_trip10", 589, 605);
    }

    /**
     * Started Stop: Mckinley Dr @ Bougainvillea Av
     * Destination Stop: 40 St @ Miller Ave
     * Recorded In: car following the bus Route 5 (6 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip10C() throws IOException {
        runSimulation("nav_trip10c", 484, 509);
    }

    /**
     * Started Stop: 22nd St @ Okara Rd
     * Destination Stop: 22nd St @ 111th Av
     * Recorded In: Bus route 12 (4 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip11() throws IOException {
        runSimulation("nav_trip11", 331, 371);
    }

    /**
     * Started Stop: 22nd St @ Okara Rd
     * Destination Stop: 22nd St @ 111th Av
     * Recorded In: Car following bus route 12 (4 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip11C() throws IOException {
        runSimulation("nav_trip11c", 119, 158);
    }

    /**
     * Started Stop: Bruce B Downs Bl @ Lakeside Commons
     * Destination Stop: Fletcher Av @Usf Banyan Cir
     * Recorded In:  Bus route 18 (2 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip12() throws IOException {
        runSimulation("nav_trip12", 69, 199);
    }

    /**
     * Started Stop: Bruce B Downs Bl @ Lakeside Commons
     * Destination Stop: Fletcher Av @Usf Banyan Cir
     * Recorded In: Car following bus route 18 (2 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip12C() throws IOException {
        runSimulation("nav_trip12c", 880, 901);
    }

    /**
     * Started Stop: Busch Blvd @ 40thSt
     * Destination Stop: Busch Blvd @ 50th St
     * Recorded In: Bus route 39 (5 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip13() throws IOException {
        runSimulation("nav_trip13", 2549, 2732);
    }

    /**
     * Started Stop: Busch Blvd @ 40thSt
     * Destination Stop: Busch Blvd @ 50th St
     * Recorded In: Bus route 39 (5 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip13C() throws IOException {
        runSimulation("nav_trip13c", 230, 519);
    }

    /**
     * Started Stop: Busch Blvd @ 52nd St
     * Destination Stop: Busch Blvd @ 22nd St
     * Recorded In: Bus route 39 (12 stops)
     * Device Used: Kyocera
     * FIXME - This test is failing because the lat/long for the 2nd-to-last stop is wrong - it's
     * not where the actual physical stop is located. Hence, it appears that the pull the cord alert
     * is triggered to soon.
     */
    @Test
    public void testTrip14() throws IOException {
        runSimulation("nav_trip14", 548, 571);
    }

    /**
     * Started Stop: Busch Blvd @ 52nd St
     * Destination Stop: Busch Blvd @ 22nd St
     * Recorded In: Car following bus route 39 (12 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip14C() throws IOException {
        runSimulation("nav_trip14c", 387, 429);
    }

    /**
     * Started Stop: Busch Blvd @ 12 St
     * Destination Stop: Busch Blvd @ 33 Rd St
     * Recorded In: Bus route 39 7 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip15() throws IOException {
        runSimulation("nav_trip15", 225, 417);
    }

    /**
     * Started Stop: Busch Blvd @ 12 St
     * Destination Stop: Busch Blvd @ 33 Rd St
     * Recorded In: Car following bus route 39
     * Device Used: Kyocera
     */
    @Test
    public void testTrip15C() throws IOException {
        runSimulation("nav_trip15c", 217, 287);
    }

    /**
     * Started Stop: Library LIB
     * Destination Stop: Magnolia apartments
     * Recorded In: Bull Runner route D
     * Device Used: Kyocera
     * FIXME - this test is failing with "Get ready triggered too soon"
     */
    @Test
    public void testTrip16() throws IOException {
        runSimulation("nav_trip16", 142, 192);
    }

    /**
     * Started Stop: Library LIB
     * Destination Stop: Magnolia apartments
     * Recorded In: Car following Bull Runner route D
     * Device Used: Kyocera
     */
    @Test
    public void testTrip16C() throws IOException {
        runSimulation("nav_trip16c", 121, 168);
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Holly at Magnolia Dr
     * Recorded In: Bull Runner route D
     * Device Used: Kyocera
     */
    @Test
    public void testTrip17() throws IOException {
        runSimulation("nav_trip17", 100, 181);
    }

    /**
     * Started Stop: Stop 504
     * Destination Stop: Library LIB
     * Recorded In: Bull Runner route F
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip18() throws IOException {
        runSimulation("nav_trip18", 31, 51);
    }

    /**
     * Started Stop: Stop 504
     * Destination Stop: Library LIB
     * Recorded In: car following Bull Runner route F
     * Device Used: Kyocera
     */
    @Test
    public void testTrip18C() throws IOException {
        runSimulation("nav_trip18c", 31, 58);
    }

    /**
     * Started Stop: Social Science
     * Destination Stop: Library LIB
     * Recorded In: Car following Bull Runner route C
     * Device Used: Kyocera
     */
    @Test
    public void testTrip20() throws IOException {
        runSimulation("nav_trip20", 133, 195);
    }

    /**
     * Started Stop: Social Science
     * Destination Stop: Library LIB
     * Recorded In: Car following Bull Runner route C
     * Device Used: Kyocera
     */
    @Test
    public void testTrip20C() throws IOException {
        runSimulation("nav_trip20c", 53, 117);
    }

    /**
     * Started Stop: Math and Engineering
     * Destination Stop: Epsilon Hall
     * Recorded In: Bull Runner route C
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip21() throws IOException {
        runSimulation("nav_trip21", 547, 686);
    }

    /**
     * Started Stop: Math and Engineering
     * Destination Stop: Epsilon Hall
     * Recorded In: Car following Bull Runner route C
     * Device Used: Kyocera
     */
    @Test
    public void testTrip21C() throws IOException {
        runSimulation("nav_trip21c", 323, 402);
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Center of Transportation Research
     * Recorded In: Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip22() throws IOException {
        runSimulation("nav_trip22", 1085, 1198);
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Center of Transportation Research
     * Recorded In: Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip22C() throws IOException {
        runSimulation("nav_trip22c", 1032, 1099);
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Holly Mail Room
     * Recorded In: Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip23() throws IOException {
        runSimulation("nav_trip23", 0, 24);
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Holly Mail Room
     * Recorded In: Car following Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip23C() throws IOException {
        runSimulation("nav_trip23c", 0, 19);
    }

    /**
     * Started Stop: USF library LIB
     * Destination Stop: Patel Center
     * Recorded In: Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip24() throws IOException {
        runSimulation("nav_trip24", 115, 178);
    }

    /**
     * Started Stop: USF library LIB
     * Destination Stop: Patel Center
     * Recorded In: Car following the Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip24C() throws IOException {
         runSimulation("nav_trip24c", 203, 262);
    }

    /**
     * Started Stop: Baseball Field
     * Destination Stop: Greek Housing
     * Recorded In: Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip25() throws IOException {
        // Read test CSV
        runSimulation("nav_trip25", 209, 235);
    }

    /**
     * Started Stop: Baseball Field
     * Destination Stop: Greek Housing
     * Recorded In: Car following Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip25C() throws IOException {
        runSimulation("nav_trip25c", 217, 247);
    }

    /**
     * Started Stop: Fletcher Avenue @ 46th Street
     * Destination Stop: University Area Transit Center
     * Recorded In: Route 6
     * Device Used: ZTE Z831
     */
    @Test
    public void testTrip26() throws IOException {
        // Read test CSV
        runSimulation("nav_trip26", 367, 463);
    }

    /**
     * Started Stop: University Area Transit Center
     * Destination Stop: Fletcher Avenue @ Palm Drive MetroRapid
     * Recorded In: Route 6
     * Device Used: ZTE Z831
     */
    @Test
    public void testTrip27() throws IOException {
        // Read test CSV
        runSimulation("nav_trip27", 909, 944);
    }

    /**
     * Started Stop: Fletcher Avenue @ Palm Drive MetroRapid
     * Destination Stop: 56th Street @ 131st Avenue (S)
     * Recorded In: Route 6
     * Device Used: ZTE Z831
     */
    @Test
    public void testTrip28() throws IOException {
        // Read test CSV
        runSimulation("nav_trip28", 400, 473);
    }

    /**
     * Started Stop: 56th Street @ 131st Avenue (N)
     * Destination Stop: Fletcher Avenue @ 42nd Street MetroRapid
     * Recorded In: Route 6
     * Device Used: ZTE Z831
     */
    @Test
    public void testTrip29() throws IOException {
        // Read test CSV
        runSimulation("nav_trip29", 472, 594);
    }

    /**
     * Started Stop: Fletcher Avenue @ 42nd Street MetroRapid
     * Destination Stop: University Area Transit Center
     * Recorded In: Route 6
     * Device Used: One Plus 6
     */
    @Test
    public void testTrip30() throws IOException {
        // Read test CSV
        runSimulation("nav_trip30", 1001, 1048);
    }

    /**
     * Started Stop: University Area Transit Center
     * Destination Stop: 15th St @ 127 avenue
     * Recorded In: Route 42
     * Device Used: One Plus 6
     */
    @Test
    public void testTrip31() throws IOException {
        // Read test CSV
        runSimulation("nav_trip31", 2508, 2654);
    }

    /**
     * Started Stop: 15th St @127 avenue
     * Destination Stop: Bruce B Downs Bl @ Lakeside Commons
     * Recorded In: Route 42
     * Device Used: ZTE Z831
     */

    @Test
    public void testTrip32() throws IOException {
        // Read test CSV
        runSimulation("nav_trip32", 1847, 1888);
    }

    /**
     * Started Stop: Bruce B Downs Bl @ Lakeside Commons
     * Destination Stop: University Area Transit Center
     * Recorded In: Route 42
     * Device Used: ZTE Z831
     */
    @Test
    public void testTrip33() throws IOException {
        // Read test CSV
        runSimulation("nav_trip33", 335, 401);
    }

    /**
     * Runs the simulation with the provided CSV data and expected "Get ready" and "Pull the Cord Now" notification indexes
     *
     * @param csvFileName file name of the CSV file to load from the raw resources directory that contains the test location data from a user's trip
     * @param expectedGetReadyIndex the index when the "Get Ready" notification is expected
     * @param expectedPullCordIndex the index for when the "Pull the Cord Now" notification is expected
     */
    private void runSimulation(String csvFileName, int expectedGetReadyIndex, int expectedPullCordIndex) throws IOException {
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri(csvFileName));
        String csv = IOUtils.toString(reader);
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation(expectedGetReadyIndex, expectedPullCordIndex);
    }

    // Class for holding relevant details for testing.
    class NavigationSimulation {

        String mTripId;

        String mDestinationId;

        String mBeforeId;

        Location mDestinationLocation;

        Location mSecondToLastLocation;

        Location[] mLocations;

        long[] mTimes;

        // Index algorithmically identifed as a "good" location for triggering the "Get Ready" alert (vs. a user manually specifying the index that's hard coded into our tests - TODO Actually use this in assertions
        int mAutomatedGetReadyIndex = -1;

        boolean useElapsedNanos = false;           // Should use elapsed nanos instead of time.

        /**
         * Loads recorded user location data into the simulation for testing navigation.  Recorded user
         * data simulates a real-time trip.
         *
         * @param csv string with the above format
         */
        NavigationSimulation(String csv) {
            String[] lines = csv.split("\n");

            // Setup meta data.
            String[] details = lines[0].split(",");
            mTripId = details[0];
            mDestinationId = details[1];
            mBeforeId = details[4];

            mDestinationLocation = new Location(LocationManager.GPS_PROVIDER);
            mDestinationLocation.setLatitude(Double.parseDouble(details[2]));
            mDestinationLocation.setLongitude(Double.parseDouble(details[3]));

            mSecondToLastLocation = new Location(LocationManager.GPS_PROVIDER);
            mSecondToLastLocation.setLatitude(Double.parseDouble(details[5]));
            mSecondToLastLocation.setLongitude(Double.parseDouble(details[6]));

            mLocations = new Location[lines.length - 1];

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

                mLocations[i - 1] = new Location(provider);

                // Check if we can use elapsed nano seconds. Else, we'll use time.
                if (!nanosStr.equals("")
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    useElapsedNanos = true;
                    mLocations[i - 1].setElapsedRealtimeNanos(Long.parseLong(nanosStr));
                }

                mLocations[i - 1].setTime(time);
                mLocations[i - 1].setLatitude(lat);
                mLocations[i - 1].setLongitude(lng);
                mLocations[i - 1].setAltitude(altitude);
                mLocations[i - 1].setBearing(bearing);
                mLocations[i - 1].setAccuracy(accuracy);
                mLocations[i - 1].setSpeed(speed);
            }

            // Compute index of point nearest to second to last stop.
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < mLocations.length; i++) {
                if (mLocations[i].distanceTo(mSecondToLastLocation) < minDist) {
                    minDist = mLocations[i].distanceTo(mSecondToLastLocation);
                    mAutomatedGetReadyIndex = i;
                }
            }

            // Compute time differences between readings in ms, if realtime ns is available, use it
            // else use getTime.
            mTimes = new long[mLocations.length];
            if (useElapsedNanos && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                for (int i = 1; i < mLocations.length; i++) {
                    mTimes[i] = (mLocations[i].getElapsedRealtimeNanos() - mLocations[i - 1]
                            .getElapsedRealtimeNanos());
                    mTimes[i] /= 1000000;
                }
            } else {
                for (int i = 1; i < mLocations.length; i++) {
                    mTimes[i] = mLocations[i].getTime() - mLocations[i - 1].getTime();
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

        public Location getSecondToLastLocation() {
            return mSecondToLastLocation;
        }

        public Location[] getLocations() {
            return mLocations;
        }

        public long[] getTimes() {
            return mTimes;
        }

        public int getAutomatedGetReadyIndex() {
            return mAutomatedGetReadyIndex;
        }

        /**
         * Runs the simulation on the data provided in the constructor using the provided expected values
         * for the index when the "Get Ready" notification is expected and the index for when the "Pull
         * the Cord Now" notification is expected
         * @param expectedGetReadyIndex the index when the "Get Ready" notification is expected
         * @param expectedPullCordIndex the index for when the "Pull the Cord Now" notification is expected
         */
        void runSimulation(int expectedGetReadyIndex, int expectedPullCordIndex) {
            NavigationServiceProvider provider = new NavigationServiceProvider(mTripId,
                    mDestinationId);
            Location prevLocation = null;
            // Use the first location time as the starting time for this PathLink
            // TODO - capture PathLink nav starting time in logs
            PathLink link = new PathLink(mLocations[0].getTime(), null, mSecondToLastLocation, mDestinationLocation, mTripId);

            // Begin navigation & simulation for a single path link
            provider.navigate(new Path(new ArrayList<>(Collections.singletonList(link))));

            for (int i = 0; i <= expectedGetReadyIndex; i++) {
                Location l = mLocations[i];

                try {
                    Thread.sleep(mTimes[i] / SPEED_UP);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }

                // Code added to check for duplicate locations in the .csv log files
                if (prevLocation == null || !LocationUtils.isDuplicate(prevLocation, l)) {
                    provider.locationUpdated(l);
                }
                prevLocation = l;

                if (provider.getGetReady() && i < expectedGetReadyIndex) {
                    fail("Get ready triggered too soon");
                }

                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()),
                        Boolean.toString(provider.getFinished())));

            }

            Boolean check1 = provider.getGetReady() && !provider.getFinished();
            assertTrue(check1);

            if(expectedGetReadyIndex != 0){
                prevLocation = mLocations[expectedGetReadyIndex - 1];
            }

            for (int i = expectedGetReadyIndex; i <= expectedPullCordIndex; i++) {
                Location l = mLocations[i];
                try {
                    Thread.sleep((mTimes[i] / SPEED_UP));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }

                // Code added to check for duplicate locations in the .csv log files
                if (prevLocation == null || !LocationUtils.isDuplicate(prevLocation, l)) {
                    provider.locationUpdated(l);
                }
                prevLocation = l;

                if (provider.getFinished() && i < expectedPullCordIndex) {
                    fail("Pull the Cord triggered too soon");
                }

                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()),
                        Boolean.toString(provider.getFinished())));
            }

            Boolean check2 = provider.getGetReady() && provider.getFinished();
            assertTrue(check2);
        }
    }
}
