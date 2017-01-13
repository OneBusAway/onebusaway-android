/*
 * Copyright (C) 2012 individual contributors.
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

import org.onebusaway.android.util.MyTextUtils;

import android.test.AndroidTestCase;

public class MyTextUtilsTest extends AndroidTestCase {

    public void testTitleCase() {
        assertEquals(null, MyTextUtils.toTitleCase(null));
        assertEquals("E John St & 13th Ave E", MyTextUtils.toTitleCase("E JOHN ST & 13th AVE E"));
        assertEquals("Seattle", MyTextUtils.toTitleCase("SEATTLE"));
        assertEquals("Seattle", MyTextUtils.toTitleCase("Seattle"));
    }

    public void testSentenceCase() {
        assertEquals("Testing sentence case", MyTextUtils.toSentenceCase("Testing sentence case"));
        assertEquals("Testing sentence case again",
                MyTextUtils.toSentenceCase("TESTING SENTENCE CASE AGAIN"));
        assertEquals("Another test", MyTextUtils.toSentenceCase("Another Test"));
        assertEquals("Another test", MyTextUtils.toSentenceCase("Another TEST"));
    }

    public void testIsAllCaps() {
        assertTrue(MyTextUtils.isAllCaps("THIS IS ALL CAPS"));
        assertFalse(MyTextUtils.isAllCaps("THIS IS not ALL CAPS"));
    }
}
