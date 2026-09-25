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
package org.onebusaway.android.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * HOME's one `BackHandler`: enabled iff [action] is not [HomeBackAction.NONE], and dispatching on
 * that same value, so whether HOME claims the press and what it does cannot drift apart. [action] is
 * [homeBackAction]'s answer for the current composition; the callbacks are the rungs' effects, owned
 * by the screen. A composable of its own so an instrumented test drives the real dispatch through the
 * real dispatcher rather than a copy of this `when`.
 */
@Composable
internal fun HomeBackHandler(
    action: HomeBackAction,
    onReturnToSource: () -> Unit,
    onCancelEndpointPick: () -> Unit,
    onNavigateBackInDirections: () -> Unit,
    onCollapseSheet: () -> Unit,
    onUndoMapAction: () -> Unit
) {
    BackHandler(enabled = action != HomeBackAction.NONE) {
        when (action) {
            HomeBackAction.RETURN_TO_SOURCE -> onReturnToSource()
            HomeBackAction.CANCEL_ENDPOINT_PICK -> onCancelEndpointPick()
            HomeBackAction.NAVIGATE_BACK_IN_DIRECTIONS -> onNavigateBackInDirections()
            HomeBackAction.COLLAPSE_SHEET -> onCollapseSheet()
            HomeBackAction.UNDO_MAP_ACTION -> onUndoMapAction()
            HomeBackAction.NONE -> Unit
        }
    }
}
