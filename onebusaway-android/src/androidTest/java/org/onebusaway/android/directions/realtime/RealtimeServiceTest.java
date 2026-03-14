/*
 * Copyright (C) 2026 Amit-Matth
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

package org.onebusaway.android.directions.realtime;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.directions.util.CustomAddress;
import org.onebusaway.android.directions.util.OTPConstants;
import org.onebusaway.android.directions.util.TripRequestBuilder;
import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;

import java.util.ArrayList;
import java.util.Date;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Regression test for RealtimeService.
 */
@RunWith(AndroidJUnit4.class)
public class RealtimeServiceTest {

    private RealtimeService mService;

    @Before
    public void setUp() {
        mService = new RealtimeService() {
            @Override
            public Context getApplicationContext() {
                return InstrumentationRegistry.getTargetContext();
            }

            @Override
            public String getPackageName() {
                return InstrumentationRegistry.getTargetContext().getPackageName();
            }

            @Override
            public Object getSystemService(String name) {
                return InstrumentationRegistry.getTargetContext().getSystemService(name);
            }
        };
    }

    @Test
    public void testRescheduleRealtimeUpdatesWithNullStart() {
        Bundle bundle = new Bundle();
        boolean result = mService.rescheduleRealtimeUpdates(bundle);
        assertFalse("Realtime updates should not be rescheduled when start time is null", result);
    }

    @Test
    public void testRescheduleRealtimeUpdatesWithFutureStart() {
        Bundle bundle = new Bundle();
        // Set start time to 2 hours in the future (Query window is 1 hour)
        long futureTime = System.currentTimeMillis() + (OTPConstants.REALTIME_SERVICE_QUERY_WINDOW * 2);
        new TripRequestBuilder(bundle).setDateTime(new Date(futureTime));

        boolean result = mService.rescheduleRealtimeUpdates(bundle);
        assertTrue("Realtime updates should be rescheduled for future trips", result);
    }

    @Test
    public void testRescheduleRealtimeUpdatesWithNearStart() {
        Bundle bundle = new Bundle();
        // Set start time to 30 minutes in the future (within 1 hour window)
        long nearTime = System.currentTimeMillis() + (OTPConstants.REALTIME_SERVICE_QUERY_WINDOW / 2);
        new TripRequestBuilder(bundle).setDateTime(new Date(nearTime));

        boolean result = mService.rescheduleRealtimeUpdates(bundle);
        assertFalse("Realtime updates should not be rescheduled if trip starts soon", result);
    }

    @Test
    public void testGetItinerarySafety() {
        Bundle bundle = new Bundle();

        // Test with empty bundle
        assertNull("getItinerary should return null for empty bundle", mService.getItinerary(bundle));

        // Test with null itineraries list
        bundle.putSerializable(OTPConstants.ITINERARIES, null);
        assertNull("getItinerary should return null for null itineraries", mService.getItinerary(bundle));

        // Test with empty itineraries list
        bundle.putSerializable(OTPConstants.ITINERARIES, new ArrayList<Itinerary>());
        assertNull("getItinerary should return null for empty list", mService.getItinerary(bundle));

        // Test with valid itinerary
        ArrayList<Itinerary> list = new ArrayList<>();
        Itinerary it = new Itinerary();
        list.add(it);
        bundle.putSerializable(OTPConstants.ITINERARIES, list);
        bundle.putInt(OTPConstants.SELECTED_ITINERARY, 0);

        assertEquals("getItinerary should return the selected itinerary", it, mService.getItinerary(bundle));

        // Test with out of bounds index (positive)
        bundle.putInt(OTPConstants.SELECTED_ITINERARY, 1);
        assertNull("getItinerary should return null for out of bounds index", mService.getItinerary(bundle));

        // Test with out of bounds index (negative)
        bundle.putInt(OTPConstants.SELECTED_ITINERARY, -1);
        assertNull("getItinerary should return null for negative index", mService.getItinerary(bundle));
    }

    @Test
    public void testOnHandleIntentWithEmptyBundleDoesNotCrash() {
        Intent intent = new Intent(OTPConstants.INTENT_START_CHECKS);
        try {
            mService.onHandleIntent(intent);
        } catch (NullPointerException e) {
            fail("onHandleIntent should not throw NPE on empty bundle: " + e.getMessage());
        }
        // Other exceptions propagate naturally and fail the test visibly

        // Test with null action
        Intent nullActionIntent = new Intent();
        try {
            mService.onHandleIntent(nullActionIntent);
        } catch (NullPointerException e) {
            fail("onHandleIntent should not throw NPE on null action: " + e.getMessage());
        }
    }

    @Test
    public void testGetSimplifiedBundleWithMissingItineraryDoesNotCrash() throws Exception {
        // Bundle with no itineraries and no selected index
        Bundle badBundle = new Bundle();

        // Use reflection because getSimplifiedBundle() is private
        java.lang.reflect.Method m =
                RealtimeService.class.getDeclaredMethod("getSimplifiedBundle", Bundle.class);
        m.setAccessible(true);

        Object result = m.invoke(mService, badBundle);
        // After the fix, getSimplifiedBundle() should handle the null itinerary gracefully
        // and return null instead of crashing.
        assertNull("getSimplifiedBundle should return null for missing itinerary", result);
    }

    @Test
    public void testGetSimplifiedBundleWithMissingNotificationTargetDoesNotCrash() throws Exception {
        Bundle bundle = new Bundle();

        // Minimal from/to and date so TripRequestBuilder.copyIntoBundleSimple() does not throw.
        CustomAddress from = new CustomAddress();
        from.setLatitude(0);
        from.setLongitude(0);
        CustomAddress to = new CustomAddress();
        to.setLatitude(0);
        to.setLongitude(0);
        new TripRequestBuilder(bundle).setFrom(from).setTo(to).setDateTime(new Date());

        // Build a valid itinerary with one transit leg so ItineraryDescription succeeds
        // and we actually reach the NOTIFICATION_TARGET check (not the catch block).
        Itinerary it = new Itinerary();
        Leg leg = new Leg();
        leg.mode = "BUS";
        leg.realTime = true;
        leg.tripId = "testTrip";
        leg.endTime = String.valueOf(System.currentTimeMillis() + 3600000);
        it.legs = new ArrayList<>();
        it.legs.add(leg);

        ArrayList<Itinerary> itineraries = new ArrayList<>();
        itineraries.add(it);
        bundle.putSerializable(OTPConstants.ITINERARIES, itineraries);
        bundle.putInt(OTPConstants.SELECTED_ITINERARY, 0);
        // Intentionally DO NOT put OTPConstants.NOTIFICATION_TARGET into the bundle

        java.lang.reflect.Method m =
                RealtimeService.class.getDeclaredMethod("getSimplifiedBundle", Bundle.class);
        m.setAccessible(true);

        Object result = m.invoke(mService, bundle);
        // After the fix, getSimplifiedBundle() should handle missing NOTIFICATION_TARGET
        // gracefully and return null instead of crashing.
        assertNull("getSimplifiedBundle should return null when NOTIFICATION_TARGET is missing", result);
    }

    @Test
    public void testOnHandleIntentWithNullItineraryLegsDoesNotCrash() {
        Bundle bundle = new Bundle();
        // No DATE_TIME in bundle so rescheduleRealtimeUpdates() returns false
        // and the service proceeds to getItinerary() then startRealtimeUpdates().
        ArrayList<Itinerary> itineraries = new ArrayList<>();
        Itinerary it = new Itinerary();
        it.legs = null;
        itineraries.add(it);
        bundle.putSerializable(OTPConstants.ITINERARIES, itineraries);
        bundle.putInt(OTPConstants.SELECTED_ITINERARY, 0);

        Intent intent = new Intent(OTPConstants.INTENT_START_CHECKS);
        intent.putExtras(bundle);

        try {
            mService.onHandleIntent(intent);
        } catch (NullPointerException e) {
            fail("onHandleIntent should not throw NPE when itinerary.legs is null: " + e.getMessage());
        }
    }
}
