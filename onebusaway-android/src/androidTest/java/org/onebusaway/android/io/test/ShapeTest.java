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
package org.onebusaway.android.io.test;

import org.onebusaway.android.io.elements.ObaShapeElement;

import android.location.Location;
import android.test.AndroidTestCase;

import java.util.List;

public class ShapeTest extends AndroidTestCase {

    public void testDecodeLines() {
        List<Location> list = ObaShapeElement.decodeLine("_p~iF~ps|U", 1);
        assertNotNull(list);
        assertEquals(1, list.size());
        Location pt = list.get(0);
        // Original test used GeoPoint, which is in degrees * 1E6 - fixed below via division
        assertEquals(38500000 / 1E6, pt.getLatitude());
        assertEquals(-120200000 / 1E6, pt.getLongitude());

        list = ObaShapeElement.decodeLine("_p~iF~ps|U_ulLnnqC", 2);
        assertNotNull(list);
        assertEquals(list.size(), 2);
        pt = list.get(0);
        assertEquals(38500000 / 1E6, pt.getLatitude());
        assertEquals(-120200000 / 1E6, pt.getLongitude());
        pt = list.get(1);
        assertEquals(40700000 / 1E6, pt.getLatitude());
        assertEquals(-120950000 / 1E6, pt.getLongitude());

        list = ObaShapeElement.decodeLine("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3);
        assertNotNull(list);
        assertEquals(3, list.size());
        pt = list.get(2);
        assertEquals(43252000 / 1E6, pt.getLatitude());
        assertEquals(-126453000 / 1E6, pt.getLongitude());
    }

    public void testDecodeLevels() {
        List<Integer> list = ObaShapeElement.decodeLevels("mD", 1);
        assertNotNull(list);
        assertEquals(1, list.size());
        Integer i = list.get(0);
        assertEquals(174, (int) i);

        list = ObaShapeElement.decodeLevels("BBBB", 4);
        assertNotNull(list);
        assertEquals(4, list.size());
        assertEquals(3, (int) list.get(0));
        assertEquals(3, (int) list.get(1));
        assertEquals(3, (int) list.get(2));
        assertEquals(3, (int) list.get(3));
    }
}
