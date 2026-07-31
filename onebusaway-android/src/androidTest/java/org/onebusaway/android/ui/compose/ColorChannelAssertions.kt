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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue

/** The channel a rendered colour is expected to lead on. */
enum class Channel { RED, GREEN, BLUE }

/**
 * Asserts a sampled pixel reads as [channel]: dominance rather than an exact hex, so the assertion
 * survives a palette tweak and holds in both themes — but strictly, with no tolerance to tune, since
 * callers sample the centre of a filled shape and get the fill colour itself, not an antialiased edge.
 *
 * Shared because the directions endpoint dots are now drawn in two places — the trip-plan form's rail
 * and the map long-press menu (#2112) — and "green origin, red destination" is asserted of both.
 */
fun assertDominant(color: Color, channel: Channel, what: String) {
    val (dominant, others) = when (channel) {
        Channel.RED -> color.red to listOf(color.green, color.blue)
        Channel.GREEN -> color.green to listOf(color.red, color.blue)
        Channel.BLUE -> color.blue to listOf(color.red, color.green)
    }
    assertTrue(
        "$what should read $channel, but its dot was $color",
        others.all { dominant > it }
    )
}
