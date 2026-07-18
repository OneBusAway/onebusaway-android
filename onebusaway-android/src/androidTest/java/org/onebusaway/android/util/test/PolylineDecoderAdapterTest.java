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
package org.onebusaway.android.util.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.SmokeTest;
import org.onebusaway.android.util.PolylineDecoder;

import android.location.Location;

import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * On-device smoke check for the {@link PolylineDecoder#decodeLine} boundary adapter: the decode
 * <em>algorithm</em> is covered on plain JVM by {@code PolylineDecoderTest} (in {@code src/test}),
 * so this asserts only the thin part that can't run off-device — that the adapter mints a real
 * {@code android.location.Location} per decoded point with the right coordinates.
 */
// API-23 floor smoke subset (#1818): exercises Location minting on the floor.
@SmokeTest
@RunWith(AndroidJUnit4.class)
public class PolylineDecoderAdapterTest {

    @Test
    public void decodeLineMintsLocations() {
        List<Location> list = PolylineDecoder.decodeLine("_p~iF~ps|U_ulLnnqC", 2);
        assertNotNull(list);
        assertEquals(2, list.size());
        Location pt = list.get(0);
        assertEquals(38.5, pt.getLatitude());
        assertEquals(-120.2, pt.getLongitude());
        pt = list.get(1);
        assertEquals(40.7, pt.getLatitude());
        assertEquals(-120.95, pt.getLongitude());
    }
}
