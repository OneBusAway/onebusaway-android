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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import org.onebusaway.android.R
import org.onebusaway.android.region.CustomRegionRequest

/**
 * The consent gate for an `add-region` deep link (#2027, #2030), rendered at the activity's setContent
 * root alongside [RegionPickerHost] so it overlays whatever screen the link landed on.
 *
 * The dialog names the servers the link would switch to, because that host is the only thing the rider
 * can actually judge: the region *name* is attacker-controlled text and says nothing about where the
 * data comes from. Declining is the safe default, so Cancel is the dismiss action and back/scrim both
 * decline rather than leaving the request hanging.
 */
@Composable
internal fun AddRegionDialog(
    pending: StateFlow<CustomRegionRequest?>,
    invalid: StateFlow<Boolean>,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onDismissInvalid: () -> Unit
) {
    val request by pending.collectAsStateWithLifecycle()
    val isInvalid by invalid.collectAsStateWithLifecycle()

    request?.let { AddRegionConfirmDialog(it, onConfirm, onDecline) }
    if (isInvalid) AddRegionInvalidDialog(onDismissInvalid)
}

@Composable
private fun AddRegionConfirmDialog(
    request: CustomRegionRequest,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.add_region_title, request.name)) },
        text = {
            Column {
                Text(stringResource(R.string.add_region_message, stringResource(R.string.app_name)))
                ServerRow(stringResource(R.string.add_region_server_oba), request.obaBaseUrl)
                request.otpBaseUrl?.let {
                    ServerRow(stringResource(R.string.add_region_server_otp), it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.add_region_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * One "what it is → where it points" row. The URL gets the emphasis and is allowed two lines before
 * ellipsizing, so a long path can't push the host out of view — the host is the part that matters.
 */
@Composable
private fun ServerRow(label: String, url: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Mirrors iOS's error alert: the link named a server address that isn't a usable URL. */
@Composable
private fun AddRegionInvalidDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_region_invalid_title)) },
        text = { Text(stringResource(R.string.add_region_invalid_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}
