package org.onebusaway.android.util.test;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.MockObaStop;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.ui.ArrivalInfo;
import org.onebusaway.android.util.UIHelp;

import android.text.TextUtils;

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

        String serializedRoutes = UIHelp.serializeRouteDisplayNames(stop, routes);
        assertEquals("1,5", serializedRoutes);
    }

    public void testDeserializeRouteDisplayNames() {
        String serializedRoutes = "1,5";
        List<String> routeList = UIHelp.deserializeRouteDisplayNames(serializedRoutes);
        assertEquals("1", routeList.get(0));
        assertEquals("5", routeList.get(1));
    }

    public void testFormatRouteDisplayNames() {
        String formattedString;
        ArrayList<String> routes = new ArrayList<String>();
        ArrayList<String> highlightedRoutes = new ArrayList<String>();

        routes.add("1");
        routes.add("5");
        formattedString = UIHelp.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5", formattedString);

        routes.clear();
        routes.add("5");
        routes.add("1");
        formattedString = UIHelp.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5", formattedString);

        routes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        formattedString = UIHelp.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1, 5, 8a, 8b, 15", formattedString);

        // Test highlighting one URL
        routes.clear();
        routes.add("5");
        routes.add("1");
        routes.add("15");
        routes.add("8b");
        routes.add("8a");
        highlightedRoutes.add("1");
        formattedString = UIHelp.formatRouteDisplayNames(routes, highlightedRoutes);
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
        formattedString = UIHelp.formatRouteDisplayNames(routes, highlightedRoutes);
        assertEquals("1*, 5, 8a, 8b*, 15*", formattedString);
    }

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
                arrivals, null, response.getCurrentTime());

        ObaRoute route = response.getRoute(arrivalInfo.get(0).getInfo().getRouteId());
        String url = route != null ? route.getUrl() : null;
        boolean hasUrl = !TextUtils.isEmpty(url);
        boolean isReminderVisible = false;  // We don't have views here, so just fake it

        // HART has route schedule URLs in test data, so below options should allow the user to set
        // a reminder and view the route schedule
        int options = UIHelp.buildTripOptions(hasUrl, isReminderVisible);
        String[] array = getContext().getResources().getStringArray(options);
        assertEquals(array[0], "Set a reminder");
        assertEquals(array[1], "Show route information");
        assertEquals(array[2], "Show only this route");
        assertEquals(array[3], "Show route schedule");
        assertEquals(array[4], "Report problem with trip");

        isReminderVisible = true;

        // Now we should see route schedules and *edit* the reminder
        options = UIHelp.buildTripOptions(hasUrl, isReminderVisible);
        array = getContext().getResources().getStringArray(options);
        assertEquals(array[0], "Edit this reminder");
        assertEquals(array[1], "Show route information");
        assertEquals(array[2], "Show only this route");
        assertEquals(array[3], "Show route schedule");
        assertEquals(array[4], "Report problem with trip");

        // Get a PSTA response - PSTA test data doesn't include route schedule URLs
        Application.get().setCustomApiUrl("api.tampa.onebusaway.org/api");
        response =
                new ObaArrivalInfoRequest.Builder(getContext(), "PSTA_4077").build().call();
        assertOK(response);

        stop = response.getStop();
        assertNotNull(stop);
        assertEquals("PSTA_4077", stop.getId());
        routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("PSTA", agency.getId());

        arrivals = response.getArrivalInfo();
        assertNotNull(arrivals);
        arrivalInfo = ArrivalInfo.convertObaArrivalInfo(getContext(),
                arrivals, null, response.getCurrentTime());

        route = response.getRoute(arrivalInfo.get(0).getInfo().getRouteId());
        url = route != null ? route.getUrl() : null;
        hasUrl = !TextUtils.isEmpty(url);
        isReminderVisible = false;  // We don't have views here, so just fake it

        // PSTA does not have route schedule URLs in test data, so below options should allow the
        // user to set a reminder but NOT view the route schedule
        options = UIHelp.buildTripOptions(hasUrl, isReminderVisible);
        array = getContext().getResources().getStringArray(options);
        assertEquals(array[0], "Set a reminder");
        assertEquals(array[1], "Show route information");
        assertEquals(array[2], "Show only this route");
        assertEquals(array[3], "Report problem with trip");

        isReminderVisible = true;

        // Now we should see *edit* the reminder, and still no route schedule
        options = UIHelp.buildTripOptions(hasUrl, isReminderVisible);
        array = getContext().getResources().getStringArray(options);
        assertEquals(array[0], "Edit this reminder");
        assertEquals(array[1], "Show route information");
        assertEquals(array[2], "Show only this route");
        assertEquals(array[3], "Report problem with trip");
    }
}
