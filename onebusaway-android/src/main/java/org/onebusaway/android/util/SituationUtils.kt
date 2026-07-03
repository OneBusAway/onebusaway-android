/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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

package org.onebusaway.android.util

import org.onebusaway.android.models.ObaSituation
import java.util.concurrent.TimeUnit

/**
 * Utility methods related to situations (service alerts). Aggregation of a response's situations
 * lives in `io.client.StopArrivals.situations`; this is the model-level active-window check.
 */
object SituationUtils {

    /**
     * Returns true if [currentTime] (epoch millis) falls within one of the [situation]'s active
     * windows, or if the situation has no active windows (assumed always-active).
     *
     * [currentTime] must be the response's **server** `currentTime` whenever the active windows come
     * from a server response, so the comparison cancels device clock skew (#1612). This function is
     * a pure function of its inputs — it reads no clock of its own.
     */
    @JvmStatic
    fun isActiveWindowForSituation(situation: ObaSituation, currentTime: Long): Boolean {
        if (situation.activeWindows.isEmpty()) {
            // We assume a situation is active if it doesn't contain any active window information.
            return true
        }
        // Active window times arrive in either seconds or milliseconds; normalize both ends and "now"
        // to millis by magnitude so the comparison never depends on the units of "now".
        val nowMs = toEpochMillis(currentTime)
        return situation.activeWindows.any { window ->
            val fromMs = toEpochMillis(window.from)
            // The window must have started (guards future-dated windows), and either be open-ended
            // (to == 0 means no end — see #990) or not yet ended.
            fromMs <= nowMs && (window.to == 0L || nowMs <= toEpochMillis(window.to))
        }
    }

    /**
     * HEURISTIC (see CLAUDE.md "No unsanctioned heuristics" — needs human sign-off): normalizes an
     * epoch timestamp to milliseconds by **magnitude**, because the upstream feed is inconsistent
     * about whether active-window `from`/`to` are seconds or milliseconds and carries no unit field.
     *
     * Assumption: values below [SECONDS_MILLIS_THRESHOLD] are seconds — that many *seconds* only
     * reaches the year ~5138, whereas any modern *millis* timestamp is far larger — so they're scaled
     * up; anything else is already millis. Non-positive values pass through.
     *
     * Failure mode: a real seconds timestamp at/after year ~5138, or a millis timestamp before 1973,
     * is misclassified. Both are far outside any plausible transit alert window, so the guess is safe
     * in practice — but it is still a guess, and the right long-term fix is to normalize the unit at
     * the wire-parsing boundary (where the server/version may be known) and delete this.
     */
    private fun toEpochMillis(timestamp: Long): Long =
        if (timestamp in 1 until SECONDS_MILLIS_THRESHOLD) TimeUnit.SECONDS.toMillis(timestamp) else timestamp

    /** ~Year 5138 in epoch seconds / year 1973 in epoch millis — cleanly separates the two units. */
    private const val SECONDS_MILLIS_THRESHOLD = 100_000_000_000L
}
