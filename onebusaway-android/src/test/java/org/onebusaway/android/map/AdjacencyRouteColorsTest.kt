/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.models.RouteDirectionKey
import kotlin.math.abs

@SuppressLint("RestrictedApi")
class AdjacencyRouteColorsTest {

    @Test
    fun `routes divide the hue circle at chroma 75 and tone 55`() {
        val colors = adjacencyRouteColors(listOf("a", "b", "c", "d"))

        assertEquals(listOf("a", "b", "c", "d"), colors.keys.toList())
        assertEquals(
            listOf(0.0, 90.0, 180.0, 270.0).map { hue -> Hct.from(hue, 75.0, 55.0).toInt() },
            colors.values.toList(),
        )
    }

    @Test
    fun `duplicate route ids retain their first hue slot`() {
        val colors = adjacencyRouteColors(listOf("a", "b", "a"))

        assertEquals(listOf("a", "b"), colors.keys.toList())
        assertEquals(Hct.from(0.0, 75.0, 55.0).toInt(), colors.getValue("a"))
        assertEquals(Hct.from(180.0, 75.0, 55.0).toInt(), colors.getValue("b"))
    }

    @Test
    fun `directions of one route receive distinct colors`() {
        val outbound = RouteDirectionKey("11", 0)
        val inbound = RouteDirectionKey("11", 1)

        val colors = adjacencyRouteColors(listOf(outbound, inbound))

        assertEquals(setOf(outbound, inbound), colors.keys)
        assertEquals(2, colors.values.toSet().size)
    }

    @Test
    fun `continuing route keeps its color when a stop introduces more routes`() {
        val route62 = adjacencyRouteColors(listOf("62")).getValue("62")

        val colors = adjacencyRouteColors(
            listOf("31", "32", "62", "E Line"),
            retained = mapOf("62" to route62),
        )

        assertEquals(route62, colors.getValue("62"))
        assertEquals(4, colors.values.toSet().size)
    }

    @Test
    fun `continuing route keeps its color when the next stop has fewer routes`() {
        val first = adjacencyRouteColors(listOf("45", "79"))

        val next = adjacencyRouteColors(listOf("79"), retained = first)

        assertEquals(first.getValue("79"), next.getValue("79"))
        assertEquals(setOf("79"), next.keys)
    }

    @Test
    fun `new routes fill the largest retained hue gaps`() {
        val retained = mapOf("existing" to Hct.from(0.0, 75.0, 55.0).toInt())

        val colors = adjacencyRouteColors(
            listOf("existing", "opposite", "quarter", "three-quarter"),
            retained,
        )

        assertEquals(retained.getValue("existing"), colors.getValue("existing"))
        val hueDifference = abs(
            Hct.fromInt(colors.getValue("opposite")).hue -
                Hct.fromInt(colors.getValue("existing")).hue
        )
        assertEquals(180.0, minOf(hueDifference, 360.0 - hueDifference), 1.0)
        assertEquals(4, colors.values.toSet().size)
    }
}
