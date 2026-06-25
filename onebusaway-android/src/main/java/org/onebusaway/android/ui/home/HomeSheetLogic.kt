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

/**
 * Pure decision logic for the arrivals bottom sheet, lifted out of [HomeScreen]'s `LaunchedEffect`
 * /`BackHandler` so the parity-sensitive behavior (the part that can't be exercised in a JVM test
 * from inside a `@Composable`) is unit-testable. [HomeScreen] does the Compose plumbing — keying the
 * effect, reading the live `SheetState`, animating — and defers every *decision* to these functions.
 *
 * The model: **visibility is business state** ([shouldShowSheet]) reconciled by [sheetReconcile];
 * **expansion is ephemeral UI** toggled by [toggleSheetTarget] and unwound by [sheetBackAction].
 */

/** The arrivals sheet's resting position, reported from the screen back to the activity and the state
 *  the reconcile/toggle/back decisions below operate on. */
enum class ArrivalsSheetState { Hidden, Collapsed, Expanded }

/** The sheet is shown (peeking or full) iff a stop is focused. (HOME is always the map now — the
 *  list screens are their own destinations — so a focused stop is the only condition.) */
internal fun shouldShowSheet(focusedStop: FocusedStop?): Boolean = focusedStop != null

/** What [HomeScreen] does to the sheet when the focused stop / tab changes (not on a user drag). */
enum class SheetReconcile { HIDE, PEEK_OPEN, LEAVE }

/**
 * Reconcile the sheet toward its desired visibility. When it should show, only a *hidden* sheet is
 * peeked open — a peek/full sheet keeps the user's current position (mirrors the legacy
 * `showSlidingPanel()`, which only acted when the panel was hidden). When it shouldn't show, hide it.
 */
internal fun sheetReconcile(shouldShow: Boolean, current: ArrivalsSheetState): SheetReconcile = when {
    !shouldShow -> SheetReconcile.HIDE
    current == ArrivalsSheetState.Hidden -> SheetReconcile.PEEK_OPEN
    else -> SheetReconcile.LEAVE
}

/** The chevron toggle target: a full sheet collapses to peek; anything else expands to full. */
internal fun toggleSheetTarget(current: ArrivalsSheetState): ArrivalsSheetState =
    if (current == ArrivalsSheetState.Expanded) ArrivalsSheetState.Collapsed else ArrivalsSheetState.Expanded

/** What a back-press does, given the sheet's resting state (mirrors the legacy back handling). */
enum class SheetBackAction { COLLAPSE, CLEAR_FOCUS, NONE }

internal fun sheetBackAction(current: ArrivalsSheetState): SheetBackAction = when (current) {
    ArrivalsSheetState.Expanded -> SheetBackAction.COLLAPSE      // full -> peek
    ArrivalsSheetState.Collapsed -> SheetBackAction.CLEAR_FOCUS  // peek -> clear focus (then hides)
    ArrivalsSheetState.Hidden -> SheetBackAction.NONE            // let the system handle back
}

/** The collapsed-peek size tier for the previewed arrival count (maps to the header-height dimens). */
enum class ArrivalsPeekTier { NONE, ONE, TWO_OR_MORE }

internal fun arrivalsPeekTier(arrivalCount: Int): ArrivalsPeekTier = when {
    arrivalCount >= 2 -> ArrivalsPeekTier.TWO_OR_MORE
    arrivalCount == 1 -> ArrivalsPeekTier.ONE
    else -> ArrivalsPeekTier.NONE
}
