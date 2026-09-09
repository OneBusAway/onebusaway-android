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
package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import org.onebusaway.android.R
import org.onebusaway.android.time.LocalIllustrationTime
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalActions
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.arrivals.RouteRowGroup
import org.onebusaway.android.ui.arrivals.chronologicalArrivals
import org.onebusaway.android.ui.compose.components.MigrationChoice
import org.onebusaway.android.ui.compose.components.MigrationDialog
import org.onebusaway.android.ui.compose.components.PhonePreview

/** Illustrations are fixed sample departures, so this choice also works offline or out of service. */
@Composable
internal fun ArrivalDisplayChoiceDialog(
    initialMode: ArrivalDisplayMode,
    onSave: (ArrivalDisplayMode) -> Unit,
    onDismiss: () -> Unit,
    page: Int = 1,
    pageCount: Int = 1,
    onBack: (() -> Unit)? = null
) {
    var selected by rememberSaveable { mutableStateOf(initialMode) }
    MigrationDialog(
        title = stringResource(R.string.arrival_display_choose_title),
        page = page,
        pageCount = pageCount,
        onContinue = { onSave(selected) },
        onDismiss = onDismiss,
        onBack = onBack,
        content = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.arrival_display_choose_body))
                ArrivalDisplayMode.entries.forEach { mode ->
                    MigrationChoice(
                        title = stringResource(if (mode == ArrivalDisplayMode.TIME) R.string.arrival_display_time else R.string.arrival_display_route),
                        description = stringResource(if (mode == ArrivalDisplayMode.TIME) R.string.arrival_display_time_description else R.string.arrival_display_route_description),
                        previous = mode == ArrivalDisplayMode.TIME,
                        selected = selected == mode,
                        onSelect = { selected = mode }
                    ) {
                        PhonePreview(onSelect = { selected = mode }, Modifier.padding(top = 8.dp)) { SampleArrivalRows(mode) }
                    }
                }
            }
        }
    )
}

/** Actual arrival rows with a fixed clock and sample departures, shared by both migration pages. */
@Composable
internal fun SampleArrivalRows(mode: ArrivalDisplayMode, singleRoute: Boolean = false) {
    val context = LocalContext.current
    val downtown = stringResource(R.string.arrival_display_sample_downtown)
    val northgate = stringResource(R.string.arrival_display_sample_northgate)
    val now = remember {
        ServerTime(LocalDate.of(2026, 1, 1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    val arrivals = remember(context, downtown, northgate, now, singleRoute) {
        listOf(
            previewArrival("8", downtown, 3, tripId = "sample-eight-first", context = context, now = now),
            previewArrival("40", northgate, 5, predicted = false, tripId = "sample-forty", context = context, now = now),
            previewArrival("8", downtown, 12, scheduleDeviationMinutes = 2, tripId = "sample-eight-second", context = context, now = now)
        ).filter { !singleRoute || it.shortName == "8" }
    }
    val groups = remember(arrivals, mode) {
        if (mode == ArrivalDisplayMode.TIME) {
            chronologicalArrivals(arrivals).map { RouteRowGroup(listOf(it)) }
        } else {
            arrivals.groupBy { it.routeId }.values.map(::RouteRowGroup)
        }
    }
    val actions = remember(arrivals) {
        arrivals.associate { arrival ->
            arrival.tripId to ArrivalActions(
                tripId = arrival.tripId,
                routeId = arrival.routeId,
                routeShortName = arrival.shortName,
                routeLongName = arrival.headsign,
                routeColor = if (arrival.shortName == "8") 0xFF9C27B0.toInt() else 0xFF0A5B3E.toInt(),
                scheduleUrl = null,
                agencyName = null,
                blockId = null
            )
        }
    }
    val callbacks = remember { previewRowCallbacks() }
    CompositionLocalProvider(LocalIllustrationTime provides now) {
        Column {
            groups.forEach { group ->
                RouteArrivalRow(
                    group = group,
                    actionsFor = { actions[it.tripId] },
                    isFavorite = group.routeId == "route_8",
                    callbacks = callbacks,
                    chronological = mode == ArrivalDisplayMode.TIME
                )
            }
        }
    }
}
