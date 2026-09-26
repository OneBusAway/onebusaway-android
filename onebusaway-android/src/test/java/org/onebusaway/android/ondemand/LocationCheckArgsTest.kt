package org.onebusaway.android.ondemand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

class LocationCheckArgsTest {

    @Test
    fun `sources round-trip through their route values`() {
        for (source in listOf(ProbeSource.Rider, ProbeSource.MapCenter, ProbeSource.Point(null))) {
            assertEquals(source, probeSourceFromRoute(source.toRouteValue()))
        }
        assertNull(probeSourceFromRoute("elsewhere"))
        assertNull(probeSourceFromRoute(null))
    }

    @Test
    fun `a check is rebuilt only when both inside and source are present`() {
        assertEquals(
            LocationCheck(ProbeSource.Rider, true, "Boyne City", GeoPoint(45.05, -85.1)),
            locationCheckFromArgs("true", "rider", "Boyne City", "45.05", "-85.1")
        )
        assertEquals(LocationCheck(ProbeSource.MapCenter, false, null, null), locationCheckFromArgs("false", "center", null, null, null))
        assertEquals(null, locationCheckFromArgs("true", "point", null, "45.05", null)?.point)
        assertNull(locationCheckFromArgs(null, "rider", null, null, null))
        assertNull(locationCheckFromArgs("true", null, null, null, null))
    }
}
