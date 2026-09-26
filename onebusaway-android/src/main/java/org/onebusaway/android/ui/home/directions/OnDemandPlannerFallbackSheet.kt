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
package org.onebusaway.android.ui.home.directions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ui.compose.components.LoadingContent
import org.onebusaway.android.ui.compose.components.SheetDragHandle
import org.onebusaway.android.ui.home.ondemand.CardPrimary
import org.onebusaway.android.ui.home.ondemand.PlannerFallbackState
import org.onebusaway.android.ui.home.ondemand.ServiceDisc
import org.onebusaway.android.ui.home.ondemand.cardPrimary
import org.onebusaway.android.ui.home.ondemand.metaLine

private val CARD_RADIUS = 22.dp
private val PILL_HEIGHT = 40.dp

/** Spec §3.8 (screen 5): the services that serve both planned ends, with the caption and the picker link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDemandPlannerFallbackSheet(
    state: PlannerFallbackState,
    colors: Map<String, Int>,
    now: Instant,
    onOpenDetail: (OnDemandMatch, ProbePoint) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowAll: (List<OnDemandMatch>, ProbePoint) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ondemand_planner_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when (state) {
                is PlannerFallbackState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingContent() }
                is PlannerFallbackState.Ready -> ReadyContent(state, colors, now, onOpenDetail, onCall, onOpenUrl, onShowAll)
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: PlannerFallbackState.Ready,
    colors: Map<String, Int>,
    now: Instant,
    onOpenDetail: (OnDemandMatch, ProbePoint) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowAll: (List<OnDemandMatch>, ProbePoint) -> Unit
) {
    val qualifying = state.qualification.qualifying
    val hidden = state.qualification.hiddenCount
    if (qualifying.isEmpty() && hidden == 0) {
        Text(stringResource(R.string.ondemand_planner_empty), style = MaterialTheme.typography.bodyMedium)
        return
    }
    qualifying.forEach { match ->
        PlannerServiceCard(match, colors[match.service.id] ?: ONDEMAND_OUTSIDE_GRAY, now, { onOpenDetail(match, state.origin) }, onCall, onOpenUrl)
    }
    Text(stringResource(R.string.ondemand_planner_status_now), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.ondemand_planner_caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        text = stringResource(R.string.ondemand_planner_show_all),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onShowAll(state.originMatches.filter { it.isInside }, state.origin) }.padding(vertical = 4.dp)
    )
}

/** The screen-1 card without its footer: name, "Serves both locations", the meta line and one primary pill. */
@Composable
private fun PlannerServiceCard(match: OnDemandMatch, color: Int, now: Instant, onOpenDetail: () -> Unit, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val primary = cardPrimary(match.service)
    Surface(shape = RoundedCornerShape(CARD_RADIUS), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenDetail), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ServiceDisc(color)
                Column(Modifier.weight(1f)) {
                    Text(match.service.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ondemand_planner_serves_both), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metaLine(match.availability, now), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (primary) {
                is CardPrimary.Call -> Button(onClick = { onCall(primary.phone) }, modifier = Modifier.fillMaxWidth().height(PILL_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_card_call_to_book), modifier = Modifier.padding(start = 8.dp))
                }
                is CardPrimary.Online -> Button(onClick = { onOpenUrl(primary.url) }, modifier = Modifier.fillMaxWidth().height(PILL_HEIGHT)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.ondemand_book_online), modifier = Modifier.padding(start = 8.dp))
                }
                null -> Unit
            }
        }
    }
}
