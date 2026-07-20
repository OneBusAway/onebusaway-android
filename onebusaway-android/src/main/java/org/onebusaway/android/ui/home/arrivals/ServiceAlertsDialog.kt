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
package org.onebusaway.android.ui.home.arrivals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.AlertItem
import org.onebusaway.android.ui.arrivals.ArrivalsUiState
import org.onebusaway.android.ui.arrivals.ServiceAlertsContent

/** Centered modal presentation of the focused stop's complete service-alert workflow. */
@Composable
internal fun ServiceAlertsDialog(
    content: ArrivalsUiState.Content,
    onShowAlert: (String) -> Unit,
    onHideAlert: (AlertItem) -> Unit,
    onShowHiddenAlerts: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 560.dp)
                .testTag("focus_alert_panel")
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.stop_info_service_alerts),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_navigation_close),
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                }
                HorizontalDivider()
                ServiceAlertsContent(
                    alerts = content.alerts,
                    hiddenAlertCount = content.hiddenAlertCount,
                    onShowAlert = onShowAlert,
                    onHideAlert = onHideAlert,
                    onShowHiddenAlerts = onShowHiddenAlerts,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
                )
            }
        }
    }
}
