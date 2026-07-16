/* Copyright (C) 2026 Open Transit Software Foundation */
package org.onebusaway.android.map.render

import org.junit.Assert.assertEquals
import org.junit.Test

class ListReconciliationTest {

    @Test
    fun `retains equal items while adding and removing siblings`() {
        val plan = reconcileEqualItems(
            previous = listOf("62", "old"),
            next = listOf("new-a", "62", "new-b"),
        )

        assertEquals(listOf(null, 0, null), plan.previousIndexForNext)
        assertEquals(setOf(1), plan.removedPreviousIndices)
    }

    @Test
    fun `duplicate values retain distinct previous instances`() {
        val plan = reconcileEqualItems(
            previous = listOf("62", "62"),
            next = listOf("62", "62", "62"),
        )

        assertEquals(listOf(0, 1, null), plan.previousIndexForNext)
        assertEquals(emptySet<Int>(), plan.removedPreviousIndices)
    }
}
