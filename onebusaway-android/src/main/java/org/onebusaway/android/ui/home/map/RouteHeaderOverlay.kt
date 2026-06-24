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
package org.onebusaway.android.ui.home.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.map.RouteHeader
import org.onebusaway.android.ui.compose.components.LineBadge
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The route-mode header overlay (the Compose replacement for the legacy `route_info_head.xml` /
 * `RouteMapController.RoutePopup`). Renders the route short/long name + agency (or a spinner while the
 * route loads) and a cancel button that exits route mode. It reports its measured height via [onHeight]
 * so the host can set the map's top padding (keeping vehicle markers visible under it).
 */
@Composable
fun RouteHeaderOverlay(
    header: RouteHeader,
    onCancel: () -> Unit,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.onSizeChanged { onHeight(it.height) },
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
    ) {
        if (header.loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CircularProgressIndicator(Modifier.size(48.dp))
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Auto-shrinks (and wraps) a long route short name to fit a fixed slot rather than
                // crowding out the long-name/agency column — the shared route-badge style.
                LineBadge(
                    text = header.shortName,
                    maxFontSize = 45.sp,
                    width = 96.dp,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                Column(Modifier.weight(1f)) {
                    if (header.longName.isNotEmpty()) {
                        Text(
                            text = header.longName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (header.agency.isNotEmpty()) {
                        Text(text = header.agency)
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_navigation_close),
                        contentDescription = stringResource(android.R.string.cancel),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun RouteHeaderOverlayPreview() {
    ObaTheme {
        Column {
            // A short route short name.
            RouteHeaderOverlay(
                header = RouteHeader(
                    loading = false,
                    shortName = "40",
                    longName = "Downtown Seattle - Northgate",
                    agency = "King County Metro",
                ),
                onCancel = {},
                onHeight = {},
            )
            Spacer(Modifier.size(12.dp))
            // A long route short name: it should ellipsize rather than crowd out the long name.
            RouteHeaderOverlay(
                header = RouteHeader(
                    loading = false,
                    shortName = "Mount Si. Trailhead",
                    longName = "North Bend - Snoqualmie Falls Express",
                    agency = "King County Metro",
                ),
                onCancel = {},
                onHeight = {},
            )
            Spacer(Modifier.size(12.dp))
            // The loading state (spinner shown while the route resolves).
            RouteHeaderOverlay(
                header = RouteHeader(loading = true, shortName = "", longName = "", agency = ""),
                onCancel = {},
                onHeight = {},
            )
        }
    }
}
