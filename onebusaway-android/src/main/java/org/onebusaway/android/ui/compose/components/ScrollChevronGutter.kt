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
package org.onebusaway.android.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/**
 * A fixed-width leading/trailing gutter holding the "more to scroll" chevron, [visible] when that
 * direction has more content off-screen. [pointsRight] picks the direction; the left reuses the right
 * chevron drawable rotated 180°. The slot keeps its width even when hidden so toggling it never
 * reflows the flanked content. Tapping it fires [onClick] (typically a one-viewport scroll toward that
 * edge); the tap is only wired up while [visible], so a hidden gutter is never an invisible tap target.
 *
 * Shared "there's more, keep scrolling" affordance for horizontally-scrolling strips — the ETA strip's
 * pill row and the trip-results option-card picker both flank their scroll with a pair of these.
 */
@Composable
fun ScrollChevronGutter(
    visible: Boolean,
    pointsRight: Boolean,
    contentDescriptionRes: Int,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(20.dp)
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            Icon(
                painter = painterResource(R.drawable.ic_navigation_chevron_right),
                // Resolved only while shown, since callers may recompose this frequently (e.g. the ETA
                // strip's per-second liveNow tick).
                contentDescription = stringResource(contentDescriptionRes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (pointsRight) 0f else 180f)
            )
        }
    }
}
