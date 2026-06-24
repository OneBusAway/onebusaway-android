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
package org.onebusaway.android.ui.report.open311

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Open311 dynamic issue form: loads the service description through [Open311Repository],
 * holds the immutable [Open311FormState], and runs a cancelable submit on [viewModelScope]
 * (replacing the legacy setRetainInstance + ServiceDescriptionTask/ServiceRequestTask lifecycle).
 */
class Open311ProblemViewModel(private val repository: Open311Repository) : ViewModel() {

    private val _loadState = MutableStateFlow(Open311LoadState.LOADING)
    val loadState: StateFlow<Open311LoadState> = _loadState.asStateFlow()

    private val _form = MutableStateFlow(Open311FormState())
    val form: StateFlow<Open311FormState> = _form.asStateFlow()

    private val _submitState = MutableStateFlow<Open311SubmitState>(Open311SubmitState.Idle)
    val submitState: StateFlow<Open311SubmitState> = _submitState.asStateFlow()

    private var submitJob: Job? = null

    init {
        load()
    }

    fun load() {
        _loadState.value = Open311LoadState.LOADING
        viewModelScope.launch {
            repository.loadForm().fold(
                onSuccess = {
                    _form.value = it
                    _loadState.value = Open311LoadState.LOADED
                },
                onFailure = { _loadState.value = Open311LoadState.ERROR }
            )
        }
    }

    fun setMainDescription(text: String) = _form.update { it.copy(mainDescription = text) }

    fun setFieldText(code: Int, text: String) = putValue(code, FieldValue.Text(text))

    fun setSingleChoice(code: Int, key: String?) = putValue(code, FieldValue.SingleChoice(key))

    fun setMultiChoice(code: Int, key: String, checked: Boolean) = _form.update { state ->
        val current = (state.values[code] as? FieldValue.MultiChoice)?.selectedKeys ?: emptySet()
        val updated = if (checked) current + key else current - key
        state.copy(values = state.values + (code to FieldValue.MultiChoice(updated)))
    }

    fun setAnonymous(anonymous: Boolean) = _form.update { it.copy(anonymous = anonymous) }

    fun setFirstName(value: String) = _form.update { it.copy(contact = it.contact.copy(firstName = value)) }

    fun setLastName(value: String) = _form.update { it.copy(contact = it.contact.copy(lastName = value)) }

    fun setEmail(value: String) = _form.update { it.copy(contact = it.contact.copy(email = value)) }

    fun setPhone(value: String) = _form.update { it.copy(contact = it.contact.copy(phone = value)) }

    fun setImagePath(path: String?) = _form.update { it.copy(imagePath = path) }

    fun submit() {
        if (_submitState.value == Open311SubmitState.Submitting) return
        _submitState.value = Open311SubmitState.Submitting
        submitJob = viewModelScope.launch {
            _submitState.value = repository.submit(_form.value)
        }
    }

    /** Cancels an in-flight submit (the legacy progress dialog's Cancel button). */
    fun cancelSubmit() {
        submitJob?.cancel()
        _submitState.value = Open311SubmitState.Idle
    }

    /** Clears a terminal submit result once the host has shown it. */
    fun onSubmitStateHandled() = _submitState.update {
        if (it == Open311SubmitState.Submitting) it else Open311SubmitState.Idle
    }

    private fun putValue(code: Int, value: FieldValue) =
        _form.update { it.copy(values = it.values + (code to value)) }
}
