/*
 * Copyright (C) 2016-2026 University of South Florida and Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.nav

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime

/**
 * Replays whole rides through the reducer on the JVM, in both of its coordinates.
 *
 * Trip 19 is excluded from the recorded set because its recording is missing; the ten `*c` entries
 * are excluded because their paired car traces are missing.
 */
class ReminderTraceReplayTest {
    /**
     * The recorded rides. Their trips have churned out of every feed, so no shape can be sourced for
     * them (see `tools/fetch-reminder-shapes.py`) and they exercise the straight-line fallback —
     * which remains the shipped path for legacy and pre-upgrade sessions.
     */
    @Test
    fun recordedTrips_emitOneOrderedAlertPairAndComplete() {
        RECORDED_TRACES.forEach { replay(it) }
    }

    /**
     * The generated rides: a simulated vehicle over real, current route geometry, so these exercise
     * the along-the-route coordinate the recorded traces cannot reach. Built by
     * `tools/generate-reminder-fixtures.py`.
     */
    @Test
    fun generatedTrips_replayAlongTheirRouteShape() {
        val names = generatedTraceNames()
        assertTrue("no generated fixtures found; run tools/generate-reminder-fixtures.py", names.isNotEmpty())
        names.forEach { replay(it, requireShape = true) }
    }

    private fun replay(name: String, requireShape: Boolean = false) {
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
        val shape = shapeFor(name)
        val plan = ReminderPlan(
            sessionId = name,
            rides = listOf(
                ReminderRide(
                    mode = ReminderMode.BUS,
                    routeLabel = null,
                    tripId = header[0],
                    // These are recordings of the legacy trip-details launch, whose contract carries
                    // only the destination and the stop before it. Naming the penultimate stop as the
                    // boarding stop would fabricate a fact the fixtures do not have, and would gate
                    // rides that the shipped legacy path does not gate.
                    board = null,
                    penultimate = penultimate,
                    alight = destination,
                    scheduledStart = ServerTime(0),
                    scheduledEnd = ServerTime(0),
                    shape = shape
                )
            )
        )
        var state = ReminderEngineState()
        val emitted = mutableListOf<ReminderEffect>()
        var lastTimestamp = 0L
        var sawAlongRouteReading = false
        lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val values = line.split(',')
            if (values.size < 13) return@forEach
            val sample = ReminderLocationSample(
                point = ReminderPoint(values[5].toDouble(), values[6].toDouble()),
                speedMetersPerSecond = values[8].toFloatOrNull(),
                accuracyMeters = values[10].toFloat(),
                // The anonymized wall-clock column is constant within each legacy fixture. The
                // elapsed-time column preserves sample ordering and duplicates, which is what the
                // reducer needs for replay.
                timestamp = WallTime(values[3].toLong())
            )
            lastTimestamp = maxOf(lastTimestamp, sample.timestamp.epochMs)
            val transition = ReminderEngine.reduce(plan, state, sample)
            transition.effects.filterIsInstance<ReminderEffect.AlightNow>().forEach {
                val distance = ReminderEngine.distanceMeters(sample.point, destination.point)
                assertTrue("$name emitted alight alert outside its stop-progression window", state.penultimateReached || distance <= 400.0)
            }
            // Which coordinate actually produced this reading. On a curving route the remaining
            // distance along the route runs well ahead of the straight-line distance to the stop,
            // so a reading that exceeds it can only have come from the shape. Without this the
            // whole fixture could silently replay through the fallback and still pass.
            transition.effects.filterIsInstance<ReminderEffect.Progress>().forEach { progress ->
                val straightLine = ReminderEngine.distanceMeters(sample.point, destination.point)
                if (progress.alightDistanceMeters > straightLine + 1.0) sawAlongRouteReading = true
            }
            state = transition.state
            emitted += transition.effects.filterNot { it is ReminderEffect.Progress }
        }
        if (requireShape) {
            assertTrue("$name never produced an along-the-route reading", sawAlongRouteReading)
        }

        // Legacy captures stop after the reminder fires rather than after the vehicle reaches the
        // destination. Finish each replay with two independent terminal fixes so the same trace
        // also exercises the new automatic-completion behavior.
        repeat(2) { offset ->
            val transition = ReminderEngine.reduce(
                plan,
                state,
                ReminderLocationSample(destination.point, 5f, null, WallTime(lastTimestamp + offset + 1))
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

    /**
     * The sidecar written beside a fixture by the fixture tools. Its fields are [ReminderShape]'s,
     * so it decodes straight into the model rather than through a mirror of the same schema; the
     * provenance the tools also record is dropped by [SIDECAR_JSON]'s `ignoreUnknownKeys`.
     */
    private fun shapeFor(name: String): ReminderShape? = javaClass.classLoader?.getResourceAsStream("$name.shape.json")
        ?.bufferedReader()?.use { it.readText() }
        ?.let { SIDECAR_JSON.decodeFromString<ReminderShape>(it) }

    private fun generatedTraceNames(): List<String> = javaClass.classLoader?.getResourceAsStream(GENERATED_MANIFEST)
        ?.bufferedReader()?.use { reader -> reader.readLines().map(String::trim).filter { it.isNotEmpty() } }
        .orEmpty()

    private companion object {
        const val GENERATED_MANIFEST = "generated-fixtures.txt"

        /** The sidecars carry provenance fields the engine has no use for. */
        val SIDECAR_JSON = Json { ignoreUnknownKeys = true }

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

        val RECORDED_TRACES = (1..34)
            .filterNot { it == 19 }
            .flatMap { number -> listOf("nav_trip$number", "nav_trip${number}c") }
            .filterNot { it in MISSING_CAR_TRACES }
    }
}
