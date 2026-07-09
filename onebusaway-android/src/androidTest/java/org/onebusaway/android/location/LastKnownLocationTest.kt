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
package org.onebusaway.android.location

import android.location.Location
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.app.di.LocationEntryPoint
import org.onebusaway.android.time.ElapsedTime
import org.onebusaway.android.util.PermissionUtils
import org.onebusaway.android.util.PermissionUtils.LOCATION_PERMISSIONS
import org.onebusaway.android.util.TestUtils
import org.onebusaway.android.util.describeLocation
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

/**
 * Tests that the app can fetch a fresh last-known location through [LocationEntryPoint]. These are
 * device-only (they need a real GPS fix), so each guards on emulator + permission state.
 */
@RunWith(AndroidJUnit4::class)
class LastKnownLocationTest {

    private val targetContext =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testLocationApiV1() {
        // Skip on emulators (null location) and when location permission is not granted.
        if (TestUtils.isRunningOnEmulator() ||
            !PermissionUtils.hasGrantedAllPermissions(targetContext, LOCATION_PERMISSIONS)
        ) {
            return
        }
        // Retrieves the last known location from any available source — the fused provider when
        // Google Play Services is present, otherwise the framework Location API v1 providers.
        val loc = LocationEntryPoint.get(targetContext).lastKnownLocation()
        // Can fail on some devices (e.g., a fresh reboot without a network connection).
        assertNotNull(loc)
        Log.d(TAG, "Location Provider for Location API v1 test is '${loc!!.provider}'")
        assertFreshLocation(loc)
    }

    @Test
    fun testLocationServices() {
        val api = GoogleApiAvailability.getInstance()
        if (api.isGooglePlayServicesAvailable(targetContext) != ConnectionResult.SUCCESS ||
            TestUtils.isRunningOnEmulator() ||
            !PermissionUtils.hasGrantedAllPermissions(targetContext, LOCATION_PERMISSIONS)
        ) {
            return
        }
        // Could return either a fused or Location API v1 location.
        val loc = LocationEntryPoint.get(targetContext).lastKnownLocation()
        assertNotNull(loc)
        Log.d(TAG, "Location Provider for Location Services test is '${loc!!.provider}'")
        assertFreshLocation(loc)
    }

    /** Asserts the location is fairly recent (within [FRESH_LOCATION_THRESHOLD] — see #737). */
    private fun assertFreshLocation(location: Location) {
        val now = ElapsedTime.now()
        val fixTime = ElapsedTime(TimeUnit.NANOSECONDS.toMillis(location.elapsedRealtimeNanos))
        Log.d(TAG, "Location from ${describeLocation(location, now)}")
        // Monotonic elapsed-realtime clock, so the interval is skew-free.
        assertTrue(now - fixTime <= FRESH_LOCATION_THRESHOLD)
    }

    companion object {
        private const val TAG = "LastKnownLocationTest"

        // Within the last 24 hours - see #737
        private val FRESH_LOCATION_THRESHOLD = 24.hours
    }
}
