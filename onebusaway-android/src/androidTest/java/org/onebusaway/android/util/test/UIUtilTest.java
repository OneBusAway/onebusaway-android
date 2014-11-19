package org.onebusaway.android.util.test;

import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.mock.MockObaStop;
import org.onebusaway.android.util.UIHelp;

import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Tests to evaluate utility methods related to the presentation of information to the user in the
 * UI
 */
public class UIUtilTest extends AndroidTestCase {

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

}
