/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

/**
 * Exercises the flavor-neutral reconcile/width bookkeeping the Google and MapLibre renderers share
 * (#1906). A fake native line records its width and whether it is still on the map, so the shared
 * logic — retain-equal, remove-gone, create-new, patch-changed-width, camera-settle resync — is
 * verified without a real map.
 */
class RoutePolylineReconcilerTest {

    /** Stand-in for a native gms/maplibre Polyline: tracks its width and removal. */
    private class FakeLine(val polyline: RoutePolyline, var width: Float) {
        var removed = false
    }

    /**
     * Wires a [RoutePolylineReconciler] to a fake native map, recording every create/remove/width-set so
     * a test can assert the exact native calls. Width is a simple `thicknessDp * zoom`-free stand-in: a
     * line's width is just its index profile so equal polylines can still change width across a settle.
     */
    private class Harness(val widthOf: (RoutePolyline, Float) -> Float = { _, zoom -> zoom }) {
        val created = mutableListOf<FakeLine>()
        var createCount = 0
        var removeCount = 0
        var setWidthCount = 0

        val reconciler = RoutePolylineReconciler<FakeLine>(
            widthOf = widthOf,
            createLine = { polyline, width ->
                createCount++
                FakeLine(polyline, width).also(created::add)
            },
            removeLines = { lines ->
                removeCount++
                lines.forEach { it.removed = true }
            },
            setWidth = { line, width ->
                setWidthCount++
                line.width = width
            },
            caseColorOf = { CASE_COLOR },
            caseExtraWidth = CASE_EXTRA_WIDTH
        )

        /** The lines still on the map — created minus removed (creation order, not draw order). */
        fun live(): List<FakeLine> = created.filterNot { it.removed }

        /** The live cases and strokes, split by the colour the fake case draws in. */
        fun cases(): List<FakeLine> = live().filter { it.polyline.color == CASE_COLOR }

        fun strokes(): List<FakeLine> = live().filterNot { it.polyline.color == CASE_COLOR }
    }

    private fun line(color: Int, vararg points: Double): RoutePolyline = RoutePolyline(color = color, points = points.map { GeoPoint(it, it) })

    @Test
    fun `first reconcile creates every line at the zoom width`() {
        val h = Harness()
        val a = line(1, 0.0)
        val b = line(2, 1.0)

        h.reconciler.reconcile(listOf(a, b), zoom = 4f)

        assertEquals(2, h.createCount)
        assertEquals(0, h.removeCount)
        assertEquals(listOf(a, b), h.live().map { it.polyline })
        assertTrue(h.live().all { it.width == 4f })
    }

    @Test
    fun `identical list instance is an O(1) no-op`() {
        val h = Harness()
        val list = listOf(line(1, 0.0))
        h.reconciler.reconcile(list, zoom = 4f)

        h.reconciler.reconcile(list, zoom = 9f)

        assertEquals(1, h.createCount)
        assertEquals(0, h.removeCount)
        // Width is not resynced by reconcile when the model is unchanged; that's resyncWidths' job.
        assertEquals(4f, h.live().single().width, 0f)
    }

    @Test
    fun `equal-but-new list retains the existing native lines`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0), line(2, 1.0)), zoom = 4f)
        val before = h.live()

        // A fresh list that is value-equal (a republished snapshot) must not touch native state.
        h.reconciler.reconcile(listOf(line(1, 0.0), line(2, 1.0)), zoom = 4f)

        assertEquals(2, h.createCount)
        assertEquals(0, h.removeCount)
        assertEquals(before, h.live())
    }

    @Test
    fun `adds and removes siblings while retaining the shared line`() {
        val h = Harness()
        val kept = line(62, 0.0)
        h.reconciler.reconcile(listOf(kept, line(99, 1.0)), zoom = 4f)
        val keptNative = h.live().first { it.polyline == kept }

        h.reconciler.reconcile(listOf(line(10, 2.0), kept, line(11, 3.0)), zoom = 4f)

        // The shared line's native object is retained (same instance), the dropped one removed, two added.
        assertSame(keptNative, h.live().first { it.polyline == kept })
        assertEquals(1, h.removeCount)
        assertEquals(setOf(10, 62, 11), h.live().map { it.polyline.color }.toSet())
    }

    @Test
    fun `retained line's width is patched when a non-equal update changes its width`() {
        val h = Harness()
        val a = line(1, 0.0)
        h.reconciler.reconcile(listOf(a), zoom = 4f)
        val native = h.live().first { it.polyline == a }

        // Adding a sibling makes the list non-equal (so reconcile runs instead of early-returning); the
        // new zoom gives the retained line a different width, which is patched on its native object.
        h.reconciler.reconcile(listOf(line(1, 0.0), line(2, 1.0)), zoom = 7f)

        assertFalse(native.removed)
        assertEquals(7f, native.width, 0f)
        assertEquals(1, h.setWidthCount)
    }

    @Test
    fun `retained line's width is left untouched when the zoom-width is unchanged`() {
        val h = Harness()
        val a = line(1, 0.0)
        h.reconciler.reconcile(listOf(a, line(2, 1.0)), zoom = 4f)
        val native = h.live().first { it.polyline == a }

        // A non-equal update (a sibling swapped) at the same zoom retains a with an unchanged width.
        h.reconciler.reconcile(listOf(line(1, 0.0), line(3, 2.0)), zoom = 4f)

        assertFalse(native.removed)
        assertEquals(4f, native.width, 0f)
        assertEquals(0, h.setWidthCount)
    }

    @Test
    fun `resyncWidths patches every retained line when zoom crosses a width breakpoint`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0), line(2, 1.0)), zoom = 4f)

        h.reconciler.resyncWidths(zoom = 10f)

        assertEquals(0, h.removeCount)
        assertTrue(h.live().all { it.width == 10f })
    }

    @Test
    fun `resyncWidths is a no-op when the width is unchanged`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0)), zoom = 4f)
        val native = h.live().single()
        native.width = -1f // sentinel: prove setWidth is not called

        h.reconciler.resyncWidths(zoom = 4f)

        assertEquals(-1f, native.width, 0f)
    }

    @Test
    fun `clear removes every drawn line and resets state`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0), line(2, 1.0)), zoom = 4f)

        h.reconciler.clear()

        assertTrue(h.created.all { it.removed })
        // After a clear, a subsequent reconcile starts fresh — every line is created again.
        h.reconciler.reconcile(listOf(line(1, 0.0)), zoom = 4f)
        assertEquals(3, h.createCount)
        assertFalse(h.live().single().removed)
    }

    // --- Casing (#2082): a cased line draws as two natives, the case beneath its own stroke. ---

    @Test
    fun `a cased line draws its case first, wider, and without chevrons`() {
        val h = Harness()

        h.reconciler.reconcile(listOf(line(1, 0.0).copy(cased = true, directional = true)), zoom = 4f)

        // Created case-first, which is what puts it under its own stroke in both SDKs' add-order draw.
        assertEquals(listOf(CASE_COLOR, 1), h.created.map { it.polyline.color })
        assertEquals(4f + CASE_EXTRA_WIDTH, h.cases().single().width, 0f)
        assertEquals(4f, h.strokes().single().width, 0f)
        // The stroke keeps its chevrons; the case must not double them.
        assertTrue(h.strokes().single().polyline.directional)
        assertFalse(h.cases().single().polyline.directional)
    }

    @Test
    fun `an uncased line draws one native, as before`() {
        val h = Harness()

        h.reconciler.reconcile(listOf(line(1, 0.0)), zoom = 4f)

        assertEquals(1, h.createCount)
        assertTrue(h.cases().isEmpty())
    }

    @Test
    fun `a case keeps its line's dash so a broken line's case breaks with it`() {
        val h = Harness()

        h.reconciler.reconcile(listOf(line(1, 0.0).copy(cased = true, dash = RouteLineDash.TRAIL)), zoom = 4f)

        assertEquals(RouteLineDash.TRAIL, h.cases().single().polyline.dash)
    }

    @Test
    fun `a case is re-widened with its line, staying the wider of the two`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0).copy(cased = true)), zoom = 4f)

        h.reconciler.resyncWidths(zoom = 10f)

        assertEquals(10f + CASE_EXTRA_WIDTH, h.cases().single().width, 0f)
        assertEquals(10f, h.strokes().single().width, 0f)
    }

    @Test
    fun `removing a cased line removes its case too`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0).copy(cased = true)), zoom = 4f)

        // A wholly different line: the cased one goes away, so its case must not be stranded on the map.
        h.reconciler.reconcile(listOf(line(2, 1.0)), zoom = 4f)

        assertTrue(h.created.filter { it.polyline.color == CASE_COLOR }.all { it.removed })
        assertEquals(listOf(2), h.live().map { it.polyline.color })
    }

    @Test
    fun `clear removes cases as well as strokes`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(line(1, 0.0).copy(cased = true), line(2, 1.0)), zoom = 4f)

        h.reconciler.clear()

        assertTrue(h.created.all { it.removed })
    }

    private companion object {
        // The fake's case colour, distinct from every line colour used here so a test can tell the two apart.
        const val CASE_COLOR = -424242

        const val CASE_EXTRA_WIDTH = 3f
    }
}
