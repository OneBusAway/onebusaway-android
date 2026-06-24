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
package org.onebusaway.android.ui.search

/**
 * UI state for an incremental search screen.
 *
 * [Error] means the server could not be reached at all; a server that responds with an error
 * code is presented as [Results] with no items, matching the legacy search screens ("fake no
 * results" rather than a scary communication error).
 */
sealed interface SearchUiState<out T> {

    /** Empty query — show the screen's hint text. */
    data object Idle : SearchUiState<Nothing>

    data object Searching : SearchUiState<Nothing>

    data class Results<T>(val items: List<T>) : SearchUiState<T>

    data object Error : SearchUiState<Nothing>
}
