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
    fun coloredAndNeutralStopsKeepWhiteCenters() {
        for (outlineColor in listOf(0xFF2277BB.toInt(), 0xFF616161.toInt(), 0xFF969696.toInt())) {
            val bitmap = drawRouteStopBitmap(RouteStopIconKey(54, outlineColor, null), Color.WHITE)
            assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            assertEquals(outlineColor, bitmap.getPixel(bitmap.width / 2 + 23, bitmap.height / 2))
        }
    }

    @Test
    fun everyDirectionFitsWithoutClippingAndKeepsTheBoardingPointCentered() {
        val color = 0xFF2277BB.toInt()
        val north = drawRouteStopBitmap(RouteStopIconKey(54, color, 0f), Color.WHITE)
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
                // Rotating the arrow must not change the white center or cut into the circular rim.
                val center = bitmap.width / 2f
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        val dx = x + 0.5f - center
                        val dy = y + 0.5f - center
                        if (dx * dx + dy * dy < 25f * 25f) {
                            assertEquals("Arrow changes the $direction rim at ($x, $y)", north.getPixel(x, y), bitmap.getPixel(x, y))
                        }
                    }
                }
                val radians = Math.toRadians(angle.toDouble())
                val radius = 30
                val x = bitmap.width / 2 + (kotlin.math.sin(radians) * radius).toInt()
                val y = bitmap.height / 2 - (kotlin.math.cos(radians) * radius).toInt()
                assertTrue("Missing $direction arrow", Color.alpha(bitmap.getPixel(x, y)) > 0)
            }
        }
    }
}
