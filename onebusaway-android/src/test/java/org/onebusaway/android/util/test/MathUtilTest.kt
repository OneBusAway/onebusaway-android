package org.onebusaway.android.util.test

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.util.MathUtils

class MathUtilTest {
    @Test
    fun orientationToDirection() {
        assertEquals(90.0, MathUtils.toDirection(0.0), 0.0)
        assertEquals(0.0, MathUtils.toDirection(90.0), 0.0)
        assertEquals(270.0, MathUtils.toDirection(180.0), 0.0)
        assertEquals(180.0, MathUtils.toDirection(270.0), 0.0)
    }
}
