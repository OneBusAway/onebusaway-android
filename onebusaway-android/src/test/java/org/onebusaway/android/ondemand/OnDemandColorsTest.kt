package org.onebusaway.android.ondemand

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDemandColorsTest {

    private val brand = 0xFF78AA36.toInt()

    @Test
    fun `a route colour is used opaque and the brand fills in for none`() {
        val colours = resolveServiceColors(listOf(service(id = "a", routeColor = 0xFF112233.toInt()), service(id = "b")), brand)
        assertEquals(0xFF112233.toInt(), colours["a"])
        assertEquals(brand, colours["b"])
    }

    @Test
    fun `collisions take the palette in service id order regardless of list order`() {
        val first = resolveServiceColors(listOf(service(id = "b"), service(id = "a"), service(id = "c")), brand)
        val second = resolveServiceColors(listOf(service(id = "c"), service(id = "a"), service(id = "b")), brand)
        assertEquals(first, second)
        assertEquals(brand, first["a"])
        assertEquals(ONDEMAND_FALLBACK_PALETTE[0], first["b"])
        assertEquals(ONDEMAND_FALLBACK_PALETTE[1], first["c"])
    }

    @Test
    fun `a palette colour already taken by a route colour is skipped`() {
        val colours = resolveServiceColors(listOf(service(id = "a", routeColor = ONDEMAND_FALLBACK_PALETTE[0]), service(id = "b"), service(id = "c")), brand)
        assertEquals(brand, colours["b"])
        assertEquals(ONDEMAND_FALLBACK_PALETTE[1], colours["c"])
    }

    @Test
    fun `text colour prefers the route text colour then contrast`() {
        assertEquals(0xFFFFFF00.toInt(), readableTextColor(0xFF000080.toInt(), preferred = 0xFFFFFF00.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), readableTextColor(0xFF1A3A8A.toInt(), preferred = null))
        assertEquals(0xFF000000.toInt(), readableTextColor(0xFFF5F5C0.toInt(), preferred = null))
        assertEquals(0xFFFFFFFF.toInt(), readableTextColor(ONDEMAND_OUTSIDE_GRAY, preferred = null))
    }
}
