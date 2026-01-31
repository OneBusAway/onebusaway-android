/*
 * Copyright (C) 2025 Francesc Josep Vilarino Guell (francesc@francescsoftware.com)
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

import org.junit.After;
import org.junit.Test;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.JacksonSerializer;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.Occupancy;
import org.onebusaway.android.mock.MockRegion;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

public class ObaArrivalInfoTest extends ObaTestCase {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @After
    public void tearDown() {
        Application.get().setCurrentRegion(null);
    }

    @Test
    public void testGetPredicted_PredictedNull_PredictedArrivalTimeSet() {
        String json = "{\"predictedArrivalTime\": 123456789}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertTrue(info.getPredicted());
    }

    @Test
    public void testGetPredicted_PredictedNull_PredictedDepartureTimeSet() {
        String json = "{\"predictedDepartureTime\": 123456789}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertTrue(info.getPredicted());
    }

    @Test
    public void testGetPredicted_PredictedNull_BothTimesZero() {
        String json = "{}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertFalse(info.getPredicted());
    }

    @Test
    public void testGetPredicted_PredictedTrue_PredictedArrivalTimeSet() {
        String json = "{\"predicted\": true, \"predictedArrivalTime\": 123456789}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertTrue(info.getPredicted());
    }

    @Test
    public void testGetPredicted_PredictedTrue_BothTimesZero() {
        String json = "{\"predicted\": true}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertFalse(info.getPredicted());
    }

    @Test
    public void testGetPredicted_PredictedFalse() {
        String json = "{\"predicted\": false, \"predictedArrivalTime\": 123456789}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertFalse(info.getPredicted());
    }

    @Test
    public void testGetNumCars_PugetSound_TwoCars() {
        ObaRegion ps = MockRegion.getPugetSound(context);
        Application.get().setCurrentRegion(ps);

        String json = "{\"vehicleId\": \"Train[123-456]\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);

        String numCars = info.getNumCars(context);
        assertNotNull(numCars);
        assertTrue(numCars.contains("2"));
    }

    @Test
    public void testGetNumCars_PugetSound_ThreeCars() {
        ObaRegion ps = MockRegion.getPugetSound(context);
        Application.get().setCurrentRegion(ps);

        String json = "{\"vehicleId\": \"Train[123-456-789]\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        String numCars = info.getNumCars(context);
        assertNotNull(numCars);
        assertTrue(numCars.contains("3"));
    }

    @Test
    public void testGetNumCars_PugetSound_InvalidFormat() {
        ObaRegion ps = MockRegion.getPugetSound(context);
        Application.get().setCurrentRegion(ps);

        String json = "{\"vehicleId\": \"Train123\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        String numCars = info.getNumCars(context);
        assertNull(numCars);
    }

    @Test
    public void testGetNumCars_NonPugetSound() {
        ObaRegion tampa = MockRegion.getTampa(context);
        Application.get().setCurrentRegion(tampa);

        String json = "{\"vehicleId\": \"Train[123-456]\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        String numCars = info.getNumCars(context);
        assertNull(numCars);
    }

    @Test
    public void testGetNumCars_NullRegion() {
        Application.get().setCurrentRegion(null);
        String json = "{\"vehicleId\": \"Train[123-456]\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        String numCars = info.getNumCars(context);
        assertNull(numCars);
    }

    @Test
    public void testGetOccupancy_BothSet() {
        String json = "{\"historicalOccupancy\": \"MANY_SEATS_AVAILABLE\", " +
                "\"occupancyStatus\": \"STANDING_ROOM_ONLY\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);

        assertEquals(Occupancy.MANY_SEATS_AVAILABLE, info.getHistoricalOccupancy());
        assertEquals(Occupancy.STANDING_ROOM_ONLY, info.getOccupancyStatus());
    }

    @Test
    public void testGetOccupancy_UnknownHistorical() {
        String json = "{\"historicalOccupancy\": \"unknown\", \"occupancyStatus\": \"FULL\"}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);

        assertNull(info.getHistoricalOccupancy());
        assertEquals(Occupancy.FULL, info.getOccupancyStatus());
    }

    @Test
    public void testGetOccupancy_Null() {
        String json = "{}";
        ObaArrivalInfo info = JacksonSerializer.getInstance()
                .deserializeFromResponse(json, ObaArrivalInfo.class);
        assertNull(info.getHistoricalOccupancy());
        assertNull(info.getOccupancyStatus());
    }
}
