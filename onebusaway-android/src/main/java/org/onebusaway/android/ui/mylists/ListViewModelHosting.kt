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
package org.onebusaway.android.ui.mylists

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.onebusaway.android.ui.search.SearchViewModel

/**
 * Hosts a [MyListViewModel] scoped to the current [androidx.navigation.NavBackStackEntry] (the nearest
 * `ViewModelStoreOwner`). Used by the `My*` NavHost destinations, where each destination owns its lists
 * for as long as it's on the back stack. The class erases its generic, so several instances of the same
 * runtime class in one store would collide under a keyless lookup (e.g. a recent-stops and a starred-stops
 * list are both `MyListViewModel<StopListItem>` at runtime) — the string [key] disambiguates them.
 */
@Composable
internal fun <T> rememberListVm(
    key: String,
    repo: () -> MyListRepository<T>
): MyListViewModel<T> {
    @Suppress("UNCHECKED_CAST")
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { MyListViewModel(repo()) } }
    ) as MyListViewModel<T>
}

/** The [SearchViewModel] equivalent of [rememberListVm] (entry-scoped; same keying rationale). */
@Composable
internal fun <T> rememberSearchVm(
    key: String,
    search: () -> (suspend (String) -> Result<List<T>>)
): SearchViewModel<T> {
    @Suppress("UNCHECKED_CAST")
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { SearchViewModel(search()) } }
    ) as SearchViewModel<T>
}
