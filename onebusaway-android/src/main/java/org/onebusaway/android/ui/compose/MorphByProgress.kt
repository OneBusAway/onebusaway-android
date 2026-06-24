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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest

/**
 * Crossfades and size-morphs between two layouts in lockstep with a 0..1 [progress] provider
 * ([start] at 0, [end] at 1), tracking the value continuously instead of playing a fixed tween — so a
 * gesture that drives [progress] half-way leaves the content half-morphed.
 *
 * A [SeekableTransitionState] turns [AnimatedContent]'s crossfade + bounding-box [SizeTransform] into a
 * seekable morph that [progress] drives directly, with linear easing so it follows the source evenly.
 * The transition's `currentState` stays at [start], so progress 0 always renders exactly [start] (and
 * its intrinsic size) — important when that resting layout's measured size matters (e.g. it sets a
 * sheet's peek height).
 *
 * @param durationMs reference timeline the seek is mapped onto. [progress] drives the playhead, so the
 *   absolute value only matters for any tail animation if [progress] is released between 0 and 1;
 *   keeping all child specs equal and linear keeps the crossfade and size in sync across the fraction.
 */
@Composable
fun MorphByProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    durationMs: Int = 280,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    val morph = remember { SeekableTransitionState(false) }
    LaunchedEffect(morph) {
        snapshotFlow { progress().coerceIn(0f, 1f) }
            .collectLatest { fraction -> morph.seekTo(fraction, targetState = true) }
    }
    val transition = rememberTransition(morph, label = "morphByProgress")
    transition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(durationMs, easing = LinearEasing)) togetherWith
                fadeOut(tween(durationMs, easing = LinearEasing)))
                .using(SizeTransform(clip = false) { _, _ -> tween(durationMs, easing = LinearEasing) })
        },
        contentKey = { it },
    ) { atEnd ->
        if (atEnd) end() else start()
    }
}
