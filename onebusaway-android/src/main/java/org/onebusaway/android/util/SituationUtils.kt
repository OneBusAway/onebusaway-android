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
        // Active-window from/to are epoch *seconds* per the OBA REST contract (see SituationWindow in
        // ObaApiModels; confirmed by every response fixture), while [currentTime] is epoch *millis* —
        // a known, fixed unit mismatch, so convert "now" to seconds deterministically (no unit guess).
        val nowSec = TimeUnit.MILLISECONDS.toSeconds(currentTime)
        return situation.activeWindows.any { window ->
            // The window must have started (guards future-dated windows), and either be open-ended
            // (to == 0 means no end — see #990) or not yet ended.
            window.from <= nowSec && (window.to == 0L || nowSec <= window.to)
        }
    }
}
