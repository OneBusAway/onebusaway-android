/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.map.googlemapsv2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a bulb's halo is sized (#2241). Maps draws a custom cap at `bitmapRadius / referenceWidth` of the
 * stroke, so the disc a bulb is drawn from comes out at exactly the stroke width — and the case's copy of
 * it, drawn on a wider stroke, came out wider by the case's *whole* extra while the case shows only half
 * of that on each side of the line. These pin the inset that closes the gap, stated as the drawn radius
 * rather than as the factor, since the radius is the thing a rider sees.
 */
class GoogleEndpointBulbCapTest {

    /** The radius the cap draws at on a stroke of [widthPx], given the reference-width factor. */
    private fun drawnRadius(widthPx: Float, markInsetPx: Float) = widthPx / bulbReferenceScale(widthPx, markInsetPx)

    @Test
    fun `a line's own bulb is a circle of its stroke width`() {
        // Unchanged behaviour, and the reason the inset is expressed as a factor of 1 for an ordinary line:
        // the bulb is what says the rider gets on or off here, and it is sized from the ride it caps.
        assertEquals(15f, drawnRadius(widthPx = 15f, markInsetPx = 0f), 0.001f)
    }

    @Test
    fun `a case's bulb is inset, so its halo is the weight the case shows along the line`() {
        // A selected ride: 15px stroke, cased 2.25px per side, so the case is drawn at 19.5px and its bulb
        // must land 2.25px outside the 15px one — the same weight the rider reads beside the line, and half
        // the 4.5px halo an un-inset copy of the bulb gave it.
        val lineRadius = 15f
        val band = 2.25f
        val caseStroke = lineRadius + 2 * band

        assertEquals(lineRadius + band, drawnRadius(caseStroke, markInsetPx = band), 0.001f)
    }

    @Test
    fun `the halo keeps its weight at every stroke the zoom ramp reaches`() {
        // The inset is a constant weight while the stroke follows the ramp, so the factor is not fixed —
        // which is exactly why the width patch re-stamps the cap. Same band at both ends of the ramp.
        val band = 2.25f
        for (lineRadius in listOf(7.5f, 11.25f, 15f)) {
            val caseStroke = lineRadius + 2 * band
            assertEquals(
                "halo drifted at a $lineRadius px stroke",
                band,
                drawnRadius(caseStroke, markInsetPx = band) - lineRadius,
                0.001f
            )
        }
    }

    @Test
    fun `a stroke thinner than the inset it asks for draws the plain disc`() {
        // Nothing produces this today — a case is always wider than the band it shows — but inverting the
        // cap is a far worse answer than declining to inset, so it degrades rather than going negative.
        assertEquals(1f, bulbReferenceScale(widthPx = 2f, markInsetPx = 2f), 0.001f)
        assertEquals(1f, bulbReferenceScale(widthPx = 1f, markInsetPx = 4f), 0.001f)
    }
}
