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

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import java.io.IOException
import org.onebusaway.android.R

/**
 * A classified trip-plan failure. Two levels of granularity are carried so the UI can be both concise
 * and specific:
 *
 *  - [category] — the coarse *kind* of problem, surfaced to the user as a consistent header (and its
 *    [severity] colour). This is the "richer ontology": a plan can fail because we couldn't reach the
 *    planner, because an endpoint is unusable, because there's no service at the chosen time, because
 *    there's simply no route, or because the request itself was rejected — and those are genuinely
 *    different situations, each with one stable heading (e.g. every no-route failure reads
 *    "Cannot find route").
 *  - [detailRes] — the specific, already-localized explanation (the exact OTP1/OTP2 message), shown as
 *    the reason line beneath the header, so none of the fine-grained wire information is flattened away.
 *
 * Both planner paths — OTP1 REST ([otp1ErrorFor]) and OTP2 GraphQL ([otp2ErrorFor]) — classify their
 * wire errors into this type and throw it as a [TripPlanException]; [TripPlanViewModel] surfaces it as
 * [PlanResult.Error], and the directions UI renders the category header above the reason.
 */
data class TripPlanError(
    val category: Category,
    @get:StringRes val detailRes: Int,
) {
    /**
     * The coarse failure kind. [headerRes] is the stable heading naming the category; [severity]
     * drives the header's colour.
     */
    enum class Category(
        @get:StringRes val headerRes: Int,
        val severity: Severity,
    ) {
        /** Couldn't reach or get a usable answer from the planner (timeout, transport failure). */
        CONNECTIVITY(R.string.tripplanner_error_header_connectivity, Severity.ERROR),

        /** An endpoint couldn't be used — not geocoded, ambiguous, or not accessible. */
        LOCATION(R.string.tripplanner_error_header_location, Severity.WARNING),

        /** Transit doesn't run for the chosen time/date (no times, outside the service period). */
        SCHEDULE(R.string.tripplanner_error_header_schedule, Severity.WARNING),

        /** No usable route between the two points (no path, no stops in range, out of coverage). */
        NO_ROUTE(R.string.tripplanner_error_header_no_route, Severity.WARNING),

        /** Not a failure so much as advice — walking beats transit for this trip. */
        ADVISORY(R.string.tripplanner_error_header_advisory, Severity.INFO),

        /** The request itself was rejected or unclassified (bad parameter, no server, unknown). */
        REQUEST(R.string.tripplanner_error_header_request, Severity.ERROR),
    }

    /**
     * How alarming the failure is; [colorRes] is the header colour it maps to (a `md_theme_severity*`
     * text colour tuned for the inverting `inverseSurface` snackbar bar): [INFO] blue (a tip — not a
     * failure), [WARNING] amber (a valid, user-actionable non-result), [ERROR] red (something failed).
     */
    enum class Severity(@get:ColorRes val colorRes: Int) {
        INFO(R.color.md_theme_severityInfo),
        WARNING(R.color.md_theme_severityWarning),
        ERROR(R.color.md_theme_severityError),
    }

    companion object {
        /** The generic fallback when a failure carries no classified [TripPlanError]. */
        val Unknown = TripPlanError(Category.REQUEST, R.string.tripplanner_error_not_defined)

        /** No route between the endpoints — also the (defensive) empty-results case. */
        val NoRoute = TripPlanError(Category.NO_ROUTE, R.string.tripplanner_error_path_not_found)
    }
}

/**
 * Carries a classified [TripPlanError] through the throw-based planner contract. Extends [IOException]
 * so the existing `runCatching`/`runCatchingCancellable` wrapping in [DefaultTripPlanRepository] (and
 * the monitor's empty-list-on-failure `planBlocking`) handles it unchanged.
 */
class TripPlanException(val error: TripPlanError, cause: Throwable? = null) : IOException(cause)

/** The [TripPlanError] for a thrown [Throwable], falling back to [TripPlanError.Unknown]. */
fun Throwable.toTripPlanError(): TripPlanError =
    (this as? TripPlanException)?.error ?: TripPlanError.Unknown
