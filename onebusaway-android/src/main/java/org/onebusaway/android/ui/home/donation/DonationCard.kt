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
package org.onebusaway.android.ui.home.donation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.onebusaway.android.R

/**
 * Self-wiring donation feature module: collects [DonationViewModel] state, builds its callbacks, runs
 * its effects (open the learn-more / donations page — STARTED-gated so it can't startActivity from the
 * background), re-gates availability on each resume, and renders [DonationOverlay]. The host just
 * places this with its ViewModel + whether the map's NEARBY tab is showing.
 */
@Composable
fun DonationFeature(
    viewModel: DonationViewModel,
    onNearby: Boolean,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // The effects collector outlives a recomposition, so read onLearnMore through the latest snapshot
    // rather than capturing the lambda from first composition.
    val currentOnLearnMore by rememberUpdatedState(onLearnMore)
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DonationEffect.OpenLearnMore -> currentOnLearnMore()
                    DonationEffect.OpenDonatePage ->
                        context.startActivity(viewModel.buildDonationsPageIntent())
                }
            }
        }
    }
    val callbacks = remember(viewModel) {
        DonationCallbacks(
            onClose = viewModel::requestDismiss,
            onLearnMore = viewModel::learnMore,
            onDonate = viewModel::donate,
            onDismissForever = viewModel::dismissForever,
            onRemindLater = viewModel::remindLater,
            onCancelDismiss = viewModel::cancelDismiss,
        )
    }
    DonationOverlay(
        cardVisible = onNearby && state.available,
        dismissDialogVisible = state.showDismissDialog,
        callbacks = callbacks,
        modifier = modifier,
    )
}

/** The donation card's callbacks, reported back to [DonationViewModel] (mirrors SurveyCallbacks). */
class DonationCallbacks(
    val onClose: () -> Unit,
    val onLearnMore: () -> Unit,
    val onDonate: () -> Unit,
    val onDismissForever: () -> Unit,
    val onRemindLater: () -> Unit,
    val onCancelDismiss: () -> Unit,
)

/**
 * The donation feature's overlay: the prompt card (shown when [cardVisible]) plus its dismiss
 * confirmation dialog (shown when [dismissDialogVisible]). Self-contained — driven by
 * [DonationViewModel] state, actions reported through [callbacks] — so the donation concern no longer
 * lives in HomeUiState / HomeViewModel / HomeActivity. The NEARBY-tab gate is folded into [cardVisible]
 * by the caller (HomeScreen), like the other map chrome.
 */
@Composable
fun DonationOverlay(
    cardVisible: Boolean,
    dismissDialogVisible: Boolean,
    callbacks: DonationCallbacks,
    modifier: Modifier = Modifier,
) {
    if (cardVisible) {
        DonationCard(
            onClose = callbacks.onClose,
            onLearnMore = callbacks.onLearnMore,
            onDonate = callbacks.onDonate,
            modifier = modifier,
        )
    }
    if (dismissDialogVisible) {
        DonationDismissDialog(
            onDismissForever = callbacks.onDismissForever,
            onRemindLater = callbacks.onRemindLater,
            onCancel = callbacks.onCancelDismiss,
        )
    }
}

/**
 * The donation prompt overlaid near the top of the map, replacing the XML donation_view include.
 * The title carries the app name for white-label brands; the close / learn-more / donate actions are
 * dispatched to [DonationViewModel].
 */
@Composable
fun DonationCard(
    onClose: () -> Unit,
    onLearnMore: () -> Unit,
    onDonate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = colorResource(R.color.theme_primary)
    Card(shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(R.string.donation_view_title, stringResource(R.string.app_name)),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(top = 4.dp, start = 4.dp, end = 4.dp)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.close_donation_card),
                        tint = colorResource(R.color.body_text_1)
                    )
                }
            }
            Text(
                text = stringResource(R.string.donation_view_body),
                modifier = Modifier.padding(4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onLearnMore, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.donation_view_learn_more_button), color = primary)
                }
                Button(
                    onClick = onDonate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.donation_view_donate_now_button))
                }
            }
        }
    }
}

/**
 * Confirmation when the user closes the donation card. Three stacked actions (the Compose idiom for
 * >2 dialog buttons): keep asking later, stop asking, or cancel.
 */
@Composable
private fun DonationDismissDialog(
    onDismissForever: () -> Unit,
    onRemindLater: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.donation_dismiss_dialog_title)) },
        text = {
            Text(stringResource(R.string.donation_dismiss_dialog_body, stringResource(R.string.app_name)))
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRemindLater) {
                    Text(stringResource(R.string.donation_dismiss_dialog_remind_me_later_button))
                }
                TextButton(onClick = onDismissForever) {
                    Text(stringResource(R.string.donation_dismiss_dialog_dont_want_to_help_button))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.donation_dismiss_dialog_cancel_button))
                }
            }
        }
    )
}
