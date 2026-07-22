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

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The Compose test rule this project's on-device UI tests should use.
 *
 * It calls the non-deprecated v2 `createComposeRule`, but pins composition to an
 * `UnconfinedTestDispatcher` through the rule's `effectContext` parameter (which, per its contract,
 * uses any `TestDispatcher` found there for composition and the `MainTestClock`). This runs
 * composition coroutines eagerly, matching the semantics these tests were written and validated
 * against, and matching the old — now deprecated — v1 `createComposeRule`. The point of this helper
 * is to drop that deprecation suppression suite-wide (issue #1792) without changing test behavior.
 *
 * Note on the v2 default (`StandardTestDispatcher`, which *queues* composition coroutines rather than
 * running them eagerly): when #1792 was filed, some composition-coroutine chains (e.g. the ETA
 * strip's now-removed measure -> effect -> scroll settle) never advanced to completion under it. That
 * upstream gap is gone on compose-ui-test 1.11.4 (the rule's `waitUntil`/idle machinery now pumps the
 * dispatcher) and the tests pass under the plain default too — but we pin Unconfined because
 * `EtaStripRenderTest` is an `@SmokeTest` on the API 23 floor emulator, whose StandardTestDispatcher
 * timing is unvalidated.
 *
 * See https://github.com/OneBusAway/onebusaway-android/issues/1792 for the investigation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun createUnconfinedComposeRule(): ComposeContentTestRule = createComposeRule(UnconfinedTestDispatcher())
