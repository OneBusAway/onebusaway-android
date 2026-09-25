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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

// The drag handle's geometry — the visible bar plus the vertical padding above and below it. Shared by
// every bottom-sheet grab bar (the arrivals sheet, the directions sheet) and their peek-height math, so
// the handles can't drift apart.
val DRAG_HANDLE_BAR_HEIGHT = 4.dp
val DRAG_HANDLE_VERTICAL_PADDING = 9.dp
val DRAG_HANDLE_HEIGHT = DRAG_HANDLE_BAR_HEIGHT + DRAG_HANDLE_VERTICAL_PADDING * 2

/**
 * The short tinted grab-bar pill drawn inside a bottom-sheet drag handle — a muted grey matching the
 * panel chrome, so it reads as part of the sheet. Callers wrap it in their own padded, gesture-bearing
 * (tap and/or drag) box.
 */
@Composable
fun DragHandleBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = colorResource(R.color.navdrawer_icon_tint),
        shape = RoundedCornerShape(percent = 50)
    ) {
        Box(Modifier.size(width = 32.dp, height = DRAG_HANDLE_BAR_HEIGHT))
    }
}

/**
 * [DragHandleBar] centred in the shared [DRAG_HANDLE_HEIGHT] band — the handle to hand a Material
 * `BottomSheetScaffold` as its `sheetDragHandle`. The scaffold wraps whatever it's given with the sheet's
 * tap-to-toggle and expand/collapse accessibility actions, so this only has to supply the geometry;
 * sizing the band here (rather than using `BottomSheetDefaults.DragHandle`, whose 48dp band read as an
 * oversized grab strip next to the arrivals sheet — #2240) keeps a handle-only peek height computable
 * from an app-owned constant instead of one of M3's private ones, and keeps the two sheets' handles the
 * same size.
 */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(vertical = DRAG_HANDLE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center
    ) {
        DragHandleBar()
    }
}
