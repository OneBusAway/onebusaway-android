/*
 * Copyright (C) 2015-2017 Paul Watts,
 * University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.util.test;

import androidx.core.util.Pair;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;
import org.onebusaway.android.app.di.PreferencesEntryPoint;
import org.onebusaway.android.app.di.RegionEntryPoint;
import org.onebusaway.android.api.contract.ArrivalsForStop;
import org.onebusaway.android.api.contract.EntryWithReferences;
import org.onebusaway.android.api.contract.ObaEnvelope;
import org.onebusaway.android.models.ObaSituation;
import org.onebusaway.android.region.Region;
import org.onebusaway.android.api.test.ObaTestCase;
import org.onebusaway.android.mock.ArrivalsFixtures;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.ui.arrivals.ArrivalInfo;
import org.onebusaway.android.ui.arrivals.dialogs.StopDetailsDialog;
import org.onebusaway.android.util.ArrivalInfoUtils;
import org.onebusaway.android.util.DisplayFormat;
import org.onebusaway.android.util.MyTextUtils;
import org.onebusaway.android.util.RouteDisplay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

/**
 * Tests to evaluate utility methods related to the presentation of information to the user in the
 * UI
 */
@RunWith(AndroidJUnit4.class)
public class UIUtilTest extends ObaTestCase {

    @Test
    public void testFormatRouteDisplayNames() {
        String formattedString;
        ArrayList<String> routes = new ArrayList<String>();
        ArrayList<String> highlightedRoutes = new ArrayList<String>();

        routes.add("1");
        routes.add("5");
        formattedString = RouteDisplay.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5", formattedString);

        routes.clear();
        routes.add("5");
        routes.add("1");
        formattedString = RouteDisplay.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5", formattedString);

        routes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        formattedString = RouteDisplay.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5, 8a, 8b, 15", formattedString);

        // Test highlighting one URL
        routes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        highlightedRoutes.add("1");
        formattedString = RouteDisplay.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1*, 5, 8a, 8b, 15", formattedString);

        // Test highlighting several URLs
        routes.clear();
        highlightedRoutes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        highlightedRoutes.add("1");
        highlightedRoutes.add("8b");
        highlightedRoutes.add("15");
        formattedString = RouteDisplay.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1*, 5, 8a, 8b*, 15*", formattedString);
    }

    /**
     * Tests formatting of stop names, trip headsigns, and route names
     */
    @Test
    public void testFormatDisplayText() {
        // Stop names
        assertEquals("SDSU Transit Center", MyTextUtils.formatDisplayText("SDSU Transit Center"));
        assertEquals("VA Hospital", MyTextUtils.formatDisplayText("VA Hospital"));
        assertEquals("SDSU", MyTextUtils.formatDisplayText("SDSU"));
        assertEquals("UTC Transit Center", MyTextUtils.formatDisplayText("UTC Transit Center"));
        // See #883
        assertEquals("SPLC / SR 513", MyTextUtils.formatDisplayText("SPLC / SR 513"));

        // Trip headsigns
        assertEquals("North to University Area TC",
                MyTextUtils.formatDisplayText("North to University Area TC"));
        assertEquals("North To University Area Tc",
                MyTextUtils.formatDisplayText("NORTH TO UNIVERSITY AREA TC"));
        assertEquals("SDSU", MyTextUtils.formatDisplayText("SDSU"));
        // See #883
        assertEquals("Hospital via SPLC Parking", MyTextUtils.formatDisplayText("Hospital via SPLC Parking"));
        assertEquals("SPLC Parking via 70th", MyTextUtils.formatDisplayText("SPLC Parking via 70th"));

        // Route names
        assertEquals("Downtown San Diego - UTC via Old Town",
                MyTextUtils.formatDisplayText("Downtown San Diego - UTC via Old Town"));
        assertEquals("UTC/VA Med CTR Express",
                MyTextUtils.formatDisplayText("UTC/VA Med CTR Express"));
    }

    @Test
    public void testCreateStopDetailsDialogText() {
        final String newLine = "\n";
        Pair stopDetails;
        StringBuilder expectedMessage;

        // Stop details
        String stopName = "University Area Transit Center";
        String stopUserName = null;
        String stopCode = "6497";
        String stopDirection = null;
        List<String> routeDisplayNames = new ArrayList<>();
        routeDisplayNames.add("5");

        // Test without stop nickname or direction
        stopDetails = StopDetailsDialog.createStopDetailsDialogText(getTargetContext(),
                stopName,
                stopUserName,
                stopCode,
                stopDirection,
                routeDisplayNames);
        assertEquals(stopName, (String) stopDetails.first);
        expectedMessage = new StringBuilder();
        expectedMessage.append("Stop # " + stopCode);
        expectedMessage.append(newLine);
        expectedMessage.append("Routes: " + routeDisplayNames.get(0));
        assertEquals(expectedMessage.toString(), (String) stopDetails.second);

        // Test with stop nickname and without direction
        stopUserName = "My stop nickname";
        stopDetails = StopDetailsDialog.createStopDetailsDialogText(getTargetContext(),
                stopName,
                stopUserName,
                stopCode,
                stopDirection,
                routeDisplayNames);
        assertEquals(stopUserName, (String) stopDetails.first);
        expectedMessage = new StringBuilder();
        expectedMessage.append("Official name: " + stopName);
        expectedMessage.append(newLine);
        expectedMessage.append("Stop # " + stopCode);
        expectedMessage.append(newLine);
        expectedMessage.append("Routes: " + routeDisplayNames.get(0));
        assertEquals(expectedMessage.toString(), (String) stopDetails.second);

        // Test without stop nickname and with direction
        stopUserName = null;
        stopDirection = "S";
        stopDetails = StopDetailsDialog.createStopDetailsDialogText(getTargetContext(),
                stopName,
                stopUserName,
                stopCode,
                stopDirection,
                routeDisplayNames);
        assertEquals(stopName, (String) stopDetails.first);
        expectedMessage = new StringBuilder();
        expectedMessage.append("Stop # " + stopCode);
        expectedMessage.append(newLine);
        expectedMessage.append("Routes: " + routeDisplayNames.get(0));
        expectedMessage.append(newLine);
        expectedMessage.append(getTargetContext().getString(DisplayFormat.getStopDirectionText(stopDirection)));
        assertEquals(expectedMessage.toString(), (String) stopDetails.second);

        // Test with stop nickname and direction
        stopUserName = "My stop nickname";
        stopDirection = "S";
        stopDetails = StopDetailsDialog.createStopDetailsDialogText(getTargetContext(),
                stopName,
                stopUserName,
                stopCode,
                stopDirection,
                routeDisplayNames);
        assertEquals(stopUserName, (String) stopDetails.first);
        expectedMessage = new StringBuilder();
        expectedMessage.append("Official name: " + stopName);
        expectedMessage.append(newLine);
        expectedMessage.append("Stop # " + stopCode);
        expectedMessage.append(newLine);
        expectedMessage.append("Routes: " + routeDisplayNames.get(0));
        expectedMessage.append(newLine);
        expectedMessage.append(getTargetContext().getString(DisplayFormat.getStopDirectionText(stopDirection)));
        assertEquals(expectedMessage.toString(), (String) stopDetails.second);
    }

    @Test
    public void testArrivalTimeIndexSearch() throws Exception {
        // Load a captured arrivals fixture and project it via the production DTO path
        Region tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        RegionEntryPoint.get(getTargetContext()).applyRegion(tampa, true);

        ObaEnvelope<EntryWithReferences<ArrivalsForStop>> env =
                ArrivalsFixtures.load(getTargetContext(), "arrivals_and_departures_for_stop_hart_6497");
        String stopId = "Hillsborough Area Regional Transit_6497";

        // The two favorited route/headsign combos. Favorite state is supplied to convert() directly
        // now (the favorites store is no longer a ContentProvider); the favorite precedence itself is
        // unit-tested in RouteHeadsignFavoriteLogicTest.
        java.util.Set<String> favorites = new java.util.HashSet<>(java.util.Arrays.asList(
                "Hillsborough Area Regional Transit_6|North to University Area TC",
                "Hillsborough Area Regional Transit_6|South to Downtown/MTC"));

        // Project the fixture's arrivals via the production path, with those two marked favorite.
        ArrayList<ArrivalInfo> arrivalInfo = ArrivalsFixtures.convert(getTargetContext(), env, true,
                (routeId, headsign, sid) -> favorites.contains(routeId + "|" + headsign));

        // Now confirm that we have the correct number of elements, and values for ETAs for the test
        validateUatcArrivalInfo(arrivalInfo);

        // First non-negative arrival should be "0", in index 5
        int firstNonNegativeArrivalIndex = ArrivalInfoUtils.findFirstNonNegativeArrival(arrivalInfo);
        assertEquals(5, firstNonNegativeArrivalIndex);

        ArrayList<Integer> preferredArrivalIndexes = ArrivalInfoUtils
                .findPreferredArrivalIndexes(arrivalInfo);

        // Indexes 11 and 13 should hold the favorites
        assertEquals(11, preferredArrivalIndexes.get(0).intValue());
        assertEquals(13, preferredArrivalIndexes.get(1).intValue());

        // With no favorites, the first two non-negative arrival times are returned - indexes 5 and 6.
        arrivalInfo = ArrivalsFixtures.convert(getTargetContext(), env, true);
        preferredArrivalIndexes = ArrivalInfoUtils.findPreferredArrivalIndexes(arrivalInfo);
        assertEquals(5, preferredArrivalIndexes.get(0).intValue());
        assertEquals(6, preferredArrivalIndexes.get(1).intValue());
    }

    /**
     * Tests the status and time labels for arrival info
     */
    @Test
    public void testArrivalInfoLabels() throws Exception {
        // Load a captured arrivals fixture and project it via the production DTO path
        Region tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        RegionEntryPoint.get(getTargetContext()).applyRegion(tampa, true);

        ObaEnvelope<EntryWithReferences<ArrivalsForStop>> env =
                ArrivalsFixtures.load(getTargetContext(), "arrivals_and_departures_for_stop_hart_6497");

        /**
         * Labels *with* arrive/depart included, and time labels
         */
        ArrayList<ArrivalInfo> arrivalInfo = ArrivalsFixtures.convert(getTargetContext(), env, true);

        // Now confirm that we have the correct number of elements, and values for ETAs for the test
        validateUatcArrivalInfo(arrivalInfo);

        // Arrivals/departures that have already happened
        assertEquals("Arrived on time", arrivalInfo.get(0).getStatusText());
        assertEquals("Arrived at " + formatTime(1438804260000L),
                arrivalInfo.get(0).getTimeText());
        assertEquals("Departed 2 min late", arrivalInfo.get(1).getStatusText());
        assertEquals("Departed at " + formatTime(1438804320000L), arrivalInfo.get(1).getTimeText());
        assertEquals("Departed 1 min early", arrivalInfo.get(2).getStatusText());
        assertEquals("Departed at " + formatTime(1438804440000L), arrivalInfo.get(2).getTimeText());
        assertEquals("Arrived on time", arrivalInfo.get(3).getStatusText());
        assertEquals("Arrived at " + formatTime(1438804440000L), arrivalInfo.get(3).getTimeText());
        assertEquals("Departed 6 min early", arrivalInfo.get(4).getStatusText());
        assertEquals("Departed at " + formatTime(1438804440000L), arrivalInfo.get(4).getTimeText());
        // Arrivals and departures that will happen in the future
        assertEquals("On time", arrivalInfo.get(5).getStatusText());
        assertEquals("Departing at " + formatTime(1438804500000L),
                arrivalInfo.get(5).getTimeText());
        assertEquals("On time", arrivalInfo.get(6).getStatusText());
        assertEquals("Arriving at " + formatTime(1438804500000L), arrivalInfo.get(6).getTimeText());
        assertEquals("5 min delay", arrivalInfo.get(7).getStatusText());
        assertEquals("Arriving at " + formatTime(1438804680000L), arrivalInfo.get(7).getTimeText());
        assertEquals("On time", arrivalInfo.get(8).getStatusText());
        assertEquals("Departing at " + formatTime(1438804800000L),
                arrivalInfo.get(8).getTimeText());
        assertEquals("On time", arrivalInfo.get(9).getStatusText());
        assertEquals("Departing at " + formatTime(1438804800000L),
                arrivalInfo.get(9).getTimeText());
        assertEquals("10 min delay", arrivalInfo.get(10).getStatusText());
        assertEquals("Arriving at " + formatTime(1438804860000L),
                arrivalInfo.get(10).getTimeText());
        assertEquals("1 min early", arrivalInfo.get(11).getStatusText());
        assertEquals("Arriving at " + formatTime(1438804920000L),
                arrivalInfo.get(11).getTimeText());
        assertEquals("On time", arrivalInfo.get(12).getStatusText());
        assertEquals("Arriving at " + formatTime(1438805100000L),
                arrivalInfo.get(12).getTimeText());
        assertEquals("1 min early", arrivalInfo.get(13).getStatusText());
        assertEquals("Departing at " + formatTime(1438805340000L),
                arrivalInfo.get(13).getTimeText());
        assertEquals("1 min delay", arrivalInfo.get(14).getStatusText());
        assertEquals("Arriving at " + formatTime(1438805520000L),
                arrivalInfo.get(14).getTimeText());
        assertEquals("On time", arrivalInfo.get(15).getStatusText());
        assertEquals("Arriving at " + formatTime(1438805700000L),
                arrivalInfo.get(15).getTimeText());
        assertEquals("On time", arrivalInfo.get(16).getStatusText());
        assertEquals("Departing at " + formatTime(1438805700000L),
                arrivalInfo.get(16).getTimeText());
        assertEquals("4 min delay", arrivalInfo.get(17).getStatusText());
        assertEquals("Arriving at " + formatTime(1438805880000L),
                arrivalInfo.get(17).getTimeText());
        assertEquals("On time", arrivalInfo.get(18).getStatusText());
        assertEquals("Departing at " + formatTime(1438806000000L),
                arrivalInfo.get(18).getTimeText());
        assertEquals("8 min delay", arrivalInfo.get(19).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806060000L),
                arrivalInfo.get(19).getTimeText());
        assertEquals("2 min delay", arrivalInfo.get(20).getStatusText());
        assertEquals("Departing at " + formatTime(1438806124000L),
                arrivalInfo.get(20).getTimeText());
        assertEquals("3 min delay", arrivalInfo.get(21).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806180000L),
                arrivalInfo.get(21).getTimeText());
        assertEquals("On time", arrivalInfo.get(22).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806300000L),
                arrivalInfo.get(22).getTimeText());
        assertEquals("On time", arrivalInfo.get(23).getStatusText());
        assertEquals("Departing at " + formatTime(1438806300000L),
                arrivalInfo.get(23).getTimeText());
        assertEquals("6 min delay", arrivalInfo.get(24).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806420000L),
                arrivalInfo.get(24).getTimeText());
        assertEquals("3 min delay", arrivalInfo.get(25).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806420000L),
                arrivalInfo.get(25).getTimeText());
        assertEquals("6 min delay", arrivalInfo.get(26).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806540000L),
                arrivalInfo.get(26).getTimeText());
        assertEquals("On time", arrivalInfo.get(27).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806540000L),
                arrivalInfo.get(27).getTimeText());
        assertEquals("On time", arrivalInfo.get(28).getStatusText());
        assertEquals("Departing at " + formatTime(1438806600000L),
                arrivalInfo.get(28).getTimeText());
        assertEquals("On time", arrivalInfo.get(29).getStatusText());
        assertEquals("Departing at " + formatTime(1438806600000L),
                arrivalInfo.get(29).getTimeText());
        assertEquals("9 min delay", arrivalInfo.get(30).getStatusText());
        assertEquals("Arriving at " + formatTime(1438806600000L),
                arrivalInfo.get(30).getTimeText());
        assertEquals("On time", arrivalInfo.get(31).getStatusText());
        assertEquals("Departing at " + formatTime(1438806600000L),
                arrivalInfo.get(31).getTimeText());

        /**
         * Status labels *without* arrive/depart included
         */
        arrivalInfo = ArrivalsFixtures.convert(getTargetContext(), env, false);

        // Now confirm that we have the correct number of elements, and values for ETAs for the test
        validateUatcArrivalInfo(arrivalInfo);

        assertEquals("On time", arrivalInfo.get(0).getStatusText());
        assertEquals("2 min late", arrivalInfo.get(1).getStatusText());
        assertEquals("1 min early", arrivalInfo.get(2).getStatusText());
        assertEquals("On time", arrivalInfo.get(3).getStatusText());
        assertEquals("6 min early", arrivalInfo.get(4).getStatusText());
        // Arrivals and departures that will happen in the future
        assertEquals("On time", arrivalInfo.get(5).getStatusText());
        assertEquals("On time", arrivalInfo.get(6).getStatusText());
        assertEquals("5 min delay", arrivalInfo.get(7).getStatusText());
        assertEquals("On time", arrivalInfo.get(8).getStatusText());
        assertEquals("On time", arrivalInfo.get(9).getStatusText());
        assertEquals("10 min delay", arrivalInfo.get(10).getStatusText());
        assertEquals("1 min early", arrivalInfo.get(11).getStatusText());
        assertEquals("On time", arrivalInfo.get(12).getStatusText());
        assertEquals("1 min early", arrivalInfo.get(13).getStatusText());
        assertEquals("1 min delay", arrivalInfo.get(14).getStatusText());
        assertEquals("On time", arrivalInfo.get(15).getStatusText());
        assertEquals("On time", arrivalInfo.get(16).getStatusText());
        assertEquals("4 min delay", arrivalInfo.get(17).getStatusText());
        assertEquals("On time", arrivalInfo.get(18).getStatusText());
        assertEquals("8 min delay", arrivalInfo.get(19).getStatusText());
        assertEquals("2 min delay", arrivalInfo.get(20).getStatusText());
        assertEquals("3 min delay", arrivalInfo.get(21).getStatusText());
        assertEquals("On time", arrivalInfo.get(22).getStatusText());
        assertEquals("On time", arrivalInfo.get(23).getStatusText());
        assertEquals("6 min delay", arrivalInfo.get(24).getStatusText());
        assertEquals("3 min delay", arrivalInfo.get(25).getStatusText());
        assertEquals("6 min delay", arrivalInfo.get(26).getStatusText());
        assertEquals("On time", arrivalInfo.get(27).getStatusText());
        assertEquals("On time", arrivalInfo.get(28).getStatusText());
        assertEquals("On time", arrivalInfo.get(29).getStatusText());
        assertEquals("9 min delay", arrivalInfo.get(30).getStatusText());
        assertEquals("On time", arrivalInfo.get(31).getStatusText());

        /**
         * Test notification texts
         */

        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(0).getShortName(), arrivalInfo.get(0).getRouteLongName())
                + " has arrived.", arrivalInfo.get(0).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(1).getShortName(), arrivalInfo.get(1).getRouteLongName())
                + " has departed.", arrivalInfo.get(1).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(2).getShortName(), arrivalInfo.get(2).getRouteLongName())
                + " has departed.", arrivalInfo.get(2).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(3).getShortName(), arrivalInfo.get(3).getRouteLongName())
                + " has arrived.", arrivalInfo.get(3).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(4).getShortName(), arrivalInfo.get(4).getRouteLongName())
                + " has departed.", arrivalInfo.get(4).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(5).getShortName(), arrivalInfo.get(5).getRouteLongName())
                + " is departing now!", arrivalInfo.get(5).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(6).getShortName(), arrivalInfo.get(6).getRouteLongName())
                + " is arriving now!", arrivalInfo.get(6).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(7).getShortName(), arrivalInfo.get(7).getRouteLongName())
                + " is arriving in 3 min!", arrivalInfo.get(7).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(8).getShortName(), arrivalInfo.get(8).getRouteLongName())
                + " is departing in 5 min!", arrivalInfo.get(8).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(9).getShortName(), arrivalInfo.get(9).getRouteLongName())
                + " is departing in 5 min!", arrivalInfo.get(9).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(10).getShortName(), arrivalInfo.get(10).getRouteLongName())
                + " is arriving in 6 min!", arrivalInfo.get(10).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(11).getShortName(), arrivalInfo.get(11).getRouteLongName())
                + " is arriving in 7 min!", arrivalInfo.get(11).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(12).getShortName(), arrivalInfo.get(12).getRouteLongName())
                + " is arriving in 10 min!", arrivalInfo.get(12).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(13).getShortName(), arrivalInfo.get(13).getRouteLongName())
                + " is departing in 14 min!", arrivalInfo.get(13).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(14).getShortName(), arrivalInfo.get(14).getRouteLongName())
                + " is arriving in 17 min!", arrivalInfo.get(14).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(15).getShortName(), arrivalInfo.get(15).getRouteLongName())
                + " is arriving in 20 min!", arrivalInfo.get(15).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(16).getShortName(), arrivalInfo.get(16).getRouteLongName())
                + " is departing in 20 min!", arrivalInfo.get(16).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(17).getShortName(), arrivalInfo.get(17).getRouteLongName())
                + " is arriving in 23 min!", arrivalInfo.get(17).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(18).getShortName(), arrivalInfo.get(18).getRouteLongName())
                + " is departing in 25 min!", arrivalInfo.get(18).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(19).getShortName(), arrivalInfo.get(19).getRouteLongName())
                + " is arriving in 26 min!", arrivalInfo.get(19).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(20).getShortName(), arrivalInfo.get(20).getRouteLongName())
                + " is departing in 27 min!", arrivalInfo.get(20).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(21).getShortName(), arrivalInfo.get(21).getRouteLongName())
                + " is arriving in 28 min!", arrivalInfo.get(21).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(22).getShortName(), arrivalInfo.get(22).getRouteLongName())
                + " is arriving in 30 min!", arrivalInfo.get(22).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(23).getShortName(), arrivalInfo.get(23).getRouteLongName())
                + " is departing in 30 min!", arrivalInfo.get(23).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(24).getShortName(), arrivalInfo.get(24).getRouteLongName())
                + " is arriving in 32 min!", arrivalInfo.get(24).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(25).getShortName(), arrivalInfo.get(25).getRouteLongName())
                + " is arriving in 32 min!", arrivalInfo.get(25).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(26).getShortName(), arrivalInfo.get(26).getRouteLongName())
                + " is arriving in 34 min!", arrivalInfo.get(26).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(27).getShortName(), arrivalInfo.get(27).getRouteLongName())
                + " is arriving in 34 min!", arrivalInfo.get(27).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(28).getShortName(), arrivalInfo.get(28).getRouteLongName())
                + " is departing in 35 min!", arrivalInfo.get(28).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(29).getShortName(), arrivalInfo.get(29).getRouteLongName())
                + " is departing in 35 min!", arrivalInfo.get(29).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(30).getShortName(), arrivalInfo.get(30).getRouteLongName())
                + " is arriving in 35 min!", arrivalInfo.get(30).getNotifyText());
        assertEquals("Route " + RouteDisplay.getRouteDisplayName(arrivalInfo.get(31).getShortName(), arrivalInfo.get(31).getRouteLongName())
                + " is departing in 35 min!", arrivalInfo.get(31).getNotifyText());
    }

    /**
     * Validates the ETAs and number of arrivals for Tampa's University Area Transit Center.  This
     * data is used for a few tests and we want to make sure it's valid.
     */
    private void validateUatcArrivalInfo(ArrayList<ArrivalInfo> arrivalInfo) {
        assertEquals(32, arrivalInfo.size());

        assertEquals(-4, arrivalInfo.get(0).getEta());
        assertEquals(-3, arrivalInfo.get(1).getEta());
        assertEquals(-1, arrivalInfo.get(2).getEta());
        assertEquals(-1, arrivalInfo.get(3).getEta());
        assertEquals(-1, arrivalInfo.get(4).getEta());
        assertEquals(0, arrivalInfo.get(5).getEta()); // First non-negative ETA
        assertEquals(0, arrivalInfo.get(6).getEta());
        assertEquals(3, arrivalInfo.get(7).getEta());
        assertEquals(5, arrivalInfo.get(8).getEta());
        assertEquals(5, arrivalInfo.get(9).getEta());
        assertEquals(6, arrivalInfo.get(10).getEta());
        assertEquals(7, arrivalInfo.get(11).getEta());
        assertEquals(10, arrivalInfo.get(12).getEta());
        assertEquals(14, arrivalInfo.get(13).getEta());
        assertEquals(17, arrivalInfo.get(14).getEta());
        assertEquals(20, arrivalInfo.get(15).getEta());
        assertEquals(20, arrivalInfo.get(16).getEta());
        assertEquals(23, arrivalInfo.get(17).getEta());
        assertEquals(25, arrivalInfo.get(18).getEta());
        assertEquals(26, arrivalInfo.get(19).getEta());
        assertEquals(27, arrivalInfo.get(20).getEta());
        assertEquals(28, arrivalInfo.get(21).getEta());
        assertEquals(30, arrivalInfo.get(22).getEta());
        assertEquals(30, arrivalInfo.get(23).getEta());
        assertEquals(32, arrivalInfo.get(24).getEta());
        assertEquals(32, arrivalInfo.get(25).getEta());
        assertEquals(34, arrivalInfo.get(26).getEta());
        assertEquals(34, arrivalInfo.get(27).getEta());
        assertEquals(35, arrivalInfo.get(28).getEta());
        assertEquals(35, arrivalInfo.get(29).getEta());
        assertEquals(35, arrivalInfo.get(30).getEta());
        assertEquals(35, arrivalInfo.get(31).getEta());
    }

    private String formatTime(long time) {
        return DisplayFormat.formatTime(getTargetContext(), time);
    }

    /**
     * Tests including all situations (service alerts) from a response in the final list, including
     * ones specific to routes
     */
    @Test
    public void testGetAllSituations() throws Exception {
        PreferencesEntryPoint.get(getTargetContext())
                .setString(R.string.preference_key_oba_api_url, "sdmts.onebusway.org/api");

        /**
         * Test route-specific alerts only
         */
        ObaEnvelope<EntryWithReferences<ArrivalsForStop>> env = ArrivalsFixtures.load(
                getTargetContext(), "arrivals_and_departures_for_stop_mts_11670_route_alerts");
        // No stop-specific alerts; route alerts don't appear in the entry's situations - see #700.
        assertEquals(0, ArrivalsFixtures.stopSituations(env).size());

        // They do appear in the references and are referenced by each arrival; getAllSituations gathers them.
        List<ObaSituation> allSituations = ArrivalsFixtures.allSituations(env, null);
        HashSet<String> situationIds = new HashSet<>();
        for (ObaSituation situation : allSituations) {
            situationIds.add(situation.getId());
        }
        // 7 route-specific alerts + 0 stop-specific = 7 total.
        assertEquals(7, allSituations.size());
        assertEquals(7, situationIds.size());
        assertTrue(situationIds.contains("MTS_38"));
        assertTrue(situationIds.contains("MTS_37"));
        assertTrue(situationIds.contains("MTS_28"));
        assertTrue(situationIds.contains("MTS_34"));
        assertTrue(situationIds.contains("MTS_11"));
        assertTrue(situationIds.contains("MTS_33"));
        assertTrue(situationIds.contains("MTS_3"));

        /**
         * Test route and stop alerts
         */

        env = ArrivalsFixtures.load(
                getTargetContext(), "arrivals_and_departures_for_stop_mts_13353_route_and_stop_alerts");
        // One stop alert in the entry; route alerts excluded - see #700.
        assertEquals(1, ArrivalsFixtures.stopSituations(env).size());

        allSituations = ArrivalsFixtures.allSituations(env, null);
        situationIds = new HashSet<>();
        for (ObaSituation situation : allSituations) {
            situationIds.add(situation.getId());
        }
        // 4 route-specific alerts + 1 stop-specific = 5 total.
        assertEquals(5, allSituations.size());
        assertEquals(5, situationIds.size());
        assertTrue(situationIds.contains("MTS_32"));
        assertTrue(situationIds.contains("MTS_34"));
        assertTrue(situationIds.contains("MTS_14"));
        assertTrue(situationIds.contains("MTS_13"));
        // The one stop alert is included.
        assertTrue(situationIds.contains("MTS_9c943ee8-d566-4cd8-8a89-a2a535ebe4fe"));
        assertTrue(situationIds.contains(ArrivalsFixtures.stopSituations(env).get(0).getId()));

        /*
        Test filtering routes alerts
         */
        env = ArrivalsFixtures.load(
                getTargetContext(), "arrivals_and_departures_for_stop_mts_11671_filter_route_alerts");

        List<String> routeFilters = new ArrayList<>();
        routeFilters.add("MTS_1");

        List<ObaSituation> filteredSituations = ArrivalsFixtures.allSituations(env, routeFilters);

        List<String> allIds = new ArrayList<>();
        for (ObaSituation situation : ArrivalsFixtures.allSituations(env, null)) {
            allIds.add(situation.getId());
        }

        List<String> filteredIds = new ArrayList<>();
        for (ObaSituation situation : filteredSituations) {
            filteredIds.add(situation.getId());
        }

        // Two alerts (routes 1 and 11) unfiltered; only the route-1 alert after filtering to MTS_1.
        assertEquals(2, allIds.size());
        assertEquals(1, filteredIds.size());
        assertEquals(1, filteredSituations.size());

        // The route-1 alert is kept; the route-11 alert is present unfiltered but filtered out.
        assertTrue(filteredIds.contains("MTS_RTA:11569670"));
        assertTrue(allIds.contains("MTS_RTA:11569666"));
        assertFalse(filteredIds.contains("MTS_RTA:11569666"));
    }
}
