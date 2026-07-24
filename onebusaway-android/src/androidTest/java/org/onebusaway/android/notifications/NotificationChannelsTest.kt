/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.android.notifications

import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the destination-reminder notification channels are configured so the arrival alerts
 * actually vibrate on API 26+ (#985): the builder's `setVibrate` is ignored once channels own
 * vibration, so the fix lives in the channel definitions.
 *
 * Notification channels only exist on API 26+; below that [NotificationChannels.registerAll] is a
 * no-op and there's nothing to assert. [SdkSuppress] statically gates the whole class to API 26+ so
 * the test runner skips it — and the channel API calls below it — on lower floors.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class NotificationChannelsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        // A channel's importance/vibration are locked to its first registration, so clear any left by
        // a prior run/install to assert the code's intended configuration from a clean slate.
        manager.deleteNotificationChannel(NotificationChannels.DESTINATION_ARRIVAL_ID)
        manager.deleteNotificationChannel(NotificationChannels.DESTINATION_ALERT_ID)
        NotificationChannels.registerAll(context)
    }

    @Test
    fun arrivalChannel_isHighImportance_andVibrates() {
        val channel = requireNotNull(
            manager.getNotificationChannel(NotificationChannels.DESTINATION_ARRIVAL_ID)
        ) { "Destination arrival channel should be registered" }
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue("Arrival channel must vibrate (#985)", channel.shouldVibrate())
        assertNotNull(channel.vibrationPattern)
        assertTrue(
            NotificationChannels.DESTINATION_VIBRATION_PATTERN.contentEquals(channel.vibrationPattern)
        )
    }

    @Test
    fun progressChannel_staysQuiet() {
        // The distance/progress notification re-posts continuously, so its channel must not buzz.
        val channel = requireNotNull(
            manager.getNotificationChannel(NotificationChannels.DESTINATION_ALERT_ID)
        ) { "Destination alert channel should be registered" }
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse("Progress channel must not vibrate", channel.shouldVibrate())
    }
}
