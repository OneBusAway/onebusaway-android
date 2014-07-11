package com.joulespersecond.oba.util.test;

import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.joulespersecond.seattlebusbot.util.LocationHelp;
import com.joulespersecond.seattlebusbot.util.TestHelp;

/**
 * Tests to evaluate location utilities
 */
public class LocationUtilTest extends AndroidTestCase {

    public static final String TAG = "LocationUtilTest";
    public static final long FRESH_LOCATION_THRESHOLD_MS = 1000 * 60 * 5;  // Within last 5 minutes

    /**
     * Google Location Services
     */
    LocationClient mLocationClient;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext()) == ConnectionResult.SUCCESS) {
            LocationHelp.LocationServicesCallback locCallback = new LocationHelp.LocationServicesCallback();
            mLocationClient = new LocationClient(getContext(), locCallback, locCallback);
            mLocationClient.connect();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Tear down LocationClient
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.disconnect();
        }
    }

    public void testLocationComparison() {
        boolean result;
        Location a;
        Location b;

        // Test that non-null location is preferred
        a = new Location("test");
        b = null;
        result = LocationHelp.compareLocations(a, b);
        assertTrue(result);

        a = null;
        b = new Location("test");
        result = LocationHelp.compareLocations(a, b);
        assertFalse(result);

        // Test that location with greater (i.e., newer) timestamp is preferred
        a = new Location("test");
        a.setTime(1001);
        b = new Location("test");
        b.setTime(1000);

        result = LocationHelp.compareLocations(a, b);
        assertTrue(result);

        a.setTime(1000);
        b.setTime(1001);

        result = LocationHelp.compareLocations(a, b);
        assertFalse(result);
    }

    public void testLocationApiV1() {
        Location loc;

        // Make sure we're not running on an emulator, since we'll get a null location there
        if (!TestHelp.isRunningOnEmulator()) {
            /**
             * Test without Google Play Services - should be a Location API v1 location.
             * Typically this is "gps" or "network", but some devices (e.g., HTC EVO LTE)
             * have custom Android framework providers such as "hybrid" that might should up here.
             * So, we can't test for "gps" or "network" specifically.
             */
            loc = LocationHelp.getLocation2(getContext(), null);
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

    public void testLocationServices() {
        Location loc;

        // Test with Google Play Services, if its supported, and if we're not running on an emulator
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext()) == ConnectionResult.SUCCESS &&
                !TestHelp.isRunningOnEmulator()) {
            /**
             * Could return either a fused or Location API v1 location
             */
            loc = LocationHelp.getLocation2(getContext(), mLocationClient);
            assertNotNull(loc);
            Log.d(TAG, "Location Provider for Location Services test is '" + loc.getProvider() + "'");
            assertFreshLocation(loc);
        }
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
            Log.d(TAG, "Location from " + LocationHelp.printLocationDetails(location));
            // Use elapsed real-time nanos, since its guaranteed monotonic
            return timeDiff <= (FRESH_LOCATION_THRESHOLD_MS * 1E6);
        } else {
            timeDiff = System.currentTimeMillis() - location.getTime();
            Log.d(TAG, "Location from " + LocationHelp.printLocationDetails(location));
            return timeDiff <= FRESH_LOCATION_THRESHOLD_MS;
        }
    }
}
