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
package org.onebusaway.android.ui.home.directions

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R

/** The tag the pinned-trip control is driven by in instrumented tests. */
const val PIN_TRIP_CONTROL_TEST_TAG = "pinTripControl"

/**
 * The "pin this trip" toggle, in the action row directly under the option picker (#2053).
 *
 * It lives here rather than in the directions action bar because that bar has no width to spare — its
 * own KDoc says as much, having already given up its clock glyph to fit the refresh button — and a
 * fifth 24dp glyph there would be both cramped and undiscoverable. A full-width row under the picker is
 * where the rider is already looking once they have chosen an option, and it has room to say what it
 * does in words.
 *
 * Filled when pinned and outlined when not, so the control's own weight says which state it is in
 * before its label is read.
 */
@Composable
fun PinTripControl(
    pinned: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(if (pinned) R.string.trip_plan_unpin else R.string.trip_plan_pin)
    val glyph = @Composable {
        Icon(
            painter = painterResource(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
    }
    val content = @Composable {
        glyph()
        Text(label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
    if (pinned) {
        Button(onClick = onToggle, enabled = enabled, modifier = modifier.testTag(PIN_TRIP_CONTROL_TEST_TAG)) {
            content()
        }
    } else {
        OutlinedButton(onClick = onToggle, enabled = enabled, modifier = modifier.testTag(PIN_TRIP_CONTROL_TEST_TAG)) {
            content()
        }
    }
}
