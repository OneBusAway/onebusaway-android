/*
 * Copyright (C) 2017 University of South Florida (sjbarbeau@gmail.com)
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.util.LocationUtils;
import org.onebusaway.android.util.PermissionUtils;
import org.onebusaway.android.util.TestUtils;

import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.onebusaway.android.util.PermissionUtils.LOCATION_PERMISSIONS;

/**
 * Tests to evaluate location utilities
 */
@RunWith(AndroidJUnit4.class)
public class LocationUtilsTest extends ObaTestCase {

    public static final String TAG = "LocationUtilTest";

    public static final long FRESH_LOCATION_THRESHOLD_MS = 1000 * 60 * 60 * 24;
            // Within last 24 hours - see #737

    /**
     * GoogleApiClient being used for Location Services
     */
    GoogleApiClient mGoogleApiClient;

    @Before
    public void before() {
        super.before();

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(getTargetContext())
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(getTargetContext());
            mGoogleApiClient.connect();
        }
    }

    @After
    public void after() {
        super.after();

        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Test
    public void testLocationComparisonByTime() {
        boolean result;
        Location a;
        Location b;

        // Test that non-null location is preferred
        a = new Location("test");
        b = null;
        result = LocationUtils.compareLocationsByTime(a, b);
        assertTrue(result);

        a = null;
        b = new Location("test");
        result = LocationUtils.compareLocationsByTime(a, b);
        assertFalse(result);

        // Test that location with greater (i.e., newer) timestamp is preferred
        a = new Location("test");
        a.setTime(1001);
        b = new Location("test");
        b.setTime(1000);

        result = LocationUtils.compareLocationsByTime(a, b);
        assertTrue(result);

        a.setTime(1000);
        b.setTime(1001);

        result = LocationUtils.compareLocationsByTime(a, b);
        assertFalse(result);
    }

    @Test
    public void testLocationComparison() {
        boolean result;
        Location a;
        Location b;

        // Test that non-null location is preferred
        a = new Location("test");
        b = null;
        result = LocationUtils.compareLocations(a, b);
        assertTrue(result);

        a = null;
        b = new Location("test");
        result = LocationUtils.compareLocations(a, b);
        assertFalse(result);

        long time = System
                .currentTimeMillis();  // We have to use current time, because of the age threshold evaluation

        // We always want the newer location
        a = new Location("test");
        a.setTime(time + 1);
        b = new Location("test");
        b.setTime(time);

        result = LocationUtils.compareLocations(a, b);
        assertTrue(result);

        a.setTime(time);
        b.setTime(time + 1);

        result = LocationUtils.compareLocations(a, b);
        assertFalse(result);

        // Test that the new location would be saved if the old location is older than the time
        // threshold, even if the accuracy is worse
        a = new Location("test");
        a.setTime(time);  // A is newer
        a.setAccuracy(LocationUtils.ACC_THRESHOLD + 1);  // 1 meter worse than threshold
        b = new Location("test");
        b.setAccuracy(LocationUtils.ACC_THRESHOLD - 1);  // 1 meter better than threshold
        b.setTime(time - LocationUtils.TIME_THRESHOLD - 1);  // older than time threshold

        result = LocationUtils.compareLocations(a, b);
        assertTrue(result);

        // A is older, so this should fail, since we never want an older location
        a = new Location("test");
        a.setTime(time - LocationUtils.TIME_THRESHOLD - 2);  // A is older
        a.setAccuracy(LocationUtils.ACC_THRESHOLD + 1);  // 1 meter worse than threshold
        b = new Location("test");
        b.setAccuracy(LocationUtils.ACC_THRESHOLD - 1);  // 1 meter better than threshold
        b.setTime(time - LocationUtils.TIME_THRESHOLD - 1);  // older than time threshold

        result = LocationUtils.compareLocations(a, b);
        assertFalse(result);

        // Test that location with greater (i.e., newer) timestamp is preferred, as long as it has
        // a reasonable accuracy
        a = new Location("test");
        a.setTime(time + 1);  // A is newer
        a.setAccuracy(LocationUtils.ACC_THRESHOLD - 1);  // 1 meter better than threshold
        b = new Location("test");
        b.setTime(time);

        result = LocationUtils.compareLocations(a, b);
        assertTrue(result);

        a = new Location("test");
        a.setTime(time + 1);  // A is newer
        a.setAccuracy(LocationUtils.ACC_THRESHOLD + 1);  // 1 meter worse than threshold
        b = new Location("test");
        b.setTime(time);

        result = LocationUtils.compareLocations(a, b);
        assertFalse(result);
    }

    @Test
    public void testLocationApiV1() {
        Location loc;

        // Make sure we're not running on an emulator, since we'll get a null location there
        if (!TestUtils.isRunningOnEmulator() && PermissionUtils.hasGrantedPermissions(getTargetContext(), LOCATION_PERMISSIONS)) {
            /**
             * Test without Google Play Services - should be a Location API v1 location.
             * Typically this is "gps" or "network", but some devices (e.g., HTC EVO LTE)
             * have custom Android framework providers such as "hybrid" that might should up here.
             * So, we can't test for "gps" or "network" specifically.
             */
            loc = Application.getLastKnownLocation(getTargetContext(), null);
            /**
             * On devices that behave correctly the following non-null test should pass.  However, it's
             * possible that it can fail on some devices (e.g., on a fresh reboot on a device without
             * a network connection)
             */
            assertNotNull(loc);
            Log.d(TAG, "Location Provider for Location API v1 test is '" + loc.getProvider() + "'");
            assertFreshLocation(loc);
        }
    }

    @Test
    public void testLocationServices() {
        Location loc;

        // Test with Google Play Services, if its supported, and if we're not running on an emulator
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(getTargetContext())
                == ConnectionResult.SUCCESS &&
                !TestUtils.isRunningOnEmulator() &&
                PermissionUtils.hasGrantedPermissions(getTargetContext(), LOCATION_PERMISSIONS)) {
            /**
             * Could return either a fused or Location API v1 location
             */
            loc = Application.getLastKnownLocation(getTargetContext(), mGoogleApiClient);
            assertNotNull(loc);
            Log.d(TAG,
                    "Location Provider for Location Services test is '" + loc.getProvider() + "'");
            assertFreshLocation(loc);
        }
    }

    @Test
    public void testIsDuplicate() {
        Location locA = new Location("A");
        locA.setTime(1234);
        locA.setLatitude(33.3);
        locA.setLongitude(66.6);

        /**
         * Test location that is the same
         */

        Location locDupA = new Location("A");
        locDupA.setTime(1234);
        locDupA.setLatitude(33.3);
        locDupA.setLongitude(66.6);

        assertTrue(LocationUtils.isDuplicate(locA, locDupA));

        /**
         * Test locations that aren't the same
         */
        Location locBTimeDiff = new Location("A");
        locBTimeDiff.setTime(9876);
        locBTimeDiff.setLatitude(33.3);
        locBTimeDiff.setLongitude(66.6);

        assertFalse(LocationUtils.isDuplicate(locA, locBTimeDiff));

        Location locBLatDiff = new Location("A");
        locBLatDiff.setTime(1234);
        locBLatDiff.setLatitude(89.9);
        locBLatDiff.setLongitude(66.6);

        assertFalse(LocationUtils.isDuplicate(locA, locBLatDiff));

        Location locBLonDiff = new Location("A");
        locBLonDiff.setTime(1234);
        locBLonDiff.setLatitude(33.3);
        locBLonDiff.setLongitude(10.0);

        assertFalse(LocationUtils.isDuplicate(locA, locBLonDiff));
    }

    /**
     * Tests whether location is fresh (i.e., fairly recent)
     *
     * @param location Location to test
     */
    private void assertFreshLocation(Location location) {
        assertTrue(checkFreshLocation(location));
    }

    /**
     * Returns true if this is a fairly recent location
     *
     * @param location Location to check
     * @return true if this is a fairly recent location, false if it is not.
     */
    private boolean checkFreshLocation(Location location) {
        long timeDiff;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            timeDiff = SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
            Log.d(TAG, "Location from " + LocationUtils.printLocationDetails(location));
            // Use elapsed real-time nanos, since its guaranteed monotonic
            return timeDiff <= (FRESH_LOCATION_THRESHOLD_MS * 1E6);
        } else {
            timeDiff = System.currentTimeMillis() - location.getTime();
            Log.d(TAG, "Location from " + LocationUtils.printLocationDetails(location));
            return timeDiff <= FRESH_LOCATION_THRESHOLD_MS;
        }
    }
}
