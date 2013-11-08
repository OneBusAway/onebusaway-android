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
import com.joulespersecond.oba.elements.ObaShapeElement;

public class ShapeTest extends AndroidTestCase {

    public void testDecodeLines() {
        List<GeoPoint> list = ObaShapeElement.decodeLine("_p~iF~ps|U", 1);
        assertNotNull(list);
        assertEquals(1, list.size());
        GeoPoint pt = list.get(0);
        assertEquals(38500000, pt.getLatitudeE6());
        assertEquals(-120200000, pt.getLongitudeE6());

        list = ObaShapeElement.decodeLine("_p~iF~ps|U_ulLnnqC", 2);
        assertNotNull(list);
        assertEquals(list.size(), 2);
        pt = list.get(0);
        assertEquals(38500000, pt.getLatitudeE6());
        assertEquals(-120200000, pt.getLongitudeE6());
        pt = list.get(1);
        assertEquals(40700000, pt.getLatitudeE6());
        assertEquals(-120950000, pt.getLongitudeE6());

        list = ObaShapeElement.decodeLine("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3);
        assertNotNull(list);
        assertEquals(3, list.size());
        pt = list.get(2);
        assertEquals(43252000, pt.getLatitudeE6());
        assertEquals(-126453000, pt.getLongitudeE6());
    }
    public void testDecodeLevels() {
        List<Integer> list = ObaShapeElement.decodeLevels("mD", 1);
        assertNotNull(list);
        assertEquals(1, list.size());
        Integer i = list.get(0);
        assertEquals(174, (int)i);

        list = ObaShapeElement.decodeLevels("BBBB", 4);
        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(3, (int)list.get(0));
        assertEquals(3, (int)list.get(1));
        assertEquals(3, (int)list.get(2));
        assertEquals(3, (int)list.get(3));

    }
}
