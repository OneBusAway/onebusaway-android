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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

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
fun navigationBarBottomPadding(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * Reports a bottom-sheet list's fully-laid-out height in px to [onContentHeight], so the host can fit
 * the sheet's collapsed peek to short content. Emits nothing — it is an effect wearing a composable's
 * clothes, hoisted here because both arrivals sheets need the identical measurement.
 *
 * Real layout, not an estimate, and not a magnitude heuristic: the reading is the bottom edge of the
 * last laid-out item. Material3 measures sheet content at full container height regardless of the peek,
 * so when the whole list fits that edge *is* the content height. When it doesn't, the last visible item
 * is the one straddling the viewport's bottom, so the same expression yields roughly the viewport
 * extent — a floor rather than a measurement, which is all a caller clamping to a peek cap can use.
 *
 * Two things make the measurement trustworthy rather than merely cheap:
 *
 *  - **One collector, keyed only on [listState].** It is deliberately *not* restarted when the content
 *    changes. A restart does not wait for the new content's layout pass — the effect's coroutine can
 *    run before it — so a restarted collector's first reading still describes the outgoing list, and
 *    reporting that would publish the wrong height. Observing layout continuously instead means a
 *    content swap needs no special handling: the new list's height simply arrives when it is measured.
 *  - **It only measures from the top.** Item offsets are viewport-relative, so once a list is scrolled
 *    `last.offset + last.size` is the last item's bottom *on screen*, not the content height. Skipping
 *    those readings is what makes the value correct — and, since the offsets that move every frame are
 *    exactly the ones being skipped, it is also what keeps a scroll gesture from republishing (and
 *    re-invalidating the host) once a frame.
 *
 * A list with nothing laid out reports nothing, so a caller still showing a spinner keeps whatever it
 * last had rather than being handed a zero.
 */
@Composable
fun ReportListContentHeight(
    listState: LazyListState,
    onContentHeight: (heightPx: Int) -> Unit
) {
    // Read through the latest lambda so a caller's inline `{ px -> … }` doesn't restart the effect.
    val report = rememberUpdatedState(onContentHeight)
    LaunchedEffect(listState) {
        snapshotFlow {
            val atTop = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (atTop && last != null) last.offset + last.size else null
        }.filterNotNull().distinctUntilChanged().collect { report.value(it) }
    }
}
