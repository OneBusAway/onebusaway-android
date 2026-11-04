package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Test

class EtaStripStartTest {

    private fun start(vararg etas: Long) = firstUpcomingIndex(etas.toList()) { it >= 0 }

    @Test
    fun mixedDepartedAndUpcoming() = assertEquals(2, start(-3, -1, 0, 4))

    @Test
    fun zeroCountsAsUpcoming() = assertEquals(1, start(-1, 0))

    @Test
    fun allDepartedOpensAtFirst() = assertEquals(0, start(-5, -2))

    @Test
    fun noneDepartedOpensAtFirst() = assertEquals(0, start(1, 5))

    @Test
    fun emptyList() = assertEquals(0, start())
}
