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
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.ONDEMAND_OUTSIDE_GRAY
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.bookingTag
import org.onebusaway.android.ondemand.pickerSubtitle
import org.onebusaway.android.ondemand.statusText
import org.onebusaway.android.ondemand.tagText
import org.onebusaway.android.ui.compose.components.SheetDragHandle
import org.onebusaway.android.ui.icons.AppIcons

private val LIST_RADIUS = 18.dp

/**
 * The overlap picker (spec §3.5, screen 2): every service at the probe point in §2.6 order, one row
 * each with its status line and its areas · booking tag. Tapping a row reports the match; the host
 * highlights it and pushes the detail page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnDemandPickerSheet(
    request: PickerRequest,
    colors: Map<String, Int>,
    now: Instant,
    onSelect: (OnDemandMatch) -> Unit,
    onDismiss: () -> Unit
) {
    val count = request.matches.size
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = { SheetDragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (request.nearby) pluralStringResource(R.plurals.ondemand_picker_title_nearby, count, count) else pluralStringResource(R.plurals.ondemand_picker_title, count, count),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pickerSubtitle(request.probe.source, request.locality).resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.dismiss))
                }
            }
            Surface(shape = RoundedCornerShape(LIST_RADIUS), color = MaterialTheme.colorScheme.surface) {
                Column {
                    request.matches.forEachIndexed { index, match ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 68.dp))
                        PickerRow(match, colors[match.service.id] ?: ONDEMAND_OUTSIDE_GRAY, now) { onSelect(match) }
                    }
                }
            }
            Text(
                text = stringResource(R.string.ondemand_picker_footer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PickerRow(match: OnDemandMatch, color: Int, now: Instant, onClick: () -> Unit) {
    val locale = LocalLocale.current.platformLocale
    val availability = match.availability
    val status = statusText(availability.status, availability.zone, now, locale)?.resolve()
    val green = colorResource(R.color.ondemand_open_green)
    val areas = pickerAreasText(match.service).resolveCopy()
    val tag = bookingTag(availability.tags)?.let { tagText(it).resolve() }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServiceDisc(color)
        Column(Modifier.weight(1f)) {
            Text(match.service.name, style = MaterialTheme.typography.titleMedium)
            if (status != null) {
                Text(
                    text = buildAnnotatedString {
                        if (availability.status is OnDemandStatus.OpenNow) withStyle(SpanStyle(color = green, fontWeight = FontWeight.SemiBold)) { append(status) } else append(status)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = listOfNotNull(areas, tag).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
