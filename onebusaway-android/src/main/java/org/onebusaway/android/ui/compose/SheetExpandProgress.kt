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
@file:OptIn(ExperimentalMaterial3Api::class)

package org.onebusaway.android.ui.compose

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * The live "openness" of a Material3 `BottomSheetScaffold` sheet as a 0..1 fraction (0 = resting at
 * the peek, 1 = fully expanded), for driving drag-tracked animations frame by frame. Attach
 * [measureModifier] to the scaffold so its container height is known, then read [fraction] (a stable
 * provider) wherever the animation needs the value.
 *
 * The fraction comes straight from the sheet's AnchoredDraggable offset: the peek anchor sits at
 * `containerHeight - peekHeight` and the expanded anchor at ~0, so progress = (peekAnchor − offset) /
 * peekAnchor. `requireOffset()` throws until the sheet is first laid out, so we report 0 (peek) until
 * then. Created via [rememberSheetExpandProgress].
 */
@Stable
class SheetExpandProgress internal constructor(
    private val sheetState: SheetState,
    private val peekHeightPx: () -> Float,
) {
    private var containerHeightPx by mutableIntStateOf(0)

    /** Attach to the `BottomSheetScaffold` (or its container) so the sheet height feeds the fraction. */
    val measureModifier: Modifier = Modifier.onSizeChanged { containerHeightPx = it.height }

    /**
     * The current open fraction in 0..1 (0 = peek, 1 = expanded), as a stable lambda so it can be
     * passed through composables without breaking recomposition skipping. It reads snapshot state, so
     * callers that invoke it inside `derivedStateOf` / `snapshotFlow` / composition re-run as the sheet
     * drags.
     */
    val fraction: () -> Float = {
        // While the sheet is animating to its peek (a collapse settle), report 0 rather than the live
        // offset. The settle uses a slightly under-damped spring that overshoots a few px above the peek
        // anchor as it comes to rest; that would make this briefly positive and flash the below-peek
        // content in for a frame. During a drag (not animating) or a settle toward Expanded, the live
        // offset is used, so the reveal still tracks the drag in lockstep.
        if (sheetState.isAnimationRunning && sheetState.targetValue == SheetValue.PartiallyExpanded) {
            0f
        } else {
            val peekAnchor = containerHeightPx - peekHeightPx()
            val offset = runCatching { sheetState.requireOffset() }.getOrNull()
            if (peekAnchor <= 0f || offset == null) {
                0f
            } else {
                ((peekAnchor - offset) / peekAnchor).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Remembers a [SheetExpandProgress] for [sheetState] whose peek anchor is [peekHeight] (the sheet's
 * collapsed peek height). The returned object is stable across recomposition; [peekHeight] is tracked
 * live, so it stays correct as the peek resizes.
 */
@Composable
fun rememberSheetExpandProgress(sheetState: SheetState, peekHeight: Dp): SheetExpandProgress {
    val peekHeightPx = with(LocalDensity.current) { peekHeight.toPx() }
    val peekHeightPxState = rememberUpdatedState(peekHeightPx)
    return remember(sheetState) { SheetExpandProgress(sheetState) { peekHeightPxState.value } }
}
