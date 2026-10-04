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
package org.onebusaway.android.ui.nightlight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule

/**
 * The night light must not flash until the user has accepted the epilepsy intro (#2358): the intro
 * dialog leaves the activity RESUMED, so [rememberFlashColor]'s `enabled` gate is the only thing
 * holding the strobe back while the warning is on screen.
 */
@RunWith(AndroidJUnit4::class)
class FlashColorTest {

    @get:Rule
    val compose = createUnconfinedComposeRule()

    private val flashColors = listOf(Color.White, Color.Green, Color.White)

    /**
     * Hosts [rememberFlashColor] under a lifecycle pinned at RESUMED, so the flash loop's
     * RESUMED-only gate is open regardless of the device's screen state (a dozing, locked test
     * device leaves the host activity paused, which would hold the screen dark for the wrong reason).
     */
    private fun setFlashContent(enabled: () -> Boolean): () -> State<Color> {
        lateinit var color: State<Color>
        compose.setContent {
            val owner = remember { ResumedLifecycleOwner() }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                color = rememberFlashColor(enabled = enabled(), flashColors = flashColors)
            }
        }
        return { color }
    }

    /** Steps the clock [ms] forward a few ms at a time, returning every color the state held. */
    private fun colorsOver(ms: Long, color: () -> State<Color>): Set<Color> {
        val seen = mutableSetOf(color().value)
        repeat((ms / STEP_MS).toInt()) {
            compose.mainClock.advanceTimeBy(STEP_MS)
            seen += color().value
        }
        return seen
    }

    @Test
    fun staysDarkWhileNotEnabled() {
        compose.mainClock.autoAdvance = false
        val color = setFlashContent { false }

        assertEquals(setOf(COLOR_DARK), colorsOver(2_000, color))
    }

    @Test
    fun flashesOnceEnabled() {
        compose.mainClock.autoAdvance = false
        var enabled by mutableStateOf(false)
        val color = setFlashContent { enabled }
        assertEquals(setOf(COLOR_DARK), colorsOver(1_000, color))

        compose.runOnIdle { enabled = true }
        val seen = colorsOver(1_000, color)
        assertTrue("expected a flash color once enabled, saw $seen", seen.containsAll(flashColors))

        // Disabling (the tap-to-pause) settles back on the dark scrim.
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(STEP_MS)
        assertEquals(setOf(COLOR_DARK), colorsOver(1_000, color))
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        // Well under the 75 ms on-time, so every flash is sampled.
        const val STEP_MS = 10L
    }
}
