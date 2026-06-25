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

/**
 * JVM-pure models for the Open311 dynamic issue form. The Open311 client library types
 * (edu.usf.cutr.*) are quarantined in [Open311Repository]; nothing here imports them, so the
 * mapping and the ViewModel are unit-testable on a plain JVM.
 */

/** How a single dynamic attribute is rendered and entered. */
enum class Open311FieldType { TEXT, NUMBER, DATETIME, SINGLE_CHOICE, MULTI_CHOICE }

/** One choice within a single/multi-value list. [key] is submitted; [name] is shown. */
data class Open311Option(val key: String, val name: String)

/**
 * A single dynamic Open311 question.
 *
 * @param code the attribute code, used as the submit key and the [Open311FormState.values] key
 * @param rawDataType the original library datatype string, preserved for the submitted attribute pair
 * @param isStopIdField true when this transit field is pre-filled with the stop code
 */
data class Open311Field(
    val code: Int,
    val label: String,
    val type: Open311FieldType,
    val required: Boolean,
    val rawDataType: String,
    val options: List<Open311Option> = emptyList(),
    val isStopIdField: Boolean = false
)

/** The user's current answer for a field, shaped by the field's type. */
sealed interface FieldValue {
    data class Text(val value: String) : FieldValue
    data class SingleChoice(val selectedKey: String?) : FieldValue
    data class MultiChoice(val selectedKeys: Set<String>) : FieldValue
}

/** Reporter contact details, pre-filled from prefs and editable unless the report is anonymous. */
data class ContactInfo(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = ""
)

/** Complete state of the Open311 form: read-only description, dynamic fields, and contact. */
data class Open311FormState(
    val headsign: String? = null,
    val descriptionLines: List<String> = emptyList(),
    val fields: List<Open311Field> = emptyList(),
    val values: Map<Int, FieldValue> = emptyMap(),
    val mainDescription: String = "",
    val imagePath: String? = null,
    val anonymous: Boolean = false,
    val contact: ContactInfo = ContactInfo()
) {
    fun textValue(code: Int): String = (values[code] as? FieldValue.Text)?.value.orEmpty()

    fun singleChoice(code: Int): String? = (values[code] as? FieldValue.SingleChoice)?.selectedKey

    fun multiChoice(code: Int): Set<String> =
        (values[code] as? FieldValue.MultiChoice)?.selectedKeys ?: emptySet()
}

/** Distinguishes load progress from the editable form. */
enum class Open311LoadState { LOADING, LOADED, ERROR }

/** Terminal results of a submit attempt; non-Sent results carry a user-facing message. */
sealed interface Open311SubmitState {
    data object Idle : Open311SubmitState
    data object Submitting : Open311SubmitState
    data object Sent : Open311SubmitState
    data class ValidationError(val message: String) : Open311SubmitState
    data class ServerError(val message: String) : Open311SubmitState
}
