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
package org.onebusaway.android.ui.compose

/**
 * UI state for a screen that loads a list once and renders Loading / Success / Error. Shared by
 * the fetch-once Compose screens (agencies, regions). Query-driven screens that also need
 * idle/searching states use their own state type instead.
 */
sealed interface ListUiState<out T> {

    data object Loading : ListUiState<Nothing>

    data class Success<T>(val items: List<T>) : ListUiState<T>

    data object Error : ListUiState<Nothing>
}
