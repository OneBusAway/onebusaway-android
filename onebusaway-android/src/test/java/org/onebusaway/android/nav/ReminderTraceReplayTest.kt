/*
 * Copyright (C) 2016-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays every field-recorded destination-reminder trace on the JVM; none are skipped on CI. */
class ReminderTraceReplayTest {
    @Test
    fun recordedTrips_emitOneOrderedAlertPairAndComplete() {
        TRACE_NAMES.forEach(::replay)
    }

    private fun replay(name: String) {
        val lines = checkNotNull(javaClass.classLoader?.getResourceAsStream("$name.csv")) {
            "Missing trace $name.csv"
        }.bufferedReader().use { it.readLines() }
        val header = lines.first().split(',')
        val destination = ReminderStop(
            header[1],
            header[1],
            ReminderPoint(header[2].toDouble(), header[3].toDouble())
        )
        val penultimate = ReminderStop(
            header[4],
            header[4],
            ReminderPoint(header[5].toDouble(), header[6].toDouble())
        )
        val plan = ReminderPlan(
            sessionId = name,
            rides = listOf(
                ReminderRide(
                    mode = ReminderMode.BUS,
                    routeLabel = null,
                    tripId = header[0],
                    board = penultimate,
                    penultimate = penultimate,
                    alight = destination,
                    scheduledStart = 0,
                    scheduledEnd = 0
                )
            )
        )
        var state = ReminderEngineState()
        val emitted = mutableListOf<ReminderEffect>()
        var lastTimestamp = 0L
        lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val values = line.split(',')
            if (values.size < 13) return@forEach
            val sample = ReminderLocationSample(
                point = ReminderPoint(values[5].toDouble(), values[6].toDouble()),
                speedMetersPerSecond = values[8].toFloatOrNull(),
                accuracyMeters = values[10].toFloat(),
                timestampMs = values[4].toLong()
            )
            lastTimestamp = maxOf(lastTimestamp, sample.timestampMs)
            val transition = ReminderEngine.reduce(plan, state, sample)
            transition.effects.filterIsInstance<ReminderEffect.AlightNow>().forEach {
                val distance = ReminderEngine.distanceMeters(sample.point, destination.point)
                assertTrue("$name emitted alight alert outside its stop-progression window", state.penultimateReached || distance <= 400.0)
            }
            state = transition.state
            emitted += transition.effects.filterNot { it is ReminderEffect.Progress }
        }

        // Legacy captures stop after the reminder fires rather than after the vehicle reaches the
        // destination. Finish each replay with two independent terminal fixes so the same trace
        // also exercises the new automatic-completion behavior.
        repeat(2) { offset ->
            val transition = ReminderEngine.reduce(
                plan,
                state,
                ReminderLocationSample(destination.point, 5f, null, lastTimestamp + offset + 1)
            )
            state = transition.state
            emitted += transition.effects.filterNot { it is ReminderEffect.Progress }
        }

        assertEquals("$name get-ready count", 1, emitted.count { it is ReminderEffect.GetReady })
        assertEquals("$name alight count", 1, emitted.count { it is ReminderEffect.AlightNow })
        assertTrue(
            "$name alert ordering",
            emitted.indexOfFirst { it is ReminderEffect.GetReady } < emitted.indexOfFirst { it is ReminderEffect.AlightNow }
        )
        assertTrue("$name did not complete", state.completed)
    }

    private companion object {
        val MISSING_CAR_TRACES = setOf(
            "nav_trip17c",
            "nav_trip26c",
            "nav_trip27c",
            "nav_trip28c",
            "nav_trip29c",
            "nav_trip30c",
            "nav_trip31c",
            "nav_trip32c",
            "nav_trip33c",
            "nav_trip34c"
        )

        val TRACE_NAMES = (1..34)
            .filterNot { it == 19 }
            .flatMap { number -> listOf("nav_trip$number", "nav_trip${number}c") }
            .filterNot { it in MISSING_CAR_TRACES }
    }
}
