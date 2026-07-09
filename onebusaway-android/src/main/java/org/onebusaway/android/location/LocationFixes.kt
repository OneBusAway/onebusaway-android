/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.location

import android.location.Location
import org.onebusaway.android.time.WallTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Fix-selection policy: which of two device location fixes to keep. All functions are pure — the
 * "now" a decision is judged against is passed in (project rule: helpers don't read the clock).
 */
object LocationFixes {

    /** Accuracy (meters) a new fix must beat to displace a recent one. */
    const val ACC_THRESHOLD_METERS = 50f

    /** Age beyond which any newer fix wins regardless of accuracy. */
    val TIME_THRESHOLD: Duration = 10.minutes

    /**
     * Compares Location A to Location B — prefers a non-null location that is more recent. Does
     * NOT take estimated accuracy into account.
     *
     * @return true if [a] is "better" than [b], false if [b] is "better" than [a]
     */
    @JvmStatic
    fun compareLocationsByTime(a: Location?, b: Location?): Boolean =
        a != null && (b == null || WallTime(a.time) > WallTime(b.time))

    /**
     * Compares a new location [a] to a previously saved location [b], considering timestamps and
     * accuracy. Typically [a] is a fix just delivered by a LocationListener and [b] is what we
     * already hold.
     *
     * Kotlin-only (the [now] value class is not callable from Java); callers pass the device wall
     * clock so the age check has no embedded clock read.
     *
     * @return true if [a] is "better" than [b], false otherwise
     */
    fun compareLocations(a: Location?, b: Location?, now: WallTime): Boolean {
        // New location isn't valid
        if (a == null) {
            return false
        }
        // First location we've seen — save it
        if (b == null) {
            return true
        }
        // getAccuracy() is 0f when the fix has no accuracy; the legacy behavior treats that as
        // "better than the threshold", so a no-accuracy newer fix still wins. Preserved deliberately.
        return isBetterFix(WallTime(a.time), a.accuracy, WallTime(b.time), now)
    }

    /**
     * Pure branchy core of [compareLocations], free of [Location] so it is JVM-unit-testable.
     *
     * @param candidateTime timestamp of the new fix
     * @param candidateAccuracy accuracy (meters) of the new fix (0f when the fix has none)
     * @param currentTime timestamp of the saved fix
     * @param now the device wall clock the decision is judged against
     */
    internal fun isBetterFix(
        candidateTime: WallTime,
        candidateAccuracy: Float,
        currentTime: WallTime,
        now: WallTime,
    ): Boolean {
        val newer = candidateTime > currentTime
        // Take the new fix only when it is more recent, and either the saved fix is old enough
        // (> TIME_THRESHOLD) that any newer fix wins, or the new fix beats the accuracy threshold.
        return newer &&
            (now - currentTime > TIME_THRESHOLD || candidateAccuracy < ACC_THRESHOLD_METERS)
    }

    /**
     * Check if two locations are the exact same by comparing their timestamp, lat & lng.
     *
     * @return true if same, false otherwise
     */
    @JvmStatic
    fun isDuplicate(a: Location, b: Location): Boolean =
        WallTime(a.time) == WallTime(b.time) &&
            a.latitude == b.latitude &&
            a.longitude == b.longitude
}
