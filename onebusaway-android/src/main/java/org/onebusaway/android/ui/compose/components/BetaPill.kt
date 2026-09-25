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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.theme.ObaTheme

/** Fully rounded, so the pill reads as a tag pinned to the label rather than as a second button. */
private val PILL_SHAPE = RoundedCornerShape(percent = 50)
private val PILL_H_PADDING = 6.dp
private val PILL_V_PADDING = 1.dp

/** Wide tracking is what makes four capitals at this size read as a badge rather than as a shouted word. */
private val PILL_LETTER_SPACING = 0.5.sp

/**
 * The "BETA" tag that marks a feature as still in beta — a small capitalized pill on a secondary
 * container, sat beside the feature's own name.
 *
 * It exists so that name can stay a plain, translatable noun phrase ("Plan a trip") instead of carrying
 * "(beta)" baked into every locale's string (#2253): the qualifier is a piece of *presentation*, and
 * gluing it into the string means every translation has to repeat it, no caller can show the name
 * without it, and it can never be styled apart from the label it qualifies. Rendered as its own
 * composable it is one word in one place, and dropping it when a feature graduates is a one-line change
 * at each call site rather than a string edit in every locale.
 *
 * TalkBack reads it as part of the surrounding row's label — "Plan a trip, beta" — which is the whole
 * point of the qualifier, so it is deliberately not marked as decorative.
 */
@Composable
fun BetaPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = PILL_SHAPE,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = stringResource(R.string.beta_pill),
            modifier = Modifier.padding(horizontal = PILL_H_PADDING, vertical = PILL_V_PADDING),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = PILL_LETTER_SPACING
        )
    }
}

@Preview(showBackground = true, name = "BetaPill — beside a label")
@Composable
private fun BetaPillPreview() {
    ObaTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            // In context, which is the only way the pill is used — its whole job is to sit next to a
            // feature's name, and it takes no parameters, so a bare instance would show nothing more.
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Plan a trip", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                BetaPill()
            }
        }
    }
}
