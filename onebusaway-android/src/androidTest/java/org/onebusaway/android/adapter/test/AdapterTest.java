/*
 * Copyright (C) 2012-2017 Paul Watts,
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

package org.onebusaway.android.adapter.test;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.ui.ArrivalsListAdapterStyleA;
import org.onebusaway.android.ui.ArrivalsListAdapterStyleB;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests aspects of the adapters used in OBA
 */
public class AdapterTest extends ObaTestCase {

    private ArrivalsListAdapterStyleA adapterA;

    private ArrivalsListAdapterStyleB adapterB;

    public void testAdapterA() throws Exception {
        // Test by setting region
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
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();

        adapterA = new ArrivalsListAdapterStyleA(getContext());
        adapterA.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        View v = adapterA.getView(0, null, null);
        assertNotNull(v);
    }

    public void testAdapterB() throws Exception {
        // Test by setting region
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
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();

        adapterB = new ArrivalsListAdapterStyleB(getContext());
        adapterB.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        View v = adapterB.getView(0, null, null);
        assertNotNull(v);
    }

    /**
     * Tests ArrivalsListAdapterStyleA for the case where there is only a single arrival time in the
     * response, and the arrival time is negative (i.e., the bus just left).
     * ArrivalsListAdapterStyleB had a bug causing a crash with this input data, since it filters
     * out negative arrival times, but didn't appropriately handle the case where this would cause
     * an empty list.
     */
    public void testAdapterASinglePastArrivalTime() throws Exception {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_1622").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_1622", stop.getId());
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();

        adapterA = new ArrivalsListAdapterStyleA(getContext());
        adapterA.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        View v = adapterA.getView(0, null, null);
        assertNotNull(v);
    }

    /**
     * Tests ArrivalsListAdapterStyleB for the case where there is only a single arrival time in the
     * response, and the arrival time is negative (i.e., the bus just left).
     * ArrivalsListAdapterStyleB had a bug causing a crash with this input data, since it filters
     * out negative arrival times, but didn't appropriately handle the case where this would cause
     * an empty list.
     */
    public void testAdapterBSinglePastArrivalTime() throws Exception {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getContext(),
                        "Hillsborough Area Regional Transit_1622").build().call();
        assertOK(response);
        ObaStop stop = response.getStop();
        assertNotNull(stop);
        assertEquals("Hillsborough Area Regional Transit_1622", stop.getId());
        final List<ObaRoute> routes = response.getRoutes(stop.getRouteIds());
        assertTrue(routes.size() > 0);
        ObaAgency agency = response.getAgency(routes.get(0).getAgencyId());
        assertEquals("Hillsborough Area Regional Transit", agency.getId());

        final ObaArrivalInfo[] arrivals = response.getArrivalInfo();

        adapterB = new ArrivalsListAdapterStyleB(getContext());
        adapterB.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        if (!adapterB.isEmpty()) {
            View v = adapterB.getView(0, null, null);
            assertNotNull(v);
        }
    }
}
