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
