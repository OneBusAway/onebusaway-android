/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.onebusaway.android.ui.tripplan.pinned

import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.database.oba.PinnedTripDao
import org.onebusaway.android.database.oba.PinnedTripRecord
import org.onebusaway.android.database.oba.SINGLE_PIN_ID
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.model.TripLeg
import org.onebusaway.android.directions.model.toJson
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripplan.TripEndpoint
import org.onebusaway.android.ui.tripplan.TripModeSelection
import org.onebusaway.android.ui.tripplan.TripPlanParams

/**
 * Covers what a stored pin is allowed to resume into. As with the reminder session store, the risk is
 * not the SQL — the DAO and migration tests pin that — but the decisions this store makes when the row
 * it finds is unreadable, inconsistent, or written by a different build.
 */
class PinnedTripStoreTest {
    private val now = WallTime(1_700_000_000_000)

    @Test
    fun `a pinned trip round trips with the option the rider chose`() = runTest {
        val pins = FakePinnedTripDao()
        val store = RoomPinnedTripStore(pins, RecordingLog())

        store.pin(params(), departNow = true, itineraries = itineraries(3), selectedIndex = 2, now = now)

        val pinned = store.pinned.first()!!
        assertEquals(params(), pinned.params)
        assertTrue("the moving anchor is part of the request the rider made", pinned.departNow)
        assertEquals(3, pinned.itineraries.size)
        assertEquals(2, pinned.selectedIndex)
        assertEquals(now, pinned.pinnedAt)
    }

    @Test
    fun `pinning a second trip leaves exactly one`() = runTest {
        // There is one slot, and it is what answers "how do pins stop cluttering" (#2053(a)). A second
        // row would be a pin the rider can neither see nor reach.
        val pins = FakePinnedTripDao()
        val store = RoomPinnedTripStore(pins, RecordingLog())

        store.pin(params(), departNow = false, itineraries = itineraries(1), selectedIndex = 0, now = now)
        store.pin(params(to = "Ballard"), departNow = false, itineraries = itineraries(2), selectedIndex = 1, now = now)

        assertEquals(1, pins.rows.size)
        assertEquals(1, store.pinned.first()!!.selectedIndex)
    }

    @Test
    fun `unpinning clears the slot`() = runTest {
        val pins = FakePinnedTripDao()
        val store = RoomPinnedTripStore(pins, RecordingLog())
        store.pin(params(), departNow = false, itineraries = itineraries(1), selectedIndex = 0, now = now)

        store.unpin()

        assertTrue(pins.rows.isEmpty())
        assertNull(store.pinned.first())
    }

    @Test
    fun `a pin taken a year ago still resumes`() {
        // The load-bearing assertion for the no-expiry decision. A pin is a standing intention with no
        // process behind it to be orphaned, so nothing about its age makes it wrong; if someone later
        // adds an age bound, this is the test that says so.
        val outcome = record(pinnedAt = now - 365.days).pinnedOutcome()

        assertTrue(outcome is PinnedTripOutcome.Resume)
    }

    @Test
    fun `a pin written by another format version is discarded`() {
        val outcome = record().copy(formatVersion = PINNED_TRIP_FORMAT_VERSION + 1).pinnedOutcome()

        assertTrue(outcome is PinnedTripOutcome.Discard)
    }

    @Test
    fun `an unreadable query is discarded`() {
        val outcome = record().copy(queryJson = "{not json").pinnedOutcome()

        assertEquals("query JSON did not decode", (outcome as PinnedTripOutcome.Discard).reason)
    }

    @Test
    fun `an unreadable plan is discarded`() {
        // `toTripItineraries` cannot tell corrupt from empty, and a pin is never written empty — so
        // decoding to nothing is exactly the corruption signal.
        val outcome = record().copy(itinerariesJson = "{not json").pinnedOutcome()

        assertEquals("itinerary JSON did not decode", (outcome as PinnedTripOutcome.Discard).reason)
    }

    @Test
    fun `an out-of-range option is discarded rather than coerced into range`() {
        // Coercing would resume a different trip than the rider pinned, which is a wrong answer wearing
        // the right clothes.
        val outcome = record(itineraries = itineraries(2)).copy(selectedIndex = 5).pinnedOutcome()

        assertTrue(outcome is PinnedTripOutcome.Discard)
    }

    @Test
    fun `an unreadable row reads as nothing pinned, and says why`() = runTest {
        val log = RecordingLog()
        val pins = FakePinnedTripDao(record().copy(queryJson = "{not json"))

        assertNull(RoomPinnedTripStore(pins, log).pinned.first())
        assertTrue(log.warnings.single().contains("query JSON did not decode"))
    }

    @Test
    fun `pinning an option the plan does not contain is refused rather than stored`() = runTest {
        // The callers all have a live plan in hand, so this can only be a wiring mistake — and a pin
        // that vanishes on the next read is far harder to notice than one that never appears.
        val pins = FakePinnedTripDao()
        val log = RecordingLog()

        RoomPinnedTripStore(pins, log)
            .pin(params(), departNow = false, itineraries = itineraries(2), selectedIndex = 7, now = now)

        assertTrue(pins.rows.isEmpty())
        assertTrue(log.warnings.single().contains("option 7"))
    }

    @Test
    fun `the selected itinerary is the one the rider pinned`() {
        val resume = record(itineraries = itineraries(3)).copy(selectedIndex = 1).pinnedOutcome()

        val pin = (resume as PinnedTripOutcome.Resume).pin
        assertNotNull(pin.selectedItinerary)
        assertEquals(pin.itineraries[1], pin.selectedItinerary)
    }

    private fun record(
        params: TripPlanParams = params(),
        departNow: Boolean = false,
        itineraries: List<TripItinerary> = itineraries(1),
        pinnedAt: WallTime = now
    ) = PinnedTripRecord(
        pinId = SINGLE_PIN_ID,
        formatVersion = PINNED_TRIP_FORMAT_VERSION,
        queryJson = PinnedTripJson.encode(params.toPinnedQuery(departNow)),
        itinerariesJson = itineraries.toJson(),
        selectedIndex = 0,
        pinnedAtMs = pinnedAt.epochMs
    )

    private fun itineraries(count: Int): List<TripItinerary> = List(count) { index ->
        TripItinerary(startTime = ServerTime(index * 60_000L), legs = listOf(TripLeg(tripId = "trip_$index")))
    }

    private fun params(to: String = "Pike Place Market") = TripPlanParams(
        from = TripEndpoint.CurrentLocation(47.60, -122.33),
        to = TripEndpoint.Geocoded(to, 47.61, -122.34),
        dateTimeMillis = 1_700_000_000_000,
        arriving = false,
        modes = TripModeSelection(),
        wheelchair = false,
        optimizeTransfers = false,
        maxWalkMeters = null
    )

    /**
     * The store reports discards through [PinnedTripLog] rather than `android.util.Log`, which throws
     * here — that indirection is what lets these tests exercise the store itself rather than only the
     * pure [pinnedOutcome] underneath it.
     */
    private class RecordingLog : PinnedTripLog() {
        val warnings = mutableListOf<String>()

        override fun warn(message: String) {
            warnings += message
        }
    }

    private class FakePinnedTripDao(initial: PinnedTripRecord? = null) : PinnedTripDao {
        private val state = MutableStateFlow(initial)

        val rows: List<PinnedTripRecord> get() = listOfNotNull(state.value)

        override fun observePinned(): Flow<PinnedTripRecord?> = state

        override suspend fun pinned(): PinnedTripRecord? = state.value

        override suspend fun upsert(record: PinnedTripRecord) {
            state.value = record
        }

        override suspend fun clear() {
            state.value = null
        }
    }
}
