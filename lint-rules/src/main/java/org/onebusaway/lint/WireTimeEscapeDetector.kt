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
package org.onebusaway.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression

/**
 * A virtual `internal` on the wire→domain boundary. The serialization DTOs in
 * `org.onebusaway.android.api.contract` keep their time fields as bare `Long`s by design — the wire is
 * the wire — and the adapter layer mints them into the domain types (`ScheduleTime`, `ServerTime`,
 * `Duration`) exactly once, at the one place that knows which endpoint it is adapting. Nothing in the
 * language stops a future caller from reaching past the typed models straight into a DTO field, which
 * would re-open the untyped path the migration closed — complete with the seconds-vs-millis trap the two
 * same-named `StopTime` / `ScheduleStopTime` DTOs embody (60180 = seconds-into-day vs 1343663220000 =
 * epoch millis).
 *
 * So this check flags a read of a curated wire time-field getter from any file **outside** the adapter
 * allowlist ([ADAPTER_PACKAGES]). It is the layer-2 (package-scoped) visibility rule Kotlin can't
 * express within a single Gradle module, simulated by lint.
 *
 * Unlike [RawTimeDetector] / [PrematureUnwrapDetector], there is deliberately **no pass-through
 * exemption**: for a clock producer the domain is a property of the *clock*, so minting can happen
 * anywhere the reading is used; for a wire field the domain is a property of the *endpoint*, so only the
 * adapter — which knows which endpoint it adapts — can mint correctly. Reading the raw field elsewhere,
 * even to pass it straight on, is the escape.
 *
 * Demolition condition: if `:api` ever becomes a real Gradle module with `internal` DTOs, the language
 * enforces this and the check should be deleted. See lint-rules/README.md.
 */
class WireTimeEscapeDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UQualifiedReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                // Cheap, constant-per-file gate first — adapter/data files never resolve a read.
                if (isInAdapterLayer(context.uastFile?.packageName)) return
                val field = WIRE_TIME_FIELDS[TimeLintSupport.propertyKey(node, WIRE_FIELD_NAMES)] ?: return
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Wire time field `$field` read outside the adapter boundary — the DTOs in " +
                        "`api.contract` are raw `Long`s whose unit is a property of the endpoint (the two " +
                        "`StopTime` DTOs are seconds vs. epoch millis). Read the domain-typed model " +
                        "(e.g. `ObaTripSchedule.StopTime.arrivalTime: ScheduleTime`) instead, and let the " +
                        "adapter be the one place that mints the wire value.",
                )
            }
        }

    private fun isInAdapterLayer(packageName: String?): Boolean {
        packageName ?: return false
        return ADAPTER_PACKAGES.any { packageName == it || packageName.startsWith("$it.") }
    }

    companion object {
        /** Packages allowed to consume wire time fields — the mint boundary and its data sources. */
        private val ADAPTER_PACKAGES = listOf(
            "org.onebusaway.android.api.adapters",
            "org.onebusaway.android.api.data",
        )

        // Wire DTO time-field reads (owner#property) -> the field name (for the message). Bare `Long`s
        // on the `api.contract` serialization models; each must be minted in the adapter, never read
        // raw by app logic. New wire time fields are one line here.
        private const val PKG = "org.onebusaway.android.api.contract"
        private val WIRE_TIME_FIELDS: Map<String, String> = mapOf(
            "$PKG.ObaEnvelope#currentTime" to "currentTime",
            "$PKG.CurrentTime#time" to "time",
            "$PKG.TripStatus#serviceDate" to "serviceDate",
            "$PKG.TripStatus#scheduleDeviation" to "scheduleDeviation",
            "$PKG.TripStatus#lastUpdateTime" to "lastUpdateTime",
            "$PKG.TripStatus#lastLocationUpdateTime" to "lastLocationUpdateTime",
            "$PKG.StopTime#arrivalTime" to "arrivalTime",
            "$PKG.StopTime#departureTime" to "departureTime",
            "$PKG.ScheduleStopTime#arrivalTime" to "arrivalTime",
            "$PKG.ScheduleStopTime#departureTime" to "departureTime",
            "$PKG.ArrivalDeparture#scheduledArrivalTime" to "scheduledArrivalTime",
            "$PKG.ArrivalDeparture#predictedArrivalTime" to "predictedArrivalTime",
            "$PKG.ArrivalDeparture#scheduledDepartureTime" to "scheduledDepartureTime",
            "$PKG.ArrivalDeparture#predictedDepartureTime" to "predictedDepartureTime",
            "$PKG.ArrivalDeparture#serviceDate" to "serviceDate",
            "$PKG.Frequency#startTime" to "startTime",
            "$PKG.Frequency#endTime" to "endTime",
        )

        /** The wire field simple-names — the cheap gate before `propertyKey`'s resolve. */
        private val WIRE_FIELD_NAMES: Set<String> =
            WIRE_TIME_FIELDS.keys.mapTo(mutableSetOf()) { it.substringAfterLast('#') }

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "WireTimeEscape",
            briefDescription = "Wire DTO time field read outside the adapter boundary",
            explanation = """
                A raw time field on a `org.onebusaway.android.api.contract` serialization DTO is being \
                read outside the adapter layer. The wire DTOs keep their time fields as bare `Long`s on \
                purpose, but the *unit* of a wire time is a property of the endpoint it came from — the \
                trip-details `StopTime.arrivalTime` is seconds-since-service-day while the \
                schedule-for-stop `ScheduleStopTime.arrivalTime` is epoch millis, under identical names. \
                Only the adapter, which knows which endpoint it adapts, can mint the value into the right \
                domain type.

                Read the domain-typed model instead (`ObaTripSchedule.StopTime.arrivalTime: ScheduleTime`, \
                `ObaTripStatus.serviceDate`, …). Do not read the raw wire field in app logic, even to pass \
                it straight on — unlike a clock reading, there is no correct place to mint it other than \
                the endpoint-aware adapter, so there is deliberately no pass-through exemption.

                If a new data source legitimately needs to touch the DTO before the adapter, add its \
                package to the allowlist in `WireTimeEscapeDetector`, or suppress this id with a one-line \
                rationale and a tracking issue (see CLAUDE.md).
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WireTimeEscapeDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
