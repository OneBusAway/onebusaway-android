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

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;

import static android.support.test.InstrumentationRegistry.getTargetContext;
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

    private int mGetReadyId;

    private int mPullCordId;

    /**
     * Started Stop: Mckinley Dr @ DOT Bldg
     * Destination Stop: University Area Transit Center
     * Recorded In: Bus (Route 5) 9 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip1() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip1"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 848;
        mPullCordId = 978;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Mckinley Dr @ DOT Bldg
     * Destination Stop: University Area Transit Center
     * Recorded In: car following Bus (Route 5) 9 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip1C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip1c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 665;
        mPullCordId = 929;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Alumni Dr @ Leroy Collins @ Eng Bldg
     * Destination Stop: Mckinley Dr @ Fowler Ave
     * Recorded In: Bus Route 5 - 1 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip2() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip2"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 0;
        mPullCordId = 14;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Alumni Dr @ Leroy Collins @ Eng Bldg
     * Destination Stop: Mckinley Dr @ Fowler Ave
     * Recorded In: Car following Route 5 - 1 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip2C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip2c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 0;
        mPullCordId = 64;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Dale Mabry Hwy @ Linebaugh Av
     * Destination Stop: Dale Mabry Hwy @ Hudson Ln @ Taco Bell
     * Recorded In: Bus (Route 36) 4 stops
     * Device Used: Nexus 5
     * FIXME - Currently fails with "Pull the cord triggered too soon"
     */
    @Test
    public void testTrip3() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip3"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 95;
        mPullCordId = 115;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Dale Mabry Hwy @ Linebaugh Av
     * Destination Stop: Dale Mabry Hwy @ Hudson Ln @ Taco Bell
     * Recorded In: car following Bus (Route 36) 4 stops
     * Device Used: Nexus 5
     * FIXME - Currently fails with "Pull the cord triggered too soon"
     */
    @Test
    public void testTrip3C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip3c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 87;
        mPullCordId = 109;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
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
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip4"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 294;
        mPullCordId = 329;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
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
        // Read test CSV.
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip4c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 253;
        mPullCordId = 329;

        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Linebaugh @ Henderson Av
     * Destination Stop: Anderson Rd @ 8110
     * Recorded In: Bus (Route 7) 4 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip5() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip5"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 372;
        mPullCordId = 660;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Linebaugh @ Henderson Av
     * Destination Stop: Anderson Rd @ 8110
     * Recorded In: Car following Bus (Route 7) 4 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip5C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip5c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 284;
        mPullCordId = 492;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Himes Av @ Colwell Av
     * Destination Stop: Himes Av @ Hillsborough Av
     * Recorded In: Bus (Route 36) 12 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip6() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip6"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 801;
        mPullCordId = 837;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Himes Av @ Colwell Av
     * Destination Stop: Himes Av @ Hillsborough Av
     * Recorded In: car following Bus (Route 36) 12 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip6C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip6c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 676;
        mPullCordId = 704;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Gunn Hwy @ Premier North Dr
     * Destination Stop: Bush Blvd @ Armenia Av
     * Recorded In: Bus (Route 39) 3 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip7() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip7"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 183;
        mPullCordId = 208;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Gunn Hwy @ Premier North Dr
     * Destination Stop: Bush Blvd @ Armenia Av
     * Recorded In: Car following Bus (Route 39) 3 stops
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip7C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip7c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 172;
        mPullCordId = 285;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Alumni Dr @ Beard
     * Destination Stop: Magnolia Dr @ Alumni Dr
     * Recorded In: Route 5 (1 stop)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip8() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip8"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 0;
        mPullCordId = 18;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Alumni Dr @ Beard
     * Destination Stop: Magnolia Dr @ Alumni Dr
     * Recorded In: Car following Route 5 (1 stop)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip8C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip8c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 0;
        mPullCordId = 20;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: 40 St @ E Hamilton
     * Destination Stop: Mckinley Dr @ Bougainvillea Av
     * Recorded In: Bus Route 5 (10 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip9() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip9"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 1041;
        mPullCordId = 1071;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: 40 St @ E Hamilton
     * Destination Stop: Mckinley Dr @ Bougainvillea Av
     * Recorded In: car following Route 5 (10 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip9C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip9c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 570;
        mPullCordId = 634;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Mckinley Dr @ Bougainvillea Av
     * Destination Stop: 40 St @ Miller Ave
     * Recorded In: Bus Route 5 (6 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip10() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip10"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 589;
        mPullCordId = 605;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Mckinley Dr @ Bougainvillea Av
     * Destination Stop: 40 St @ Miller Ave
     * Recorded In: car following the bus Route 5 (6 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip10C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip10c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 484;
        mPullCordId = 509;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: 22nd St @ Okara Rd
     * Destination Stop: 22nd St @ 111th Av
     * Recorded In: Bus route 12 (4 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip11() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip11"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 331;
        mPullCordId = 371;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: 22nd St @ Okara Rd
     * Destination Stop: 22nd St @ 111th Av
     * Recorded In: Car following bus route 12 (4 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip11C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip11c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 119;
        mPullCordId = 158;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Bruce B Downs Bl @ Lakeside Commons
     * Destination Stop: Fletcher Av @Usf Banyan Cir
     * Recorded In:  Bus route 18 (2 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip12() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip12"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 69;
        mPullCordId = 199;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Bruce B Downs Bl @ Lakeside Commons
     * Destination Stop: Fletcher Av @Usf Banyan Cir
     * Recorded In: Car following bus route 18 (2 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip12C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip12c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 880;
        mPullCordId = 901;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Busch Blvd @ 40thSt
     * Destination Stop: Busch Blvd @ 50th St
     * Recorded In: Bus route 39 (5 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip13() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip13"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 2549;
        mPullCordId = 2732;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Busch Blvd @ 40thSt
     * Destination Stop: Busch Blvd @ 50th St
     * Recorded In: Bus route 39 (5 stops)
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip13C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip13c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 230;
        mPullCordId = 519;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
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
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip14"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 548;
        mPullCordId = 571;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Busch Blvd @ 52nd St
     * Destination Stop: Busch Blvd @ 22nd St
     * Recorded In: Car following bus route 39 (12 stops)
     * Device Used: Kyocera
     */
    @Test
    public void testTrip14C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip14c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 387;
        mPullCordId = 429;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Busch Blvd @ 12 St
     * Destination Stop: Busch Blvd @ 33 Rd St
     * Recorded In: Bus route 39 7 stops
     * Device Used: Kyocera
     */
    @Test
    public void testTrip15() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip15"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 225;
        mPullCordId = 417;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Busch Blvd @ 12 St
     * Destination Stop: Busch Blvd @ 33 Rd St
     * Recorded In: Car following bus route 39
     * Device Used: Kyocera
     */
    @Test
    public void testTrip15C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip15c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 217;
        mPullCordId = 287;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
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
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip16"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 142;
        mPullCordId = 192;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Library LIB
     * Destination Stop: Magnolia apartments
     * Recorded In: Car following Bull Runner route D
     * Device Used: Kyocera
     */
    @Test
    public void testTrip16C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip16c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 121;
        mPullCordId = 168;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Holly at Magnolia Dr
     * Recorded In: Bull Runner route D
     * Device Used: Kyocera
     */
    @Test
    public void testTrip17() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip17"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 100;
        mPullCordId = 181;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Stop 504
     * Destination Stop: Library LIB
     * Recorded In: Bull Runner route F
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip18() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip18"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 31;
        mPullCordId = 50;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Stop 504
     * Destination Stop: Library LIB
     * Recorded In: car following Bull Runner route F
     * Device Used: Kyocera
     */
    @Test
    public void testTrip18C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip18c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 31;
        mPullCordId = 58;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Social Science
     * Destination Stop: Library LIB
     * Recorded In: Car following Bull Runner route C
     * Device Used: Kyocera
     */
    @Test
    public void testTrip20() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip20"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 133;
        mPullCordId = 195;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Social Science
     * Destination Stop: Library LIB
     * Recorded In: Car following Bull Runner route C
     * Device Used: Kyocera
     */
    @Test
    public void testTrip20C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip20c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 53;
        mPullCordId = 117;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Math and Engineering
     * Destination Stop: Epsilon Hall
     * Recorded In: Bull Runner route C
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip21() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip21"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 547;
        mPullCordId = 686;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Math and Engineering
     * Destination Stop: Epsilon Hall
     * Recorded In: Car following Bull Runner route C
     * Device Used: Kyocera
     */
    @Test
    public void testTrip21C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip21c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 323;
        mPullCordId = 402;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Center of Transportation Research
     * Recorded In: Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip22() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip22"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 1085;
        mPullCordId = 1198;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Center of Transportation Research
     * Recorded In: Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip22C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip22c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 1032;
        mPullCordId = 1099;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Holly Mail Room
     * Recorded In: Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip23() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip23"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 0;
        mPullCordId = 24;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Marshall Student Center
     * Destination Stop: Holly Mail Room
     * Recorded In: Car following Bull Runner route E
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip23C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip23c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 0;
        mPullCordId = 18;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: USF library LIB
     * Destination Stop: Patel Center
     * Recorded In: Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip24() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip24"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 115;
        mPullCordId = 178;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: USF library LIB
     * Destination Stop: Patel Center
     * Recorded In: Car following the Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip24C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip24c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 203;
        mPullCordId = 262;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
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
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip25"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 209;
        mPullCordId = 235;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
    }

    /**
     * Started Stop: Baseball Field
     * Destination Stop: Greek Housing
     * Recorded In: Car following Bull Runner route A
     * Device Used: Nexus 5
     */
    @Test
    public void testTrip25C() throws IOException {
        // Read test CSV
        Reader reader = Resources.read(getTargetContext(), Resources.getTestUri("nav_trip25c"));
        String csv = IOUtils.toString(reader);
        mGetReadyId = 217;
        mPullCordId = 247;
        NavigationSimulation trip = new NavigationSimulation(csv);
        trip.runSimulation();
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

        int getReadyIndex = -1;                    //  Index which getReady should be triggered.

        int finishedIndex = -1;                    //  Index which finished should be triggered.

        boolean useElapsedNanos = false;           // Should use elapsed nanos instead of time.

        /**
         * Takes a csv string with the first row containing meta-data in the format
         * of tripId,DestinationId,dest-lat,dest-lng,beforeDestinationId,before-lat,before-lng
         * and all following rows holding data to construct location points of a trip in the format
         * time,lat,lng,altitude,speed,bearing,provider.
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
                    getReadyIndex = i;
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

        public int getGetReadyIndex() {
            return getReadyIndex;
        }

        public int getFinishedIndex() {
            return finishedIndex;
        }

        void runSimulation() {
            NavigationServiceProvider provider = new NavigationServiceProvider(mTripId,
                    mDestinationId);

            // Construct Destination & Second-To-Last Location
            PathLink link = new PathLink(null, mSecondToLastLocation, mDestinationLocation);

            // Begin navigation & simulation for a single path link
            provider.navigate(new Path(new ArrayList<>(Arrays.asList(link))));

            for (int i = 0; i <= mGetReadyId; i++) {
                Location l = mLocations[i];

                try {
                    Thread.sleep(mTimes[i] / SPEED_UP);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                provider.locationUpdated(l);

                if (provider.getGetReady() && i < mGetReadyId) {
                    fail("Get ready triggered too soon");
                }

                Log.d(TAG, String.format("%d: (%f, %f, %f)\tR:%s  F:%s", i,
                        l.getLatitude(), l.getLongitude(), l.getSpeed(),
                        Boolean.toString(provider.getGetReady()),
                        Boolean.toString(provider.getFinished())));

            }

            Boolean check1 = provider.getGetReady() && !provider.getFinished();
            assertTrue(check1);

            for (int i = mGetReadyId; i <= mPullCordId; i++) {
                Location l = mLocations[i];
                try {
                    Thread.sleep((mTimes[i] / SPEED_UP));
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                provider.locationUpdated(l);

                if (provider.getFinished() && i < mPullCordId) {
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
