/*
* Copyright (c) 2017, Microsoft Corporation.
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

import org.onebusaway.android.util.EmbeddedSocialUtils;

import android.test.AndroidTestCase;

/**
 * Tests embedded social utilities
 */
public class EmbeddedSocialUtilTest extends AndroidTestCase {

    public void testDiscussionTitles() {
        long regionId;
        String stopId;
        String actual;
        String expected;

        regionId = 0;
        stopId = "Hello world \\ # % + / ? \u0082";
        actual = EmbeddedSocialUtils.createStopDiscussionTitle(regionId, stopId);
        expected = "stop_0_Hello world XA== Iw== JQ== Kw== Lw== Pw== woI=";
        assertEquals(expected, actual);
    }
}
