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
package org.onebusaway.android.ui.report.types

/** What a report-type row does when tapped; the host carries out the navigation/intents. */
enum class ReportAction { CUSTOMER_SERVICE, STOP_PROBLEM, ARRIVAL_PROBLEM, APP_FEEDBACK }

/** One row in the "Send feedback" type list. */
data class ReportType(
    val title: String,
    val description: String,
    val iconRes: Int,
    val action: ReportAction
)

/** The fixed action order shared by both report_types and report_types_without_open311 arrays. */
val REPORT_TYPE_ACTIONS = listOf(
    ReportAction.CUSTOMER_SERVICE,
    ReportAction.STOP_PROBLEM,
    ReportAction.ARRIVAL_PROBLEM,
    ReportAction.APP_FEEDBACK
)

/**
 * Drops the "Send App Feedback" row when the region defines no contact email, matching the legacy
 * ReportTypeListFragment gate. Pure, so the gating is unit-testable without Android.
 */
object ReportTypeGate {

    fun apply(types: List<ReportType>, emailDefined: Boolean): List<ReportType> =
        if (emailDefined) types else types.filterNot { it.action == ReportAction.APP_FEEDBACK }
}
