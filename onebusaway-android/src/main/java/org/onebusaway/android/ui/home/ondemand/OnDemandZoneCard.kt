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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.Instant
import org.onebusaway.android.R
import org.onebusaway.android.ondemand.OnDemandAvailability
import org.onebusaway.android.ondemand.OnDemandMatch
import org.onebusaway.android.ondemand.OnDemandStatus
import org.onebusaway.android.ondemand.cardMeta

/** Stable handles for the on-device tests. */
object OnDemandDockTestTags {
    const val CARD = "onDemandZoneCard"
    const val BAR = "onDemandDockBar"
    const val THUMBNAIL = "onDemandDockThumbnail"
    const val TRAILING = "onDemandDockTrailing"
    const val BADGE = "onDemandDockBadge"
}

private val CARD_RADIUS = 22.dp
private val ICON_DISC = 40.dp
private val PILL_HEIGHT = 40.dp

/**
 * The zone card (spec §3.3, screen 1): the first inside match at region level, its meta line, a
 * primary contact pill, "Details", and the "more services" footer when others also contain the point.
 */
@Composable
fun OnDemandZoneCard(
    match: OnDemandMatch,
    moreCount: Int,
    color: Int,
    now: Instant,
    onOpenDetail: () -> Unit,
    onOpenPicker: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = cardPrimary(match.service)
    Surface(
        modifier = modifier.testTag(OnDemandDockTestTags.CARD),
        shape = RoundedCornerShape(CARD_RADIUS),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ServiceDisc(color)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ondemand_card_eyebrow),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = match.service.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metaLine(match.availability, now),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (primary) {
                    is CardPrimary.Call -> Button(onClick = { onCall(primary.phone) }, modifier = Modifier.weight(1.5f).height(PILL_HEIGHT)) {
                        Icon(painterResource(R.drawable.ic_call), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.ondemand_card_call_to_book), modifier = Modifier.padding(start = 8.dp))
                    }
                    is CardPrimary.Online -> Button(onClick = { onOpenUrl(primary.url) }, modifier = Modifier.weight(1.5f).height(PILL_HEIGHT)) {
                        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.ondemand_book_online), modifier = Modifier.padding(start = 8.dp))
                    }
                    null -> Unit
                }
                // Alone in the row, the weight makes Details fill it (spec §3.3).
                FilledTonalButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.weight(1f).height(PILL_HEIGHT),
                    // Spec §5: the Details fill is the light brand tint with accent text.
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.ondemand_card_details))
                }
            }
            if (moreCount > 0) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPicker).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.ondemand_card_more_services, moreCount, moreCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(painterResource(R.drawable.ic_navigation_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** A 40 dp disc in the service colour with the white car glyph. */
@Composable
internal fun ServiceDisc(color: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(ICON_DISC).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_directions_car), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Status · booking tag, with "Open…" in the open-green weight when the service is open now (spec §2.8). */
@Composable
internal fun metaLine(availability: OnDemandAvailability, now: Instant): AnnotatedString {
    val locale = LocalLocale.current.platformLocale
    val segments = cardMeta(availability, now, locale).map { it.resolve() }
    val open = availability.status is OnDemandStatus.OpenNow
    val green = colorResource(R.color.ondemand_open_green)
    return buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            if (index > 0) append(" · ")
            if (index == 0 && open) withStyle(SpanStyle(color = green, fontWeight = FontWeight.SemiBold)) { append(segment) } else append(segment)
        }
    }
}
