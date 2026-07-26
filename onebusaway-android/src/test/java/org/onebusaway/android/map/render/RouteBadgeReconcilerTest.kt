/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.util.GeoPoint

/**
 * Exercises the flavor-neutral badge reconcile the Google and MapLibre renderers share. A fake native
 * marker records whether it is still on the map, so retain-equal / remove-gone / create-new is verified
 * without a real map.
 *
 * The property that matters is the one the nearby-routes hoop (#2004) depends on: adding one badge to a
 * drawn set creates exactly one marker and disturbs none of the others. Before badges had their own
 * change boundary they rode the static layer, which every snapshot change tore down wholesale.
 */
class RouteBadgeReconcilerTest {

    /** Stand-in for a native gms/maplibre Marker: tracks the badge it was made for, and removal. */
    private class FakeMarker(val badge: RouteBadge) {
        var removed = false
    }

    private class Harness {
        val created = mutableListOf<FakeMarker>()
        var createCount = 0
        var removeCount = 0

        val reconciler = RouteBadgeReconciler<FakeMarker>(
            createMarker = { badge ->
                createCount++
                FakeMarker(badge).also(created::add)
            },
            removeMarkers = { markers ->
                removeCount++
                markers.forEach { it.removed = true }
            }
        )

        /** The markers still on the map — created minus removed. */
        fun live(): List<FakeMarker> = created.filterNot { it.removed }
    }

    private fun badge(routeId: String, latitude: Double = 0.0, color: Int = 1) = RouteBadge(
        routeId = routeId,
        routeShortName = routeId,
        color = color,
        point = GeoPoint(latitude, 0.0),
        directionId = null
    )

    @Test
    fun `first reconcile creates a marker per badge`() {
        val h = Harness()

        h.reconciler.reconcile(listOf(badge("a"), badge("b")))

        assertEquals(2, h.createCount)
        assertEquals(listOf("a", "b"), h.live().map { it.badge.routeId })
    }

    @Test
    fun `an appended badge creates exactly one marker and retains the rest`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(badge("a"), badge("b")))
        val before = h.live()

        // The hoop's progressive fill: one more route resolved.
        h.reconciler.reconcile(listOf(badge("a"), badge("b"), badge("c")))

        assertEquals(3, h.createCount)
        assertEquals(0, h.removeCount)
        // The already-drawn markers are the same native objects — never removed, never re-added.
        assertSame(before[0], h.live()[0])
        assertSame(before[1], h.live()[1])
    }

    @Test
    fun `a badge that changes is replaced while its neighbours are retained`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(badge("a"), badge("b"), badge("c")))
        val before = h.live()

        // "b" gets re-anchored (the layout moved it); a and c are untouched.
        h.reconciler.reconcile(listOf(badge("a"), badge("b", latitude = 5.0), badge("c")))

        assertTrue(before[1].removed)
        assertFalse(before[0].removed)
        assertFalse(before[2].removed)
        assertEquals(4, h.createCount)
        // live() is creation order, so the replacement lands last rather than in "b"'s old slot.
        assertEquals(listOf(0.0, 0.0, 5.0), h.live().map { it.badge.point.latitude })
    }

    @Test
    fun `an equal republished list touches no native state`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(badge("a")))

        // A different list instance holding an equal badge — the layer re-emitting unchanged.
        h.reconciler.reconcile(listOf(badge("a")))

        assertEquals(1, h.createCount)
        assertEquals(0, h.removeCount)
    }

    @Test
    fun `clearing the layer removes every marker`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(badge("a"), badge("b")))

        h.reconciler.reconcile(emptyList())

        assertEquals(emptyList<String>(), h.live().map { it.badge.routeId })
        // And the reconciler is genuinely empty afterwards, not merely showing nothing: the next
        // non-empty list rebuilds from scratch rather than trying to retain removed markers.
        h.reconciler.reconcile(listOf(badge("a")))
        assertEquals(3, h.createCount)
        assertEquals(listOf("a"), h.live().map { it.badge.routeId })
    }

    @Test
    fun `duplicate badges are reconciled as a multiset`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(badge("a"), badge("a")))

        h.reconciler.reconcile(listOf(badge("a")))

        assertEquals(2, h.createCount)
        assertEquals(1, h.live().size)
    }

    @Test
    fun `dispose removes every marker and resets`() {
        val h = Harness()
        h.reconciler.reconcile(listOf(badge("a"), badge("b")))

        h.reconciler.clear()

        assertTrue(h.created.all { it.removed })
        // A clear on an already-empty reconciler must not call into the native map at all.
        h.reconciler.clear()
        assertEquals(1, h.removeCount)
    }
}
