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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.OnDemandServiceItem

/** The stop's on-demand services (GTFS-Flex pointers), one tappable row each, ahead of the route rows. */
@Composable
internal fun OnDemandServicesCard(
    items: List<OnDemandServiceItem>,
    onOpen: (serviceId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.ondemand_card_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        for (item in items) {
            Surface(
                onClick = { onOpen(item.id) },
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = item.phoneNumber?.let { stringResource(R.string.ondemand_card_phone, it) } ?: stringResource(R.string.ondemand_card_tap),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
