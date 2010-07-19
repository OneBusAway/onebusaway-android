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
package com.joulespersecond.oba.request.test;

import java.util.List;

import android.test.AndroidTestCase;

import com.google.android.maps.GeoPoint;
import com.joulespersecond.oba.ObaPolyline;

public class ShapeTest extends AndroidTestCase {

    public void testDecodeLines() {
        List<GeoPoint> list = ObaPolyline.decodeLine("_p~iF~ps|U", 1);
        assertNotNull(list);
        assertEquals(list.size(), 1);
        GeoPoint pt = list.get(0);
        assertEquals(pt.getLatitudeE6(), 38500000);
        assertEquals(pt.getLongitudeE6(), -120200000);

        list = ObaPolyline.decodeLine("_p~iF~ps|U_ulLnnqC", 2);
        assertNotNull(list);
        assertEquals(list.size(), 2);
        pt = list.get(0);
        assertEquals(pt.getLatitudeE6(), 38500000);
        assertEquals(pt.getLongitudeE6(), -120200000);
        pt = list.get(1);
        assertEquals(pt.getLatitudeE6(), 40700000);
        assertEquals(pt.getLongitudeE6(), -120950000);

        list = ObaPolyline.decodeLine("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3);
        assertNotNull(list);
        assertEquals(list.size(), 3);
        pt = list.get(2);
        assertEquals(pt.getLatitudeE6(), 43252000);
        assertEquals(pt.getLongitudeE6(), -126453000);
    }
    public void testDecodeLevels() {
        List<Integer> list = ObaPolyline.decodeLevels("mD", 1);
        assertNotNull(list);
        assertEquals(list.size(), 1);
        Integer i = list.get(0);
        assertEquals((int)i, 174);

        list = ObaPolyline.decodeLevels("BBBB", 4);
        assertNotNull(list);
        assertEquals(list.size(), 4);
        assertEquals((int)list.get(0), 3);
        assertEquals((int)list.get(1), 3);
        assertEquals((int)list.get(2), 3);
        assertEquals((int)list.get(3), 3);

    }
}
