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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalDisplayMode

@Composable
fun ArrivalDisplayModeSwitch(
    mode: ArrivalDisplayMode,
    onChange: (ArrivalDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    label: String = stringResource(R.string.arrival_display_switch)
) {
    Row(
        modifier.fillMaxWidth().padding(contentPadding),
        horizontalArrangement = Arrangement.End
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.semantics { contentDescription = label }) {
            ArrivalDisplayMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, ArrivalDisplayMode.entries.size),
                    label = { Text(stringResource(option.labelRes)) }
                )
            }
        }
    }
}

internal val ArrivalDisplayMode.labelRes: Int
    get() = when (this) {
        ArrivalDisplayMode.TIME -> R.string.arrival_display_time
        ArrivalDisplayMode.ROUTE -> R.string.arrival_display_route
    }

/** Read the default once per drawer session; refreshes and temporary switches never persist it. */
@Composable
internal fun rememberArrivalDisplayMode(sessionKey: String, defaultMode: () -> ArrivalDisplayMode): MutableState<ArrivalDisplayMode> = rememberSaveable(sessionKey) { mutableStateOf(defaultMode()) }
