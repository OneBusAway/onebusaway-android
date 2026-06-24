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

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * A [ViewModelStoreOwner] backed by a fresh [ViewModelStore] per [key], cleared when the key changes
 * or the composition leaves — so ViewModels scoped to it (via `LocalViewModelStoreOwner`) are
 * properly destroyed (their `viewModelScope` cancelled) instead of living on in the host
 * activity's store. Use it to host a short-lived, identity-keyed ViewModel from Compose.
 */
@Composable
fun rememberClearedViewModelStoreOwner(key: Any?): ViewModelStoreOwner {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(key) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}

/**
 * Unwraps the [AppCompatActivity] from a (possibly themed) Compose `LocalContext` chain — the
 * canonical bridge for a composable that needs its hosting activity. (Predates `activity-compose`'s
 * `LocalActivity`, which returns a plain `Activity` rather than the `AppCompatActivity` callers cast to.)
 */
tailrec fun Context.findActivity(): AppCompatActivity = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No AppCompatActivity found in the context chain")
}

/**
 * The system navigation-bar bottom inset (height varies by handset). The home arrivals sheet grows
 * its collapsed peek by this so the pinned peek header clears the bottom chrome, and the arrivals
 * list matches it as content padding — both call it so the value stays in sync.
 */
@Composable
fun navigationBarBottomPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
