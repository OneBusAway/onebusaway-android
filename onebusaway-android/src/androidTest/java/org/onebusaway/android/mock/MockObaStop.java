/*
 * Copyright (C) 2014-2017 University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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
package org.onebusaway.android.mock;

import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.util.LocationUtils;

import android.location.Location;

import java.util.HashMap;

/**
 * Provides a mock ObaStop to use in testing
 */
public class MockObaStop {

    static ObaStop stop26 = new MockStop26();

    static ObaRoute route1 = new MockObaRoute1();

    static ObaRoute route5 = new MockObaRoute5();

    public static ObaStop getMockStop() {
        return stop26;
    }

    public static HashMap<String, ObaRoute> getMockRoutes() {
        HashMap<String, ObaRoute> routes = new HashMap<String, ObaRoute>();
        routes.put(route1.getId(), route1);
        routes.put(route5.getId(), route5);
        return routes;
    }

    private static class MockStop26 implements ObaStop {

        @Override
        public String getStopCode() {
            return "26";
        }

        @Override
        public String getName() {
            return "Nebraska Av @ Columbus Dr";
        }

        @Override
        public Location getLocation() {
            return LocationUtils.makeLocation(getLatitude(), getLongitude());
        }

        @Override
        public double getLatitude() {
            return 27.966904;
        }

        @Override
        public double getLongitude() {
            return -82.451178;
        }

        @Override
        public String getDirection() {
            return "N";
        }

        @Override
        public int getLocationType() {
            return ObaStop.LOCATION_STOP;
        }

        @Override
        public String[] getRouteIds() {
            String[] routes = new String[2];
            routes[0] = "Hillsborough Area Regional Transit_1";
            routes[1] = "Hillsborough Area Regional Transit_5";
            return routes;
        }

        @Override
        public String getId() {
            return "Hillsborough Area Regional Transit_26";
        }
    }

    private static class MockObaRoute1 implements ObaRoute {

        @Override
        public String getShortName() {
            return "1";
        }

        @Override
        public String getLongName() {
            return "Florida Avenue";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public int getType() {
            return ObaRoute.TYPE_BUS;
        }

        @Override
        public String getUrl() {
            return "http://www.gohart.org/routes/hart/01.html";
        }

        @Override
        public Integer getColor() {
            return 0; // GTFS says 09346D?
        }

        @Override
        public Integer getTextColor() {
            return 0;  // GTFS says FFFFFF?
        }

        @Override
        public String getAgencyId() {
            return "Hillsborough Area Regional Transit";
        }

        @Override
        public String getId() {
            return "Hillsborough Area Regional Transit_1";
        }
    }

    private static class MockObaRoute5 implements ObaRoute {

        @Override
        public String getShortName() {
            return "5";
        }

        @Override
        public String getLongName() {
            return "40th Street";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public int getType() {
            return ObaRoute.TYPE_BUS;
        }

        @Override
        public String getUrl() {
            return "http://www.gohart.org/routes/hart/05.html";
        }

        @Override
        public Integer getColor() {
            return 0; // GTFS says 09346D?
        }

        @Override
        public Integer getTextColor() {
            return 0;  // GTFS says FFFFFF?
        }

        @Override
        public String getAgencyId() {
            return "Hillsborough Area Regional Transit";
        }

        @Override
        public String getId() {
            return "Hillsborough Area Regional Transit_5";
        }
    }
}
