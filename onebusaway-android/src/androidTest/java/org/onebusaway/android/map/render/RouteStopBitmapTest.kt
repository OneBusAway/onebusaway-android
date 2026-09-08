/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.render

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteStopBitmapTest {
    @Test
    fun routeStopsHaveRouteColoredRimsAndThemeAwareCenters() {
        val routeColor = 0xFF2277BB.toInt()
        for (surface in listOf(Color.WHITE, 0xFF4D4D4D.toInt())) {
            val normal = drawRouteStopBitmap(RouteStopIconKey(54, routeColor, null), surface)
            assertEquals(surface, normal.getPixel(normal.width / 2, normal.height / 2))
            assertEquals(routeColor, normal.getPixel(normal.width / 2 + 23, normal.height / 2))
        }
    }

    @Test
    fun everyDirectionFitsWithoutClippingAndKeepsTheBoardingPointCentered() {
        val color = 0xFF2277BB.toInt()
        for (direction in StopDirection.entries) {
            val angle = direction.takeIf { it != StopDirection.NONE }?.compassAngle
            val bitmap = drawRouteStopBitmap(RouteStopIconKey(54, color, angle), Color.WHITE)
            assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            for (i in 0 until bitmap.width) {
                assertEquals(0, Color.alpha(bitmap.getPixel(i, 0)))
                assertEquals(0, Color.alpha(bitmap.getPixel(i, bitmap.height - 1)))
                assertEquals(0, Color.alpha(bitmap.getPixel(0, i)))
                assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, i)))
            }
            if (angle != null) {
                val radians = Math.toRadians(angle.toDouble())
                val radius = 30
                val x = bitmap.width / 2 + (kotlin.math.sin(radians) * radius).toInt()
                val y = bitmap.height / 2 - (kotlin.math.cos(radians) * radius).toInt()
                assertTrue("Missing $direction arrow", Color.alpha(bitmap.getPixel(x, y)) > 0)
            }
        }
    }
}
