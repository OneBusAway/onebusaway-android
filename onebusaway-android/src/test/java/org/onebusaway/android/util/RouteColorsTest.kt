/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.util

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme-aware route colour policy — the faded re-tone a route badge chip is filled with, and now also
 * what the directions map strokes that route's line with. It sits here, shared, precisely so those two
 * can't drift apart; these assertions pin the properties each of them depends on.
 */
@SuppressLint("RestrictedApi") // Hct, Material's vendored color-science util; see AdjacencyRouteColors.kt.
class RouteColorsTest {

    // A vivid red and a muted one, as two agencies might publish the same route colour.
    private val vivid = Hct.from(25.0, 90.0, 45.0).toInt()

    private val muted = Hct.from(25.0, 20.0, 45.0).toInt()

    @Test
    fun `a re-toned colour keeps the agency's hue and takes the requested tone`() {
        listOf(false, true).forEach { dark ->
            val toned = Hct.fromInt(routeColorAtTone(vivid, dark, tone = 60.0)!!)
            assertEquals("hue (dark=$dark)", 25.0, toned.hue, HUE_TOLERANCE_DEGREES)
            assertEquals("tone (dark=$dark)", 60.0, toned.tone, CHANNEL_TOLERANCE)
        }
    }

    @Test
    fun `saturation is capped, not replaced`() {
        // This is what makes these colours read as *faded*: a vivid hue is muted to the theme's ceiling,
        // while an agency that already publishes something soft keeps it rather than being pushed up to the
        // cap. Both themes cap, and the dark one lets more through.
        listOf(false, true).forEach { dark ->
            val cappedVivid = Hct.fromInt(routeColorAtTone(vivid, dark, tone = 60.0)!!).chroma
            val keptMuted = Hct.fromInt(routeColorAtTone(muted, dark, tone = 60.0)!!).chroma

            assertTrue("vivid should be muted (dark=$dark)", cappedVivid < Hct.fromInt(vivid).chroma)
            assertEquals("muted should pass through (dark=$dark)", Hct.fromInt(muted).chroma, keptMuted, CHANNEL_TOLERANCE)
            assertTrue("muted should stay under the cap (dark=$dark)", keptMuted < cappedVivid)
        }
    }

    @Test
    fun `the dark theme lets more of the agency's saturation through`() {
        val light = Hct.fromInt(routeColorAtTone(vivid, dark = false, tone = 60.0)!!).chroma
        val night = Hct.fromInt(routeColorAtTone(vivid, dark = true, tone = 60.0)!!).chroma

        assertTrue("dark chroma $night should exceed light's $light", night > light)
    }

    @Test
    fun `a colourless or hueless route is declined, leaving the fallback to the caller`() {
        // Grey, black and white carry no hue to re-tone, so there is nothing to render — the chip takes a
        // neutral theme container and a map line falls back to whatever its own kind calls for.
        listOf(null, 0xFF808080.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()).forEach { source ->
            listOf(false, true).forEach { dark ->
                assertNull("$source (dark=$dark)", routeColorAtTone(source, dark, tone = 60.0))
                assertNull("$source (dark=$dark)", routeBadgeChipColor(source, dark))
            }
        }
    }

    @Test
    fun `the badge chip's fill and text differ by theme, and the fill stays the lighter of the two`() {
        val light = Hct.fromInt(routeBadgeChipColor(vivid, dark = false)!!)
        val night = Hct.fromInt(routeBadgeChipColor(vivid, dark = true)!!)

        // The chip is a light fill carrying dark text in both themes — which is also what makes the same
        // colour read as the faded rendering when it's stroked as a map line.
        listOf(light, night).forEach { fill ->
            assertTrue("fill tone ${fill.tone} should be light", fill.tone > 60.0)
        }
        listOf(false, true).forEach { dark ->
            val fill = Hct.fromInt(routeBadgeChipColor(vivid, dark)!!).tone
            val text = Hct.fromInt(routeBadgeChipTextColor(vivid, dark)!!).tone
            assertTrue("text tone $text should be darker than fill $fill (dark=$dark)", text < fill)
        }
        // But not the same colour: the theme decides how much saturation the fill holds and how light it sits.
        assertNotEquals(routeBadgeChipColor(vivid, dark = false), routeBadgeChipColor(vivid, dark = true))
    }

    @Test
    fun `a badge's text clears 4point5 to 1 against its own fill, in both themes`() {
        // The one hard constraint on how un-pastel a chip may get: the fill tone and the text tone are only
        // correct together, so darkening the fill for a stronger colour has to darken the text with it. This
        // is the assertion that fails if a future tuning pass moves one and forgets the other.
        //
        // Computed from HCT tone, which *is* CIE L*, so relative luminance follows from it exactly and no
        // per-channel sRGB round-trip is needed.
        val agencyColors = listOf(
            0xFF00A651.toInt(), // a green
            0xFF0076CE.toInt(), // a blue
            0xFF6F2C91.toInt(), // a purple
            0xFFC8102E.toInt(), // a red — the hue that clamps hardest at a light tone
            0xFFF6871F.toInt() // an orange
        )
        agencyColors.forEach { source ->
            listOf(false, true).forEach { dark ->
                val fill = Hct.fromInt(routeBadgeChipColor(source, dark)!!).tone
                val text = Hct.fromInt(routeBadgeChipTextColor(source, dark)!!).tone
                val ratio = contrastRatio(fill, text)
                assertTrue(
                    "contrast %.2f:1 for %06X (dark=%b)".format(ratio, source and 0xFFFFFF, dark),
                    ratio >= MIN_TEXT_CONTRAST
                )
            }
        }
    }

    /** WCAG contrast between two HCT tones, via the relative luminance each one is defined by. */
    private fun contrastRatio(oneTone: Double, otherTone: Double): Double {
        fun luminance(tone: Double) = if (tone > 8.0) Math.pow((tone + 16.0) / 116.0, 3.0) else tone / 903.3
        val lighter = luminance(maxOf(oneTone, otherTone))
        val darker = luminance(minOf(oneTone, otherTone))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private companion object {
        // The re-tone clamps to each hue's own sRGB gamut limit, so a hue can shift a degree or two.
        const val HUE_TOLERANCE_DEGREES = 3.0

        // Chroma/tone likewise clamp to the gamut. Wide enough to absorb that, far too narrow to hide a
        // different policy.
        const val CHANNEL_TOLERANCE = 2.0

        // WCAG AA for normal-size text, which a route name on a chip is (bold, but small).
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
