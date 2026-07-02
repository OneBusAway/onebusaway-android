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
package org.onebusaway.android.ui.arrivals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.models.contentKey

/**
 * Unit tests for [planActiveAlerts], the pure fold that turns a stop's situations into active-alert
 * rows (see #1593). Covers the active-window-before-grouping ordering, representative selection, and
 * that each row carries every grouped situation id. Hidden state is NOT computed here — it is derived
 * in the ViewModel from the reactive hidden-id source — so these tests are pure grouping.
 */
class ArrivalsAlertPlanTest {

    // --- happy paths ---

    @Test
    fun `no situations yields an empty plan`() {
        assertEquals(emptyList<AlertItem>(), plan(emptyList()))
    }

    @Test
    fun `a single active alert becomes one row carrying its own id`() {
        assertEquals(
            listOf(
                AlertItem(
                    contentId = contentIdFor("Detour"),
                    situationId = "1",
                    situationIds = setOf("1"),
                    summary = "Detour",
                    severity = AlertSeverity.WARNING
                )
            ),
            plan(listOf(situation(id = "1", summary = "Detour")))
        )
    }

    @Test
    fun `distinct content produces one row per alert in first-seen order`() {
        val alerts = plan(
            listOf(
                situation(id = "1", summary = "Detour"),
                situation(id = "2", summary = "Stop closure")
            )
        )

        assertEquals(listOf("1", "2"), alerts.map { it.situationId })
    }

    @Test
    fun `a null summary maps to an empty string`() {
        assertEquals("", plan(listOf(situation(id = "1", summary = null))).single().summary)
    }

    @Test
    fun `severity maps onto the three banner styles`() {
        assertEquals(AlertSeverity.INFO, severityOf(ObaSituation.SEVERITY_NO_IMPACT))
        assertEquals(AlertSeverity.ERROR, severityOf(ObaSituation.SEVERITY_SEVERE))
        assertEquals(AlertSeverity.ERROR, severityOf(ObaSituation.SEVERITY_VERY_SEVERE))
        assertEquals(AlertSeverity.WARNING, severityOf(ObaSituation.SEVERITY_NORMAL))
        assertEquals(AlertSeverity.WARNING, severityOf(null))
    }

    // --- active-window ordering / representative selection ---

    @Test
    fun `inactive alerts are excluded entirely`() {
        assertTrue(
            plan(listOf(situation(id = "1", summary = "Detour")), activeIds = emptySet()).isEmpty()
        )
    }

    @Test
    fun `identical content collapses to a single representative row`() {
        val alerts = plan(
            listOf(
                situation(id = "a", summary = "Stop closure"),
                situation(id = "b", summary = "Stop closure")
            )
        )

        assertEquals(1, alerts.size)
        // First occurrence among the active set wins the representative slot.
        assertEquals("a", alerts.single().situationId)
    }

    @Test
    fun `an expired duplicate does not suppress a later active one`() {
        // Same content republished under a new id: the first copy has expired (inactive), the second
        // is active. Grouping happens AFTER the active filter, so the active copy represents the group
        // instead of being dropped along with the expired one.
        val alerts = plan(
            listOf(
                situation(id = "expired", summary = "Stop closure"),
                situation(id = "active", summary = "Stop closure")
            ),
            activeIds = setOf("active")
        )

        assertEquals("active", alerts.single().situationId)
    }

    // --- grouped ids (drives hide-follows-content, see #1593) ---

    @Test
    fun `a row carries every active grouped id, not just the representative`() {
        // The feed serves the same alert twice (two live ids); the row must know both so a hide can
        // write them all and stay hidden as the feed rotates which id leads the group.
        val alerts = plan(
            listOf(
                situation(id = "a", summary = "Stop closure"),
                situation(id = "b", summary = "Stop closure")
            )
        )

        assertEquals(setOf("a", "b"), alerts.single().situationIds)
    }

    @Test
    fun `an inactive duplicate id is not folded into the row`() {
        // Only the active ids define the group; an expired duplicate's id is not carried, so a hide
        // won't waste a write on a row the DB will never surface again.
        val alerts = plan(
            listOf(
                situation(id = "active", summary = "Stop closure"),
                situation(id = "expired", summary = "Stop closure")
            ),
            activeIds = setOf("active")
        )

        assertEquals(setOf("active"), alerts.single().situationIds)
    }

    // --- helpers ---

    /** Plans alerts treating every id as active unless overridden. */
    private fun plan(
        situations: List<ObaSituation>,
        activeIds: Set<String> = situations.mapTo(mutableSetOf()) { it.id }
    ) = planActiveAlerts(
        situations = situations,
        isActive = { it.id in activeIds }
    )

    private fun situation(id: String, summary: String?): ObaSituation = FakeSituation(id, summary)

    /** The content identity [planActiveAlerts] assigns to a row with this summary (id-independent). */
    private fun contentIdFor(summary: String): String = situation("ignored", summary).contentKey

    /**
     * Minimal [ObaSituation] whose [contentKey][org.onebusaway.android.models.contentKey] is
     * driven only by [summary] — the one rider-visible field these tests vary to form content groups.
     */
    private class FakeSituation(
        override val id: String,
        override val summary: String?
    ) : ObaSituation {
        override val description: String? = null
        override val advice: String? = null
        override val reason: String? = null
        override val url: String? = null
        override val severity: String? = null
        override val creationTime: Long = 0L
        override val allAffects: Array<ObaSituation.AllAffects> = emptyArray()
        override val consequences: Array<ObaSituation.Consequence> = emptyArray()
        override val activeWindows: Array<ObaSituation.ActiveWindow> = emptyArray()
    }
}
