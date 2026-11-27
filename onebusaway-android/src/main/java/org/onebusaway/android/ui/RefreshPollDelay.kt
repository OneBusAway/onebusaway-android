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
package org.onebusaway.android.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How often a screen that polls on a fixed period refreshes (the arrivals board, trip details). */
private val REFRESH_PERIOD = 60.seconds

/**
 * The wait before a periodically refreshing screen polls again, [sinceLastResponse] after its last
 * response: what's left of the 60s period, or of [earlyRetryAfter] when that refresh met a rate limit and
 * the screen earned an early retry (see `ArrivalsViewModel.earlyRetryAfter` and
 * `TripDetailsViewModel.earlyRetryAfter`). Measured from the response either way, so a pause and
 * resume keeps the same schedule. One rule for every such screen, so their schedules can't drift.
 */
internal fun nextRefreshPollDelay(sinceLastResponse: Duration, earlyRetryAfter: Duration?): Duration = ((earlyRetryAfter ?: REFRESH_PERIOD) - sinceLastResponse).coerceIn(Duration.ZERO, REFRESH_PERIOD)
