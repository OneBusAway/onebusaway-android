package org.onebusaway.android.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDayTimeTest {
    @Test
    fun `parses hours past midnight`() {
        assertEquals(ServiceDayTime(0), ServiceDayTime.parse("00:00:00"))
        assertEquals(ServiceDayTime(5 * 3600), ServiceDayTime.parse("05:00:00"))
        assertEquals(ServiceDayTime(24 * 3600 + 50 * 60), ServiceDayTime.parse("24:50:00"))
        assertEquals(ServiceDayTime(25 * 3600), ServiceDayTime.parse("25:00:00"))
        assertTrue(ServiceDayTime.parse("24:50:00") < ServiceDayTime.parse("25:00:00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a malformed time`() {
        ServiceDayTime.parse("5pm")
    }
}
