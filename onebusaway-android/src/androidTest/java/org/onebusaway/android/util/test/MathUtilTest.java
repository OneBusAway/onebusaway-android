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

package org.onebusaway.android.util.test;

import org.onebusaway.android.io.test.ObaTestCase;
import org.onebusaway.android.util.MathUtils;

/**
 * Tests to evaluate utility methods related to math conversions
 */
public class MathUtilTest extends ObaTestCase {

    /**
     * Tests conversion from OBA orientation to normal 0-360 degrees direction.
     *
     * From OBA REST API docs for trip status (http://developer.onebusaway.org/modules/onebusaway-application-modules/current/api/where/elements/trip-status.html)
     * :  "orientation - ...0º is east, 90º is north, 180º is west, and 270º is south."
     */
    public void testOrientationToDirection() {
        // East
        double direction = MathUtils.toDirection(0);
        assertEquals(90.0, direction);

        // North
        direction = MathUtils.toDirection(90);
        assertEquals(0.0, direction);

        // West
        direction = MathUtils.toDirection(180);
        assertEquals(270.0, direction);

        // South
        direction = MathUtils.toDirection(270);
        assertEquals(180.0, direction);
    }
}
