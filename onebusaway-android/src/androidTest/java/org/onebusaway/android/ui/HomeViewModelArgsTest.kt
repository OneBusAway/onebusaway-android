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
package org.onebusaway.android.ui

import android.content.Intent
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.map.MapParams
import org.onebusaway.android.ui.nav.NavRoutes

@RunWith(AndroidJUnit4::class)
class HomeViewModelArgsTest {

    @Test
    fun preservesRouteAndCameraSeedsWithTheirOriginalTypes() {
        val seed = Bundle().apply {
            putString(MapParams.ROUTE_ID, "route-1")
            putString(MapParams.ROUTE_DIRECTION_STOP_ID, "stop-1")
            putInt(MapParams.ROUTE_DIRECTION_ID, 0)
            putBoolean(MapParams.ZOOM_TO_ROUTE, false)
            putDouble(MapParams.CENTER_LAT, 0.0)
            putDouble(MapParams.CENTER_LON, -122.3)
            putFloat(MapParams.ZOOM, 14f)
        }
        val incoming = Bundle(seed).apply {
            putString(NavRoutes.EXTRA_NAV_ROUTE, NavRoutes.SETTINGS)
            putString("unrelated", "not ViewModel state")
        }
        val handle = SavedStateHandle.createHandle(null, homeViewModelArgs(incoming))

        assertEquals(seed.keySet(), handle.keys())
        assertEquals("route-1", handle.get<String>(MapParams.ROUTE_ID))
        assertEquals("stop-1", handle.get<String>(MapParams.ROUTE_DIRECTION_STOP_ID))
        assertEquals(0, handle.get<Int>(MapParams.ROUTE_DIRECTION_ID))
        assertEquals(false, handle.get<Boolean>(MapParams.ZOOM_TO_ROUTE))
        assertEquals(0.0, handle.get<Double>(MapParams.CENTER_LAT))
        assertEquals(-122.3, handle.get<Double>(MapParams.CENTER_LON))
        assertEquals(14f, handle.get<Float>(MapParams.ZOOM))
        assertEquals(NavRoutes.SETTINGS, incoming.getString(NavRoutes.EXTRA_NAV_ROUTE))
    }

    @Test
    fun absentAndMistypedSeedsStayAbsent() {
        assertTrue(homeViewModelArgs(null).isEmpty)
        val incoming = Bundle().apply {
            putInt(MapParams.ROUTE_ID, 7)
            putString(MapParams.ROUTE_DIRECTION_ID, "1")
            putString(MapParams.ZOOM_TO_ROUTE, "true")
            putFloat(MapParams.CENTER_LAT, 47f)
            putDouble(MapParams.ZOOM, 14.0)
        }
        assertTrue(homeViewModelArgs(incoming).isEmpty)
    }

    @Test
    fun restoredStateWinsOverLaunchDefaults() {
        val restored = Bundle().apply {
            putString(MapParams.ROUTE_ID, "restored-route")
            putString("saved-focus", "stop-2")
        }
        val defaults = homeViewModelArgs(
            Bundle().apply {
                putString(MapParams.ROUTE_ID, "launch-route")
                putFloat(MapParams.ZOOM, 14f)
            }
        )
        val handle = SavedStateHandle.createHandle(restored, defaults)

        assertEquals("restored-route", handle.get<String>(MapParams.ROUTE_ID))
        assertEquals("stop-2", handle.get<String>("saved-focus"))
        assertFalse(handle.contains(MapParams.ZOOM))
    }

    @Test
    @SdkSuppress(minSdkVersion = 33) // Earlier Bundles eagerly decode all values together.
    fun foreignParcelableListCrashesRawDefaultsButIsNeverReadByFilteredDefaults() {
        val incoming = parcelledForeignIntent().apply {
            putExtra(MapParams.CENTER_LAT, 47.6)
            putExtra(MapParams.CENTER_LON, -122.3)
        }
        // Negative control: without a real parcel round trip this would just read the Java object.
        assertThrows(BadParcelableException::class.java) {
            SavedStateHandle.createHandle(null, incoming.extras)
        }
        val handle = SavedStateHandle.createHandle(null, homeViewModelArgs(incoming.extras))

        assertEquals(47.6, handle.get<Double>(MapParams.CENTER_LAT))
        assertEquals(-122.3, handle.get<Double>(MapParams.CENTER_LON))
        assertFalse(handle.contains(FOREIGN_PARAMETERS))
        assertEquals(NavRoutes.SETTINGS, incoming.getStringExtra(NavRoutes.EXTRA_NAV_ROUTE))
    }

    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun unreadableValueUnderAKnownKeyDoesNotDiscardOtherSeeds() {
        val incoming = parcelledForeignIntent(MapParams.ROUTE_ID).apply {
            putExtra(MapParams.CENTER_LAT, 47.6)
        }
        val args = homeViewModelArgs(incoming.extras)

        assertFalse(args.containsKey(MapParams.ROUTE_ID))
        assertEquals(47.6, args.getDouble(MapParams.CENTER_LAT), 0.0)
    }
}

internal const val FOREIGN_PARAMETERS = "foreign-parameters"

/** Exercises Android's lazy list unmarshalling, matching the foreign Parcelable in Play's report. */
internal fun parcelledForeignIntent(key: String = FOREIGN_PARAMETERS): Intent {
    val intent = Intent()
        .putExtra(NavRoutes.EXTRA_NAV_ROUTE, NavRoutes.SETTINGS)
        .putParcelableArrayListExtra(key, arrayListOf(UnreadableParameter()))
    val parcel = Parcel.obtain()
    return try {
        intent.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        Intent.CREATOR.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}

class UnreadableParameter : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(1)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<UnreadableParameter> = object : Parcelable.Creator<UnreadableParameter> {
            override fun createFromParcel(source: Parcel): UnreadableParameter = throw BadParcelableException(ClassNotFoundException("foreign.app.Parameter"))

            override fun newArray(size: Int): Array<UnreadableParameter?> = arrayOfNulls(size)
        }
    }
}
