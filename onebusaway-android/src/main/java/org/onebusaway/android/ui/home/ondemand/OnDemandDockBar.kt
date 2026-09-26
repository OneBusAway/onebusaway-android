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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.onebusaway.android.R
import org.onebusaway.android.models.ServiceArea
import org.onebusaway.android.ondemand.ProbePoint
import org.onebusaway.android.ondemand.ProbeSource
import org.onebusaway.android.ondemand.badgeText
import org.onebusaway.android.util.GeoPoint

private val BAR_RADIUS = 20.dp
private val BAR_MIN_HEIGHT = 72.dp
private val THUMBNAIL_SIZE = 56.dp
private val THUMBNAIL_RADIUS = 12.dp
private val BADGE_RADIUS = 9.dp
private val TRAILING_SIZE = 48.dp
private val DOT_SIZE = 7.dp
private val BADGE_FILL = Color(0xE63A3A3C)
private const val ACCESSIBILITY_FONT_SCALE = 1.3f

/**
 * The docked bar (spec §3.4, A–E): one page per stack entry, paged horizontally with dots and a
 * "%1$d of %2$d" badge on the thumbnail when there is more than one. The whole bar is one adjustable
 * element for TalkBack (its value is the badge; increment/decrement move pages) with a custom
 * "All services here" action, so the long-press picker has an accessible equivalent.
 */
@Composable
fun OnDemandDockBar(
    pages: List<DockBarPage>,
    probe: ProbePoint,
    geometry: Map<String, List<ServiceArea>>,
    onPageShown: (DockBarPage) -> Unit,
    onOpenDetail: (DockBarPage) -> Unit,
    onOpenPicker: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onZoomOut: () -> Unit,
    onPanTo: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, pages) {
        // Spec §2.3 highlights on a swipe, not on appearance: skip the page the bar composes on.
        snapshotFlow { pagerState.currentPage }.drop(1).collect { index -> pages.getOrNull(index)?.let(onPageShown) }
    }
    val badge = badgeText(pagerState.currentPage + 1, pages.size).resolve()
    val moreServicesLabel = stringResource(R.string.ondemand_bar_a11y_more_services)
    val shapes = remember(pages, geometry) {
        if (pages.all { geometry.containsKey(it.match.service.id) }) {
            pages.flatMap { page -> geometry.getValue(page.match.service.id).toThumbnailShapes(page.thumbnailColor) }
        } else {
            null
        }
    }
    Column(modifier.testTag(OnDemandDockTestTags.BAR), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = badge
                    customActions = listOf(
                        CustomAccessibilityAction(moreServicesLabel) {
                            onOpenPicker()
                            true
                        }
                    )
                    if (pages.size > 1) {
                        setProgress { target ->
                            scope.launch { pagerState.animateScrollToPage(target.roundToInt().coerceIn(0, pages.lastIndex)) }
                            true
                        }
                    }
                }
                .then(if (pages.size > 1) Modifier.progressSemantics(pagerState.currentPage.toFloat(), 0f..pages.lastIndex.toFloat(), pages.size - 2) else Modifier)
        ) { index ->
            val page = pages[index]
            DockBarPageContent(
                page = page,
                probe = probe,
                shapes = shapes,
                badge = badge.takeIf { showsBadge(pages.size) },
                onOpenDetail = { onOpenDetail(page) },
                onOpenPicker = onOpenPicker,
                onCall = onCall,
                onOpenUrl = onOpenUrl,
                onZoomOut = onZoomOut,
                onPanTo = onPanTo
            )
        }
        if (pages.size > 1) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { index ->
                    val current = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(DOT_SIZE)
                            .background(if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockBarPageContent(
    page: DockBarPage,
    probe: ProbePoint,
    shapes: List<ThumbnailShape>?,
    badge: String?,
    onOpenDetail: () -> Unit,
    onOpenPicker: () -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onZoomOut: () -> Unit,
    onPanTo: (GeoPoint) -> Unit
) {
    val textColor = Color(page.textColor)
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val zoomOutLabel = stringResource(R.string.ondemand_bar_a11y_zoom_out)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BAR_MIN_HEIGHT)
            .combinedClickable(onClick = onOpenDetail, onLongClick = onOpenPicker),
        shape = RoundedCornerShape(BAR_RADIUS),
        color = Color(page.color),
        contentColor = textColor,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(THUMBNAIL_SIZE)
                    .clip(RoundedCornerShape(THUMBNAIL_RADIUS))
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(THUMBNAIL_RADIUS))
                    .clickable(onClick = onZoomOut)
                    .semantics {
                        contentDescription = zoomOutLabel
                        role = Role.Button
                    }
                    .testTag(OnDemandDockTestTags.THUMBNAIL)
            ) {
                if (shapes == null) {
                    Box(Modifier.fillMaxSize().background(Color(page.color)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_directions_car), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                } else {
                    ZoneThumbnail(
                        shapes = shapes,
                        centre = probe.point,
                        probePoint = probe.point,
                        probeStyle = if (probe.source == ProbeSource.Rider) ProbeDotStyle.RIDER else ProbeDotStyle.MAP_CENTER,
                        modifier = Modifier.fillMaxSize().background(Color.White)
                    )
                }
                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .background(BADGE_FILL, RoundedCornerShape(BADGE_RADIUS))
                            .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(BADGE_RADIUS))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .testTag(OnDemandDockTestTags.BADGE)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                val title = page.title?.resolve()
                if (title != null) {
                    Text(page.match.service.name, style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = if (fontScale >= ACCESSIBILITY_FONT_SCALE) 2 else 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(page.match.service.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            TrailingButton(page, textColor, onOpenDetail, onCall, onOpenUrl, onPanTo)
        }
    }
}

@Composable
private fun TrailingButton(page: DockBarPage, tint: Color, onOpenDetail: () -> Unit, onCall: (String) -> Unit, onOpenUrl: (String) -> Unit, onPanTo: (GeoPoint) -> Unit) {
    val (iconRes, label, action) = when (page.trailing) {
        TrailingAction.PHONE -> Triple(R.drawable.ic_call, stringResource(R.string.ondemand_bar_a11y_call, page.match.service.name)) { page.contact.phone?.let(onCall) }
        TrailingAction.URL -> Triple(R.drawable.ic_open_in_new, stringResource(R.string.ondemand_book_online)) { page.contact.url?.let(onOpenUrl) }
        TrailingAction.CHEVRON_EDGE -> Triple(R.drawable.ic_navigation_chevron_right, stringResource(R.string.ondemand_bar_a11y_show_edge)) { page.edgePoint?.let(onPanTo) }
        TrailingAction.CHEVRON_DETAIL -> Triple(R.drawable.ic_navigation_chevron_right, stringResource(R.string.ondemand_card_details), onOpenDetail)
        TrailingAction.NONE -> return
    }
    IconButton(
        onClick = { action() },
        modifier = Modifier
            .size(TRAILING_SIZE)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
            .testTag(OnDemandDockTestTags.TRAILING)
    ) {
        Icon(painterResource(iconRes), contentDescription = label, tint = tint)
    }
}
