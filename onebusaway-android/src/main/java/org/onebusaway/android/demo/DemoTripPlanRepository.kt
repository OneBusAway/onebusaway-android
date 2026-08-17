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
package org.onebusaway.android.demo

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.toTripItinerary
import org.onebusaway.android.api.contract.OtpPlanParser
import org.onebusaway.android.directions.model.TripItinerary
import org.onebusaway.android.directions.util.TripRequestBuilder
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.ui.tripplan.DefaultTripPlanRepository
import org.onebusaway.android.ui.tripplan.TripPlanParams
import org.onebusaway.android.ui.tripplan.TripPlanRepository

/**
 * Serves the scripted tutorial's trip plan (#2164) while demo mode is on, and delegates to the real
 * [DefaultTripPlanRepository] the rest of the time.
 *
 * The plan is a genuine OpenTripPlanner `/plan` response for a Capitol Hill → U-District trip,
 * captured once and bundled as `res/raw/demo_trip_plan.json`. It is parsed through the *production*
 * [OtpPlanParser] and [toTripItinerary] adapter rather than a fixture-specific decoder, so what the
 * tour's itinerary list demonstrates is exactly what the OTP1 path produces — including the mix the
 * script needs: light rail, a bus, and one option that is the route 49 the user has just been looking
 * at from the demo stop.
 *
 * **Whatever the user asks for, they get this plan.** The tutorial long-presses a scripted destination,
 * but a user who edits the endpoints mid-tour should still see a working result rather than an error,
 * and there is nothing behind demo mode to plan a different trip against.
 */
class DemoTripPlanRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val demoMode: DemoModeController,
    private val real: DefaultTripPlanRepository
) : TripPlanRepository {

    override suspend fun plan(params: TripPlanParams): Result<List<TripItinerary>> = if (demoMode.isActive) Result.success(demoItineraries()) else real.plan(params)

    @WorkerThread
    override fun planBlocking(builder: TripRequestBuilder): List<TripItinerary> = if (demoMode.isActive) demoItineraries() else real.planBlocking(builder)

    /**
     * The bundled plan as captured, parsed once for the process.
     *
     * Only the *dating* is clock-relative, so only [rebase] has to run per call — re-reading and
     * re-decoding 27 KB of JSON each time bought nothing, and the trip-plan monitor re-plans on a timer.
     *
     * An unreadable fixture leaves the tour's planner step with an empty result rather than taking the
     * screen down; the caption still explains what the rider is looking at.
     */
    private val captured: List<TripItinerary> by lazy {
        runCatching {
            context.resources.openRawResource(R.raw.demo_trip_plan)
                .use(OtpPlanParser::parse)
                .plan?.itineraries?.map { it.toTripItinerary() }
                .orEmpty()
        }.getOrElse {
            Log.e(TAG, "Failed to parse the bundled demo trip plan", it)
            emptyList()
        }
    }

    /** The bundled plan, re-dated so it departs from now. */
    private fun demoItineraries(): List<TripItinerary> = rebase(captured)

    /**
     * Shifts a captured plan onto the current clock so its departures read as "in a few minutes"
     * however long after capture the tour runs.
     *
     * The shift is a single delta applied to **every** absolute instant in the model, which keeps the
     * plan internally consistent: leg durations, waits and transfer slack are all differences between
     * these instants, so moving them together preserves the trip exactly as OTP planned it. The delta
     * is measured from the earliest departure rather than the itinerary's own start so that the first
     * option leaves [FIRST_DEPARTURE_LEAD] from now and the later options keep their real spacing
     * behind it — which is what makes the tour's "choose from several options" step have several
     * genuinely different options to choose from.
     *
     * `TripItinerary.startTime`, `TripLeg.startTime` and `TripLeg.endTime` are the model's only
     * absolute instants (`TripPlace` carries none), so this enumeration is complete rather than a
     * best-effort sweep — and [ServerTime]'s typed arithmetic is what makes that checkable.
     */
    private fun rebase(itineraries: List<TripItinerary>): List<TripItinerary> {
        val earliest = itineraries.minOfOrNull { it.startTime } ?: return itineraries
        // Demo mode answers entirely from the device, so the demo planner's clock *is* the device wall
        // clock. That identity is stated here — the one deliberate crossing between the two domains,
        // like TripState.withStatus's — rather than left implicit in a bare Long.
        val target = ServerTime(WallTime.now().epochMs) + FIRST_DEPARTURE_LEAD
        val shift = target - earliest
        return itineraries.map { itinerary ->
            itinerary.copy(
                startTime = itinerary.startTime + shift,
                legs = itinerary.legs.map { leg ->
                    leg.copy(startTime = leg.startTime + shift, endTime = leg.endTime + shift)
                }
            )
        }
    }

    private companion object {
        private const val TAG = "DemoTripPlanRepository"

        /**
         * How long after "now" the first demo itinerary departs. Far enough ahead that the walk to the
         * stop is plausible, close enough that the plan reads as something to leave for.
         */
        val FIRST_DEPARTURE_LEAD = 4.minutes
    }
}
