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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Render real UI at phone proportions; every preview tap selects the enclosing choice. */
@Composable
internal fun PhonePreview(onSelect: () -> Unit, modifier: Modifier = Modifier, phoneWidth: Dp = 360.dp, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Box(Modifier.phonePreviewScale(phoneWidth)) { content() }
        // Cover sample stop/ETA/star controls so illustrations never open sample-trip actions.
        Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null, onClick = onSelect))
    }
}

/** Preserve the drawer's real proportions instead of squeezing its columns into a dialog card. */
private fun Modifier.phonePreviewScale(previewWidth: Dp): Modifier = layout { measurable, constraints ->
    val phoneWidth = previewWidth.roundToPx()
    val width = minOf(phoneWidth, constraints.maxWidth)
    val scale = width.toFloat() / phoneWidth
    val placeable = measurable.measure(Constraints.fixedWidth(phoneWidth))
    layout(width, (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}
