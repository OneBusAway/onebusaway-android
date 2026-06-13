/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.map.googlemapsv2

import android.os.SystemClock
import android.view.Choreographer

/**
 * Throttled choreographer frame loop that fires [onTick] at most once per [intervalMs]. Callers may
 * call [stop] from within [onTick]; the loop re-checks before re-posting.
 */
internal fun interface FrameTick {
    fun onTick(nowMs: Long)
}

internal class ThrottledFrameLoop
@JvmOverloads
constructor(private val onTick: FrameTick, private val intervalMs: Long = 50L) {
    private val choreographer: Choreographer = Choreographer.getInstance()
    private var ticking = false
    private var lastFrameUptimeMs = 0L
    private val callback = Choreographer.FrameCallback { onFrame() }

    fun start() {
        if (!ticking) {
            ticking = true
            choreographer.postFrameCallback(callback)
        }
    }

    fun stop() {
        ticking = false
        choreographer.removeFrameCallback(callback)
    }

    private fun onFrame() {
        if (!ticking) return
        val uptime = SystemClock.uptimeMillis()
        if (uptime - lastFrameUptimeMs >= intervalMs) {
            lastFrameUptimeMs = uptime
            onTick.onTick(System.currentTimeMillis())
        }
        if (ticking) choreographer.postFrameCallback(callback)
    }
}
