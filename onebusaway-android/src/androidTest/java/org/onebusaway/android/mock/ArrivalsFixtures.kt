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
package org.onebusaway.android.mock

import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.api.data.StopArrivals

import android.content.Context
import kotlinx.serialization.json.Json
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.models.ObaSituation
import org.onebusaway.android.ui.arrivals.ArrivalInfo
import org.onebusaway.android.ui.arrivals.convertArrivals

/**
 * Test helper for instrumented tests that exercise the arrival/situation projections against a
 * captured arrivals-and-departures fixture: decodes the fixture as the io/client wire DTO and runs
 * the same `convertArrivals` / `StopArrivals.situations` aggregation the app uses, so the assertions
 * ride the production path.
 */
object ArrivalsFixtures {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Decodes a `res/raw` arrivals-and-departures fixture into the io/client envelope. */
    @JvmStatic
    fun load(context: Context, fixture: String): ObaEnvelope<EntryWithReferences<ArrivalsForStop>> =
        Resources.read(context, Resources.getTestUri(fixture))
            .use { json.decodeFromString(it.readText()) }

    private fun snapshot(env: ObaEnvelope<EntryWithReferences<ArrivalsForStop>>): StopArrivals =
        StopArrivals(env.data!!, env.currentTime, 0)

    /**
     * The fixture's arrivals projected to display [ArrivalInfo] via the production [convertArrivals].
     * [favorite] supplies each row's route/headsign favorite state (defaults to none); the favorite
     * store is no longer a ContentProvider, so tests pass the favorites in directly.
     */
    @JvmStatic
    @JvmOverloads
    fun convert(
        context: Context,
        env: ObaEnvelope<EntryWithReferences<ArrivalsForStop>>,
        includeArriveDepartLabels: Boolean,
        favorite: (routeId: String, headsign: String?, stopId: String) -> Boolean = { _, _, _ -> false },
    ): ArrayList<ArrivalInfo> = ArrayList(
        convertArrivals(
            context, snapshot(env).arrivals, null, ServerTime(env.currentTime), includeArriveDepartLabels, favorite
        )
    )

    /** All situations (stop/agency + route alerts) for the fixture, via the production aggregation. */
    @JvmStatic
    fun allSituations(
        env: ObaEnvelope<EntryWithReferences<ArrivalsForStop>>,
        filter: List<String>?,
    ): List<ObaSituation> = snapshot(env).situations(filter)

    /** Just the stop/agency-level situations the entry references directly (not route alerts). */
    @JvmStatic
    fun stopSituations(
        env: ObaEnvelope<EntryWithReferences<ArrivalsForStop>>,
    ): List<ObaSituation> {
        val snapshot = snapshot(env)
        return env.data!!.entry.situationIds.mapNotNull { snapshot.situation(it) }
    }
}
