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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.onebusaway.android.donations.DonationsManager

/** The donation card's state: whether to offer it, and whether the "are you sure?" dialog is up. */
data class DonationUiState(
    val available: Boolean = false,
    val showDismissDialog: Boolean = false
)

/** What the user chose in the dismiss dialog's "maybe later" — snooze the card, or stop asking. */
enum class DonationDismissChoice { REMIND_LATER, STOP_ASKING }

/** One-shot donation navigation the activity carries out (it owns startActivity + the intents). */
sealed interface DonationEffect {
    object OpenLearnMore : DonationEffect

    /** Open the donations page at [url]. The host builds/starts the intent (mirrors [SurveyEffect]). */
    data class OpenDonatePage(val url: String) : DonationEffect
}

/**
 * Owns the donation card as a self-contained feature module (mirrors [SurveyViewModel]): the
 * `DonationsManager`-backed availability + the dismiss-confirmation dialog state, plus the card's
 * actions. This pulls the donation concern out of HomeViewModel/HomeUiState (the `donationVisible`
 * gate + `DismissDonation` dialog) and out of HomeActivity (the five callbacks). The nav-tab gate
 * (NEARBY only) stays in HomeScreen, like the other chrome.
 */
@HiltViewModel
class DonationViewModel @Inject constructor(
    private val manager: DonationsManager
) : ViewModel() {

    private val _state = MutableStateFlow(DonationUiState())
    val state: StateFlow<DonationUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<DonationEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<DonationEffect> = _effects.asSharedFlow()

    /** Recompute whether the card should offer itself (the DonationsManager flag). Call on resume. */
    fun refresh() = _state.update { it.copy(available = manager.shouldShowDonationUI()) }

    /** Close (X): ask for confirmation before stopping. */
    fun requestDismiss() = _state.update { it.copy(showDismissDialog = true) }

    fun cancelDismiss() = _state.update { it.copy(showDismissDialog = false) }

    fun learnMore() {
        _effects.tryEmit(DonationEffect.OpenLearnMore)
    }

    /** Donate now: stop asking (the legacy order), report the tap, then open the donations page. */
    fun donate() {
        manager.dismissDonationRequests()
        manager.reportDonateButtonPress()
        _effects.tryEmit(DonationEffect.OpenDonatePage(manager.donateUrl()))
    }

    /** "Maybe later" — carry out [choice], then hide the dialog and re-gate the card. */
    fun maybeLater(choice: DonationDismissChoice) {
        when (choice) {
            DonationDismissChoice.REMIND_LATER -> manager.remindUserLater()
            DonationDismissChoice.STOP_ASKING -> manager.dismissDonationRequests()
        }
        _state.update { it.copy(showDismissDialog = false, available = manager.shouldShowDonationUI()) }
    }
}
