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
package org.onebusaway.android.ui.report.problem

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the editable [ProblemFormState] and drives a single submit through
 * [ProblemReportRepository]. The host supplies the user's [Location] at submit time
 * (kept out of the ViewModel so it stays free of platform location plumbing).
 */
class ProblemReportViewModel(
    private val params: ProblemParams,
    codes: List<ProblemCode>,
    headsign: String?,
    private val repository: ProblemReportRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(
        ProblemFormState(
            kind = if (params is ProblemParams.Trip) ProblemKind.TRIP else ProblemKind.STOP,
            codes = codes,
            headsign = headsign
        )
    )
    val formState: StateFlow<ProblemFormState> = _formState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    fun onCodeSelected(index: Int) = _formState.update { it.copy(selectedCodeIndex = index) }

    fun onCommentChange(comment: String) = _formState.update { it.copy(comment = comment) }

    fun onVehicleToggle(onVehicle: Boolean) = _formState.update { it.copy(onVehicle = onVehicle) }

    fun onVehicleNumberChange(number: String) = _formState.update { it.copy(vehicleNumber = number) }

    /** Submits the report. No-op while one is already in flight or no category is chosen. */
    fun submit(location: Location?) {
        val form = _formState.value
        val code = form.selectedCode.code ?: return
        if (_submitState.value == SubmitState.Submitting) return
        _submitState.value = SubmitState.Submitting

        viewModelScope.launch {
            val result = when (val p = params) {
                is ProblemParams.Stop ->
                    repository.submitStop(p.stopId, code, form.comment, location)

                is ProblemParams.Trip ->
                    repository.submitTrip(
                        p, code, form.comment, form.onVehicle, form.vehicleNumber, location
                    )
            }
            _submitState.value = result.fold(
                onSuccess = { SubmitState.Sent },
                onFailure = { SubmitState.Error }
            )
        }
    }

    /** Clears a terminal submit result once the host has shown it. */
    fun onSubmitResultHandled() {
        _submitState.value = SubmitState.Idle
    }
}
