package org.onebusaway.android.util.test;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.map.MapParams;
import org.onebusaway.android.mock.MockObaStop;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.ui.ArrivalInfo;
import org.onebusaway.android.util.UIUtils;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.text.TextUtils;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Tests to evaluate utility methods related to the presentation of information to the user in the
 * UI
 */
public class UIUtilTest extends ObaTestCase {

    public void testSerializeRouteDisplayNames() {
        ObaStop stop = MockObaStop.getMockStop();
        HashMap<String, ObaRoute> routes = MockObaStop.getMockRoutes();

        String serializedRoutes = UIUtils.serializeRouteDisplayNames(stop, routes);
        assertEquals("1,5", serializedRoutes);
    }

    public void testDeserializeRouteDisplayNames() {
        String serializedRoutes = "1,5";
        List<String> routeList = UIUtils.deserializeRouteDisplayNames(serializedRoutes);
        assertEquals("1", routeList.get(0));
        assertEquals("5", routeList.get(1));
    }

    public void testFormatRouteDisplayNames() {
        String formattedString;
        ArrayList<String> routes = new ArrayList<String>();
        ArrayList<String> highlightedRoutes = new ArrayList<String>();

        routes.add("1");
        routes.add("5");
        formattedString = UIUtils.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5", formattedString);

        routes.clear();
        routes.add("5");
        routes.add("1");
        formattedString = UIUtils.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5", formattedString);

        routes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        formattedString = UIUtils.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5, 8a, 8b, 15", formattedString);

        // Test highlighting one URL
        routes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        highlightedRoutes.add("1");
        formattedString = UIUtils.formatRouteDisplayNames(routes, highlightedRoutes);
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
        formattedString = UIUtils.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1*, 5, 8a, 8b*, 15*", formattedString);
    }

    /**
     * Tests formatting trip headsigns
     */
    public void testFormatHeadsign() {
        assertEquals("North to University Area TC",
                UIUtils.formatHeadsign("North to University Area TC"));
        assertEquals("North To University Area Tc",
                UIUtils.formatHeadsign("NORTH TO UNIVERSITY AREA TC"));
    }

    /**
     * Tests building the trip options menu, based on various route/trip parameters
     */
    public void testBuildTripOptions() {
        // Initial setup to get an ObaArrivalInfo object from a test response
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_3105").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_3105", stop.getId());
        List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);
        ArrayList<ArrivalInfo> arrivalInfo = ArrivalInfo.convertObaArrivalInfo(getContext(),
                arrivals, null, response.getCurrentTime(), true);

        ObaRoute route = response.getRoute(arrivalInfo.get(0).getInfo().getRouteId());
        String url = route != null ? route.getUrl() : null;
        boolean hasUrl = !TextUtils.isEmpty(url);
        boolean isReminderVisible = false;  // We don't have views here, so just fake it
        boolean isRouteFavorite = false;  // We'll fake this too, for our purposes

        // HART has route schedule URLs in test data, so below options should allow the user to set
        // a reminder and view the route schedule
        List<String> options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl, isReminderVisible);
        assertEquals(options.get(0), "Add star to route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Set a reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Show route schedule");
        assertEquals(options.get(6), "Report arrival time problem");

        isReminderVisible = true;

        // Now we should see route schedules and *edit* the reminder
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl, isReminderVisible);
        assertEquals(options.get(0), "Add star to route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Edit this reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Show route schedule");
        assertEquals(options.get(6), "Report arrival time problem");

        // Get a PSTA response - PSTA test data doesn't include route schedule URLs
        ObaArrivalInfoResponse response2 =
                new ObaArrivalInfoRequest.Builder(getContext(), "PSTA_4077").build().call();
        assertOK(response2);

        stop = response2.getStop();
        assertNotNull(stop);
        assertEquals("PSTA_4077", stop.getId());
        routes = response2.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        agency = response2.getAgency(routes.get(0).getAgencyId());
        assertEquals("PSTA", agency.getId());

        arrivals = response2.getArrivalInfo();
        assertNotNull(arrivals);
        arrivalInfo = ArrivalInfo.convertObaArrivalInfo(getContext(),
                arrivals, null, response2.getCurrentTime(), true);

        route = response2.getRoute(arrivalInfo.get(0).getInfo().getRouteId());
        url = route != null ? route.getUrl() : null;
        boolean hasUrl2 = !TextUtils.isEmpty(url);
        isReminderVisible = false;  // We don't have views here, so just fake it

        // PSTA does not have route schedule URLs in test data, so below options should allow the
        // user to set a reminder but NOT view the route schedule
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl2, isReminderVisible);
        assertEquals(options.get(0), "Add star to route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Set a reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Report arrival time problem");

        isReminderVisible = true;

        // Now we should see *edit* the reminder, and still no route schedule
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl2, isReminderVisible);
        assertEquals(options.get(0), "Add star to route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Edit this reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Report arrival time problem");

        // Now change route to favorite, and do all the above over again
        isRouteFavorite = true;

        // HART
        isReminderVisible = false;  // We don't have views here, so just fake it

        // HART has route schedule URLs in test data, so below options should allow the user to set
        // a reminder and view the route schedule
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl, isReminderVisible);
        assertEquals(options.get(0), "Remove star from route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Set a reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Show route schedule");
        assertEquals(options.get(6), "Report arrival time problem");

        isReminderVisible = true;

        // Now we should see route schedules and *edit* the reminder
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl, isReminderVisible);
        assertEquals(options.get(0), "Remove star from route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Edit this reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Show route schedule");
        assertEquals(options.get(6), "Report arrival time problem");

        // PSTA
        isReminderVisible = false;  // We don't have views here, so just fake it

        // PSTA does not have route schedule URLs in test data, so below options should allow the
        // user to set a reminder but NOT view the route schedule
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl2, isReminderVisible);
        assertEquals(options.get(0), "Remove star from route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Set a reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Report arrival time problem");

        isReminderVisible = true;

        // Now we should see *edit* the reminder, and still no route schedule
        options = UIUtils
                .buildTripOptions(getContext(), isRouteFavorite, hasUrl2, isReminderVisible);
        assertEquals(options.get(0), "Remove star from route");
        assertEquals(options.get(1), "Show route on map");
        assertEquals(options.get(2), "Show trip status");
        assertEquals(options.get(3), "Edit this reminder");
        assertEquals(options.get(4), "Show only this route");
        assertEquals(options.get(5), "Report arrival time problem");
    }

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
        stopDetails = UIUtils.createStopDetailsDialogText(getContext(),
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
        stopDetails = UIUtils.createStopDetailsDialogText(getContext(),
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
        stopDetails = UIUtils.createStopDetailsDialogText(getContext(),
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
        expectedMessage.append(getContext().getString(UIUtils.getStopDirectionText(stopDirection)));
        assertEquals(expectedMessage.toString(), (String) stopDetails.second);

        // Test with stop nickname and direction
        stopUserName = "My stop nickname";
        stopDirection = "S";
        stopDetails = UIUtils.createStopDetailsDialogText(getContext(),
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
        expectedMessage.append(getContext().getString(UIUtils.getStopDirectionText(stopDirection)));
        assertEquals(expectedMessage.toString(), (String) stopDetails.second);
    }

    public void testArrivalTimeIndexSearch() {
        // Initial setup to get an ObaArrivalInfo object from a test response
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_6497").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_6497", stop.getId());
        List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        // First de-select any existing route favorites, to make sure the test returns correct results
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_2",
                "UATC to Downtown via Nebraska Ave",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_1",
                "UATC to Downtown via Florida Ave",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_18",
                "North to UATC/Livingston",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_5",
                "South to Downtown/MTC",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_2",
                "UATC to Downtown via Nebraska Ave",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_18",
                "South to UATC/Downtown/MTC",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_12",
                "North to University Area TC",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_9",
                "UATC to Downtown via 15th St",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_12",
                "South to Downtown/MTC",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_5",
                "North to University Area TC",
                stop.getId(),
                false);

        // Now mark 2 favorites - first non-negative index for this route/headsign will be index 11
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_6",
                "North to University Area TC",
                stop.getId(),
                true);

        // First non-negative index for this route/headsign will be index 13
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_6",
                "South to Downtown/MTC",
                stop.getId(),
                true);

        // Get the response
        ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);
        ArrayList<ArrivalInfo> arrivalInfo = ArrivalInfo.convertObaArrivalInfo(getContext(),
                arrivals, null, response.getCurrentTime(), true);

        // Now confirm that we have the correct number of elements, and values for ETAs for the test
        validateUatcArrivalInfo(arrivalInfo);

        // First non-negative arrival should be "0", in index 5
        int firstNonNegativeArrivalIndex = ArrivalInfo.findFirstNonNegativeArrival(arrivalInfo);
        assertEquals(5, firstNonNegativeArrivalIndex);

        ArrayList<Integer> preferredArrivalIndexes = ArrivalInfo
                .findPreferredArrivalIndexes(arrivalInfo);

        // Indexes 11 and 13 should hold the favorites
        assertEquals(11, preferredArrivalIndexes.get(0).intValue());
        assertEquals(13, preferredArrivalIndexes.get(1).intValue());

        // Now clear the favorites
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_6",
                "North to University Area TC",
                stop.getId(),
                false);
        ObaContract.RouteHeadsignFavorites.markAsFavorite(getContext(),
                "Hillsborough Area Regional Transit_6",
                "South to Downtown/MTC",
                stop.getId(),
                false);

        // Process the response again (resetting the included favorite info)
        arrivalInfo = ArrivalInfo.convertObaArrivalInfo(getContext(),
                arrivals, null, response.getCurrentTime(), true);
        preferredArrivalIndexes = ArrivalInfo.findPreferredArrivalIndexes(arrivalInfo);

        // Now the first two non-negative arrival times should be returned - indexes 5 and 6
        assertEquals(5, preferredArrivalIndexes.get(0).intValue());
        assertEquals(6, preferredArrivalIndexes.get(1).intValue());
    }

    /**
     * Tests the status and time labels for arrival info
     */
    public void testArrivalInfoLabels() {
        // Initial setup to get an ObaArrivalInfo object from a test response
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_6497").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_6497", stop.getId());
        List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        // Get the response
        ObaArrivalInfo[] arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);

        /**
         * Labels *with* arrive/depart included, and time labels
         */
        boolean includeArriveDepartLabels = true;
        ArrayList<ArrivalInfo> arrivalInfo = ArrivalInfo.convertObaArrivalInfo(
                getContext(),
                arrivals, null, response.getCurrentTime(), includeArriveDepartLabels);

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
        includeArriveDepartLabels = false;
        arrivalInfo = ArrivalInfo.convertObaArrivalInfo(getContext(), arrivals, null,
                response.getCurrentTime(), includeArriveDepartLabels);

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
    }

    public void testMaybeShrinkRouteName() {
        TextView tv = new TextView(getContext());

        String routeShortName = "TST";
        float textSize = tv.getTextSize();
        UIUtils.maybeShrinkRouteName(getContext(), tv, routeShortName);
        assertEquals(textSize, tv.getTextSize());

        routeShortName = "Test";
        UIUtils.maybeShrinkRouteName(getContext(), tv, routeShortName);
        assertEquals(getContext().getResources().getDimension(R.dimen.route_name_text_size_medium),
                tv.getTextSize());

        routeShortName = "Test2";
        UIUtils.maybeShrinkRouteName(getContext(), tv, routeShortName);
        assertEquals(getContext().getResources().getDimension(R.dimen.route_name_text_size_small),
                tv.getTextSize());
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

    public void testGetTransparentColor() {
        String colorString = "#777777";
        int alpha = 127;
        int color = Color.parseColor(colorString);
        int newColor = UIUtils.getTransparentColor(color, alpha);

        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        int newR = Color.red(newColor);
        int newG = Color.green(newColor);
        int newB = Color.blue(newColor);
        int newAlpha = Color.alpha(newColor);

        assertEquals(r, newR);
        assertEquals(g, newG);
        assertEquals(b, newB);
        assertEquals(alpha, newAlpha);
    }

    private String formatTime(long time) {
        return UIUtils.formatTime(getContext(), time);
    }

    /**
     * Tests getting map view center information from a bundle
     */
    public void testGetMapCenter() {
        // Check null and empty bundles
        Bundle b = null;
        assertNull(UIUtils.getMapCenter(b));

        b = new Bundle();
        assertNull(UIUtils.getMapCenter(b));

        // Check single params
        b.putDouble(MapParams.CENTER_LAT, 0.0);
        assertNull(UIUtils.getMapCenter(b));

        b = new Bundle();
        b.putDouble(MapParams.CENTER_LON, 0.0);
        assertNull(UIUtils.getMapCenter(b));

        // Check invalid lat/long
        b = new Bundle();
        b.putDouble(MapParams.CENTER_LAT, 0.0);
        b.putDouble(MapParams.CENTER_LON, 0.0);
        assertNull(UIUtils.getMapCenter(b));

        // Check valid lat/long
        final double lat = 28.343243;
        final double lon = -87.234234;

        b = new Bundle();
        b.putDouble(MapParams.CENTER_LAT, lat);
        b.putDouble(MapParams.CENTER_LON, lon);
        Location l = UIUtils.getMapCenter(b);
        assertNotNull(l);

        assertEquals(lat, l.getLatitude());
        assertEquals(lon, l.getLongitude());
    }
}
