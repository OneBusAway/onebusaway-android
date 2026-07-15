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
package org.onebusaway.android.map.googlemapsv2

/**
 * Applies expensive main-thread work in bounded batches, one batch per scheduled frame. Submitting
 * replacement work or cancelling invalidates callbacks already queued for older work.
 */
internal class FrameBatcher<T>(
    private val batchSize: Int,
    private val scheduleNextFrame: (callback: () -> Unit) -> Unit,
) {
    private data class Work<T>(
        val items: List<T>,
        val apply: (T) -> Unit,
        var nextIndex: Int = 0,
    )

    private var work: Work<T>? = null

    init {
        require(batchSize > 0) { "batchSize must be positive" }
    }

    fun submit(items: List<T>, apply: (T) -> Unit) {
        if (items.isEmpty()) {
            work = null
            return
        }
        val replacement = Work(items, apply)
        work = replacement
        schedule(replacement)
    }

    /** Returns whether work was pending. Already-scheduled callbacks safely become no-ops. */
    fun cancel(): Boolean {
        val cancelled = work != null
        work = null
        return cancelled
    }

    private fun schedule(scheduled: Work<T>) {
        scheduleNextFrame {
            if (work === scheduled) drainFrame(scheduled)
        }
    }

    private fun drainFrame(scheduled: Work<T>) {
        val end = (scheduled.nextIndex + batchSize).coerceAtMost(scheduled.items.size)
        for (index in scheduled.nextIndex until end) {
            scheduled.apply(scheduled.items[index])
            if (work !== scheduled) return
        }
        scheduled.nextIndex = end
        if (end < scheduled.items.size) {
            schedule(scheduled)
        } else {
            work = null
        }
    }
}
