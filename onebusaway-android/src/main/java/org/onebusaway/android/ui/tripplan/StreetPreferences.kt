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
package org.onebusaway.android.ui.tripplan

/**
 * How much walking the rider wants in an itinerary, on a five-point scale.
 *
 * Named for the **amount of walking**, which is what the rider is choosing; the OTP field behind it
 * (`walk.reluctance`) runs the other way, so [MINIMUM] walking is the *highest* reluctance and
 * [MAXIMUM] the lowest. Keeping the scale in rider terms and the inversion at the wire boundary
 * (`Otp2PlanRequestBuilder`) means no call site has to remember which direction the number goes.
 *
 * The OTP2 replacement for the OTP1-only "maximum walk distance" field: OTP2 removed
 * `maxWalkDistance` from the routing API outright (its own migration guide names `walkReluctance` as
 * the way to "reduce the amount of walking … in returned itineraries", with the surviving hard caps
 * living in the *server's* `router-config.json` as `maxAccessEgressDurationForMode`, not in a
 * request). So on an OTP2 region the rider states a preference, not a cap — and the UI says so.
 *
 * Deliberately carries no numbers: the reluctance values live at the wire boundary with their
 * sourcing, so this type stays a plain statement of intent that the OTP1 path can ignore.
 */
enum class WalkPreference {
    MINIMUM,
    LOW,

    /** The neutral point — sends nothing, so the region's own configured reluctance applies. */
    MEDIUM,
    HIGH,
    MAXIMUM
}

/**
 * How much cycling the rider wants in an itinerary, on the same five-point scale as [WalkPreference]
 * and inverted the same way ([MINIMUM] cycling is the highest `bicycle.reluctance`).
 *
 * The closest thing there is to "how far will it route me by bike": **no** OTP version has ever had a
 * `maxBikeDistance`, and OTP2's routing API carries no street-leg distance or duration cap at all
 * (the only hard limit, `accessEgress.maxDurationForMode`, is server-side `router-config.json`). So
 * this does not raise a ceiling — it stops the router preferring a transit leg over a longer ride,
 * which is what actually lengthens bike legs in practice.
 *
 * If the region's server caps bike access/egress duration, that cap binds first and no value here
 * gets past it; the symptom is bike legs bunching just under a fixed number regardless of setting.
 */
enum class BikePreference {
    MINIMUM,
    LOW,

    /** The neutral point — sends nothing, so the region's own configured reluctance applies. */
    MEDIUM,
    HIGH,
    MAXIMUM
}

/**
 * What a cycling leg should be optimized for — OTP2's `CyclingOptimizationType`, as rider intent.
 *
 * Distinct from [BikePreference]: this picks *which streets* a bike leg uses, not *how much* cycling
 * the router is willing to put in the itinerary.
 *
 * OTP2-only on purpose. OTP1 expresses the same idea through its single-valued `optimize` query
 * parameter, which this app already spends on `TRANSFERS` vs `QUICK` (the "minimize transfers"
 * switch, see `TripRequestBuilder.getOptimizeType`); sending `SAFE`/`FLAT` there would silently
 * switch minimize-transfers off. Rather than have one setting quietly disable another, the OTP1 path
 * leaves cycling optimization alone.
 *
 * [DEFAULT] omits the field entirely rather than naming OTP's documented default (`safe-streets`),
 * so a region that tuned `bicycle.optimization` in its own router config keeps that tuning.
 */
enum class CyclingPreference {
    /** Send nothing; the region's own configured cycling optimization applies. */
    DEFAULT,

    /** Shortest duration, ignoring how safe the streets are. */
    FASTEST,

    /** The safest streets, weighted even above OTP's default `safe-streets`. */
    SAFEST,

    /** Flattest route — emphasizes avoiding elevation change over safety or duration. */
    FLATTEST
}

/**
 * The [T] whose name is [name], or [default] when [name] is null or unrecognized.
 *
 * Both preferences are persisted (and carried through Bundles) by enum *name*, so reordering an
 * enum can't reinterpret a stored value the way an ordinal would. An unrecognized name means the
 * value was written by a build that knows an option this one doesn't — falling back to the
 * "send nothing, let the server decide" default is the only reading that can't misroute.
 */
internal inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default
