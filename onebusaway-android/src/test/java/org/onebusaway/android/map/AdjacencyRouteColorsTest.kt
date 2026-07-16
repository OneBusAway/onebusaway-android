/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `continuing route keeps its color when a stop introduces more routes`() {
        val route62 = adjacencyRouteColors(listOf("62")).getValue("62")

        val colors = adjacencyRouteColors(
            listOf("31", "32", "62", "E Line"),
            retained = mapOf("62" to route62),
        )

        assertEquals(route62, colors.getValue("62"))
        assertEquals(4, colors.values.toSet().size)
    }
}
