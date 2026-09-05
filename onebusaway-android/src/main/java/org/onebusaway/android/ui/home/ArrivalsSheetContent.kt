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
package org.onebusaway.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp

/**
 * A single bounded viewport for both arrivals drawers. Lazy lists wrap short content and scroll at
 * [maxHeight]; the resulting layout also sets Material's expanded sheet anchor. There is no separate
 * measurement viewport to overflow, center, or clip away the first rows (#2282).
 *
 * [onHeightChanged] observes the size (including list padding) for the collapsed peek only. It must
 * not feed back into this viewport's constraints, so a short list can grow on the next response.
 */
@Composable
internal fun ArrivalsSheetContent(
    maxHeight: Dp,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier.fillMaxWidth().heightIn(max = maxHeight)
            .onSizeChanged { onHeightChanged(it.height) }
    ) {
        content()
    }
}
