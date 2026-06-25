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

/**
 * Smooths a marker across a fresh-AVL jump without interrupting its dead-reckoning glide.
 *
 * The renderer dead-reckons each marker every dynamic tick (the caller passes the freshly
 * extrapolated [GeoPoint] as `target`). Snapping the marker onto a new fix would jump; the legacy
 * approach instead played a fixed ease *to* the new position, which stopped the glide and lurched
 * back into it at both ends. This keeps dead-reckoning as the single position authority and smears
 * only the fix's **correction** — the discontinuity the fix introduced — over [durationMs] as a
 * decaying offset on top of the live target. The displayed point is `target + error * w`, where the
 * decay weight `w = 1 - smoothstep(frac)` runs 1→0 with zero slope at both ends, so there is no
 * position jump at the fix and no velocity jump at the handoff. Only the shrinking error is a
 * straight line, so the marker still follows the route shape during the correction.
 *
 * Flavor-neutral (operates on [GeoPoint]; carries no Google/maplibre marker type) and pure given
 * `nowMs`, so both renderers share it and it is JVM-testable. Single-threaded: the caller invokes
 * [displayPosition] from its render tick, which is the only mutator. Drop departed keys via
 * [retainOnly].
 */
class CorrectionSmoother(private val durationMs: Long = DEFAULT_DURATION_MS) {

    private class State {
        var prevFix: Long = 0L
        var shownLat = 0.0
        var shownLng = 0.0
        var hasShown = false
        var errLat = 0.0
        var errLng = 0.0
        var startMs = 0L
        var correcting = false
    }

    private val states = HashMap<String, State>()

    /**
     * Record [key]'s initial shown position without computing a correction — call once when its marker
     * is created so the first fix's correction is measured from where it was placed. Equivalent to a
     * first-sighting [displayPosition] but names the intent and needs no clock.
     */
    fun prime(key: String, point: GeoPoint, fixTimeMs: Long) {
        states[key] = State().apply {
            prevFix = fixTimeMs
            shownLat = point.latitude
            shownLng = point.longitude
            hasShown = true
        }
    }

    /**
     * The position to display for [key] this tick: the dead-reckoned [target] plus a decaying
     * correction across a fresh [fixTimeMs]. Records the returned point so the next fix's correction
     * is measured from where the marker actually shows. Returns [target] unchanged on the first
     * sighting, between fixes once settled, or when the fix's jump is implausibly large (a snap).
     */
    fun displayPosition(key: String, target: GeoPoint, fixTimeMs: Long, nowMs: Long): GeoPoint {
        val st = states.getOrPut(key) { State() }

        // A fresh fix: capture the discontinuity it introduced as a decaying error, unless it's an
        // implausible teleport (then drop any correction so the base just jumps).
        if (st.hasShown && fixTimeMs != st.prevFix) {
            val shown = GeoPoint(st.shownLat, st.shownLng)
            if (haversineMeters(shown, target) < MAX_CORRECTION_METERS) {
                st.errLat = st.shownLat - target.latitude
                st.errLng = st.shownLng - target.longitude
                st.startMs = nowMs
                st.correcting = true
            } else {
                st.correcting = false
            }
        }
        st.prevFix = fixTimeMs

        var resLat = target.latitude
        var resLng = target.longitude
        if (st.correcting) {
            val frac = ((nowMs - st.startMs).toDouble() / durationMs).coerceIn(0.0, 1.0)
            if (frac >= 1.0) {
                st.correcting = false
            } else {
                // Decay weight 1 - smoothstep(frac): 1→0 with zero slope at both ends, so no position
                // jump at the fix and no velocity jump at the handoff back to dead-reckoning. frac is
                // already in [0, 1].
                val w = 1.0 - frac * frac * (3.0 - 2.0 * frac)
                resLat += st.errLat * w
                resLng += st.errLng * w
            }
        }

        st.shownLat = resLat
        st.shownLng = resLng
        st.hasShown = true
        return GeoPoint(resLat, resLng)
    }

    /**
     * Whether [key] is still absorbing a fix correction. Lets a caller that only touches its marker
     * on change (e.g. the most-recent-data dot) keep driving the decay each tick until it settles.
     */
    fun isSettling(key: String): Boolean = states[key]?.correcting == true

    /** Drop state for every key not in [liveKeys] (call when the live marker set changes). */
    fun retainOnly(liveKeys: Set<String>) {
        states.keys.retainAll(liveKeys)
    }

    private companion object {
        /** Correction duration across a fresh-AVL jump (the legacy 600ms). */
        const val DEFAULT_DURATION_MS = 600L

        /**
         * Above this jump (meters) a fresh fix snaps instead of smoothing — an absurd teleport
         * shouldn't sweep across the map. The legacy VehicleOverlay used 400 m for poll-to-poll
         * motion; we allow more so the marker also glides as it settles from its raw last-known
         * fallback onto its first dead-reckoned position (that correction routinely exceeds 400 m on
         * a stale or off-route fix).
         */
        const val MAX_CORRECTION_METERS = 2000.0
    }
}
