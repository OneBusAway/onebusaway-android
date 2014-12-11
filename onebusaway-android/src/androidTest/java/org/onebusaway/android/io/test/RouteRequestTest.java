/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.io.test;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.request.ObaRouteRequest;
import org.onebusaway.android.io.request.ObaRouteResponse;
import org.onebusaway.android.mock.MockRegion;

import android.graphics.Color;


public class RouteRequestTest extends ObaTestCase {

    public void testKCMRoute() {
        int defaultRouteLineColor = getContext().getResources().getColor(
                R.color.route_line_color_default);
        int defaultRouteTextColor = getContext().getResources().getColor(
                R.color.route_text_color_default);
        ObaRouteRequest.Builder builder = new ObaRouteRequest.Builder(getContext(), "1_10");
        ObaRouteRequest request = builder.build();
        ObaRouteResponse response = request.call();
        assertOK(response);
        assertEquals("1_10", response.getId());
        assertEquals("10", response.getShortName());
        assertEquals("1", response.getAgencyId());
        assertEquals(ObaRoute.TYPE_BUS, response.getType());
        int routeColor = defaultRouteLineColor;
        if (response.getColor() != null) {
            routeColor = response.getColor();
        }
        int routeTextColor = defaultRouteTextColor;
        if (response.getTextColor() != null) {
            routeTextColor = response.getTextColor();
        }
        // KCM doesn't define route line or text color in their GTFS, so we should be using the defaults
        assertEquals(defaultRouteLineColor, routeColor);
        assertEquals(defaultRouteTextColor, routeTextColor);

        ObaAgency agency = response.getAgency();
        assertNotNull(agency);
        assertEquals("1", agency.getId());
        assertEquals("Metro Transit", agency.getName());
    }

    public void testNewRequest() {
        // This is just to make sure we copy and call newRequest() at least once
        ObaRouteRequest request = ObaRouteRequest.newRequest(getContext(), "1_10");
        assertNotNull(request);
    }

    public void testHARTRoute() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);

        int defaultRouteLineColor = getContext().getResources().getColor(
                R.color.route_line_color_default);
        int defaultRouteTextColor = getContext().getResources().getColor(
                R.color.route_text_color_default);

        ObaRouteRequest.Builder builder = new ObaRouteRequest.Builder(getContext(),
                "Hillsborough Area Regional Transit_5");
        ObaRouteRequest request = builder.build();
        ObaRouteResponse response = request.call();
        assertOK(response);
        assertEquals("Hillsborough Area Regional Transit_5", response.getId());
        assertEquals("5", response.getShortName());
        assertEquals("Hillsborough Area Regional Transit", response.getAgencyId());
        assertEquals(ObaRoute.TYPE_BUS, response.getType());

        int route5LineColor = Color.parseColor("#09346D");
        int route5TextColor = Color.parseColor("#FFFFFF");

        int routeColor = defaultRouteLineColor;
        if (response.getColor() != null) {
            routeColor = response.getColor();
        }
        int routeTextColor = defaultRouteTextColor;
        if (response.getTextColor() != null) {
            routeTextColor = response.getTextColor();
        }

        assertEquals(route5LineColor, routeColor);
        assertEquals(route5TextColor, routeTextColor);

        ObaAgency agency = response.getAgency();
        assertNotNull(agency);
        assertEquals("Hillsborough Area Regional Transit", agency.getId());
        assertEquals("Hillsborough Area Regional Transit", agency.getName());
    }
}
