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
package org.onebusaway.android.ui.arrivals.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode
import org.onebusaway.android.ui.compose.components.SegmentedChoice

@Composable
fun ArrivalDisplayModeSwitch(
    mode: ArrivalDisplayMode,
    onChange: (ArrivalDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    label: String = stringResource(R.string.arrival_display_switch)
) {
    SegmentedChoice(
        options = ArrivalDisplayMode.entries,
        selected = mode,
        onChange = onChange,
        label = label,
        optionLabel = { it.labelRes },
        modifier = modifier,
        contentPadding = contentPadding
    )
}

internal val ArrivalDisplayMode.labelRes: Int
    get() = when (this) {
        ArrivalDisplayMode.TIME -> R.string.arrival_display_time
        ArrivalDisplayMode.ROUTE -> R.string.arrival_display_route
    }

/** Read the default once per drawer session; refreshes and temporary switches never persist it. */
@Composable
internal fun rememberArrivalDisplayMode(sessionKey: String, defaultMode: () -> ArrivalDisplayMode): MutableState<ArrivalDisplayMode> = rememberSaveable(sessionKey) { mutableStateOf(defaultMode()) }
