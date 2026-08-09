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
package org.onebusaway.android.map.render

import android.content.res.Resources
import java.util.concurrent.TimeUnit
import org.onebusaway.android.R

/**
 * Formats [elapsedSeconds] as "Data updated N min M sec ago" (or "… M sec ago" under a minute) — the
 * snippet on the selected vehicle's most-recent-data dot, in both map flavors.
 *
 * Lives beside the renderers rather than in `map/compose` because that is now its only home: it was
 * shared with the vehicle info window until the marker itself took over what that window said (#2194).
 *
 * The caller measures the age; this only words it. That keeps the server-vs-device clock choice at the
 * call site, where the paired timestamps are (#1612) — the dot's age is a server-clock interval.
 */
internal fun formatDataAge(res: Resources, elapsedSeconds: Long): String {
    val s = elapsedSeconds.coerceAtLeast(0)
    return if (s < 60) {
        res.getQuantityString(R.plurals.vehicle_last_updated_sec, s.toInt(), s)
    } else {
        res.getQuantityString(
            R.plurals.vehicle_last_updated_min_and_sec,
            (s % 60).toInt(),
            TimeUnit.SECONDS.toMinutes(s),
            s % 60
        )
    }
}
