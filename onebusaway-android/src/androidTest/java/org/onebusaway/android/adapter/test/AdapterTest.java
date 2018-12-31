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

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.Occupancy;
import org.onebusaway.android.io.elements.OccupancyState;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.mock.MockRegion;
import org.onebusaway.android.ui.ArrivalsListAdapterStyleA;
import org.onebusaway.android.ui.ArrivalsListAdapterStyleB;
import org.onebusaway.android.util.UIUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.test.runner.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Tests aspects of the adapters used in OBA
 */
@RunWith(AndroidJUnit4.class)
public class AdapterTest extends ObaTestCase {

    private ArrivalsListAdapterStyleA adapterA;

    private ArrivalsListAdapterStyleB adapterB;

    @Test
    public void testAdapterA() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
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

        adapterA = new ArrivalsListAdapterStyleA(getTargetContext());
        adapterA.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        View v = adapterA.getView(0, null, null);
        assertNotNull(v);
    }

    @Test
    public void testAdapterB() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
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

        adapterB = new ArrivalsListAdapterStyleB(getTargetContext());
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
    @Test
    public void testAdapterASinglePastArrivalTime() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
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

        adapterA = new ArrivalsListAdapterStyleA(getTargetContext());
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
    @Test
    public void testAdapterBSinglePastArrivalTime() throws Exception {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
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

        adapterB = new ArrivalsListAdapterStyleB(getTargetContext());
        adapterB.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        if (!adapterB.isEmpty()) {
            View v = adapterB.getView(0, null, null);
            assertNotNull(v);
        }
    }

    /**
     * Test occupancy visibility - we need to do this somewhere with an inflated view, so this
     * adapter works
     */
    @Test
    public void testSetOccupancyVisibility() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
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

        adapterA = new ArrivalsListAdapterStyleA(getTargetContext());
        adapterA.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        View v = adapterA.getView(0, null, null);
        assertNotNull(v);

        // Test occupancy visibility
        ViewGroup occupancy = v.findViewById(R.id.occupancy);
        ImageView silhouette1 = v.findViewById(R.id.silhouette1);
        ImageView silhouette2 = v.findViewById(R.id.silhouette2);
        ImageView silhouette3 = v.findViewById(R.id.silhouette3);

        // 3 icons
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.NOT_ACCEPTING_PASSENGERS);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.VISIBLE, silhouette1.getVisibility());
        assertEquals(View.VISIBLE, silhouette2.getVisibility());
        assertEquals(View.VISIBLE, silhouette3.getVisibility());

        // 3 icons
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.FULL);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.VISIBLE, silhouette1.getVisibility());
        assertEquals(View.VISIBLE, silhouette2.getVisibility());
        assertEquals(View.VISIBLE, silhouette3.getVisibility());

        // 3 icons
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.CRUSHED_STANDING_ROOM_ONLY);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.VISIBLE, silhouette1.getVisibility());
        assertEquals(View.VISIBLE, silhouette2.getVisibility());
        assertEquals(View.VISIBLE, silhouette3.getVisibility());

        // 2 icons
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.STANDING_ROOM_ONLY);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.VISIBLE, silhouette1.getVisibility());
        assertEquals(View.VISIBLE, silhouette2.getVisibility());
        assertEquals(View.GONE, silhouette3.getVisibility());

        // 1 icon
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.FEW_SEATS_AVAILABLE);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.VISIBLE, silhouette1.getVisibility());
        assertEquals(View.GONE, silhouette2.getVisibility());
        assertEquals(View.GONE, silhouette3.getVisibility());

        // 1 icon
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.MANY_SEATS_AVAILABLE);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.VISIBLE, silhouette1.getVisibility());
        assertEquals(View.GONE, silhouette2.getVisibility());
        assertEquals(View.GONE, silhouette3.getVisibility());

        // 0 icons
        UIUtils.setOccupancyVisibility(occupancy, Occupancy.EMPTY);
        assertEquals(View.VISIBLE, occupancy.getVisibility());
        assertEquals(View.GONE, silhouette1.getVisibility());
        assertEquals(View.GONE, silhouette2.getVisibility());
        assertEquals(View.GONE, silhouette3.getVisibility());

        // Hide entire occupancy
        UIUtils.setOccupancyVisibility(occupancy, null);
        assertEquals(View.GONE, occupancy.getVisibility());
        assertEquals(View.GONE, silhouette1.getVisibility());
        assertEquals(View.GONE, silhouette2.getVisibility());
        assertEquals(View.GONE, silhouette3.getVisibility());
    }

    /**
     * Test occupancy visibility - we need to do this somewhere with an inflated view, so this
     * adapter works
     */
    @Test
    public void testSetOccupancyContentDescription() {
        // Test by setting region
        ObaRegion tampa = MockRegion.getTampa(getTargetContext());
        assertNotNull(tampa);
        Application.get().setCurrentRegion(tampa);
        ObaArrivalInfoResponse response =
                new ObaArrivalInfoRequest.Builder(getTargetContext(),
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

        adapterA = new ArrivalsListAdapterStyleA(getTargetContext());
        adapterA.setData(arrivals, new ArrayList<String>(), response.getCurrentTime());
        View v = adapterA.getView(0, null, null);
        assertNotNull(v);

        // Test occupancy content description
        ViewGroup occupancy = v.findViewById(R.id.occupancy);

        // Historical
        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.NOT_ACCEPTING_PASSENGERS, OccupancyState.HISTORICAL);
        assertEquals("Historically full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.FULL, OccupancyState.HISTORICAL);
        assertEquals("Historically full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.CRUSHED_STANDING_ROOM_ONLY, OccupancyState.HISTORICAL);
        assertEquals("Historically full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.STANDING_ROOM_ONLY, OccupancyState.HISTORICAL);
        assertEquals("Historically standing room", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.FEW_SEATS_AVAILABLE, OccupancyState.HISTORICAL);
        assertEquals("Historically seats available", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.MANY_SEATS_AVAILABLE, OccupancyState.HISTORICAL);
        assertEquals("Historically seats available", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.EMPTY, OccupancyState.HISTORICAL);
        assertEquals("Historically empty", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, null, OccupancyState.HISTORICAL);
        assertEquals("", occupancy.getContentDescription());

        // Real-time
        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.NOT_ACCEPTING_PASSENGERS, OccupancyState.REALTIME);
        assertEquals("Full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.FULL, OccupancyState.REALTIME);
        assertEquals("Full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.CRUSHED_STANDING_ROOM_ONLY, OccupancyState.REALTIME);
        assertEquals("Full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.STANDING_ROOM_ONLY, OccupancyState.REALTIME);
        assertEquals("Standing room", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.FEW_SEATS_AVAILABLE, OccupancyState.REALTIME);
        assertEquals("Seats available", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.MANY_SEATS_AVAILABLE, OccupancyState.REALTIME);
        assertEquals("Seats available", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.EMPTY, OccupancyState.REALTIME);
        assertEquals("Empty", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, null, OccupancyState.REALTIME);
        assertEquals("", occupancy.getContentDescription());

        // Predicted
        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.NOT_ACCEPTING_PASSENGERS, OccupancyState.PREDICTED);
        assertEquals("Predicted full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.FULL, OccupancyState.PREDICTED);
        assertEquals("Predicted full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.CRUSHED_STANDING_ROOM_ONLY, OccupancyState.PREDICTED);
        assertEquals("Predicted full", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.STANDING_ROOM_ONLY, OccupancyState.PREDICTED);
        assertEquals("Predicted standing room", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.FEW_SEATS_AVAILABLE, OccupancyState.PREDICTED);
        assertEquals("Predicted seats available", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.MANY_SEATS_AVAILABLE, OccupancyState.PREDICTED);
        assertEquals("Predicted seats available", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, Occupancy.EMPTY, OccupancyState.PREDICTED);
        assertEquals("Predicted empty", occupancy.getContentDescription());

        UIUtils.setOccupancyContentDescription(occupancy, null, OccupancyState.PREDICTED);
        assertEquals("", occupancy.getContentDescription());
    }
}
