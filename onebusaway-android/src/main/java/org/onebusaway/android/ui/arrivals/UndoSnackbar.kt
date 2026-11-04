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
package org.onebusaway.android.ui.arrivals

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.onebusaway.android.ui.compose.components.RouteBadge
import org.onebusaway.android.ui.compose.components.RouteBadgeChip

/**
 * An undo snackbar's content: the whole [message], and optionally the route it names drawn as a
 * [RouteBadgeChip], so it reads as a route number rather than a word in the sentence (#2366). The
 * message is still what TalkBack reads, and what a host other than [UndoSnackbarHost] shows.
 */
class UndoSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val routeMention: RouteMention? = null
) : SnackbarVisuals {
    override val withDismissAction = false
    override val duration = SnackbarDuration.Short
}

/** A route named mid-sentence: the [route]'s badge, between [before] and [after]. */
class RouteMention(val before: String, val route: RouteBadge, val after: String) {
    companion object {
        /**
         * Splits a translated [template] at its `%1$s` route placeholder, or null when it has none — a
         * translation that doesn't name the route keeps its plain message.
         */
        fun of(template: String, route: RouteBadge): RouteMention? {
            val parts = template.split(ROUTE_PLACEHOLDER, limit = 2)
            return if (parts.size == 2) RouteMention(parts[0], route, parts[1]) else null
        }

        private const val ROUTE_PLACEHOLDER = "%1\$s"
    }
}

/**
 * The arrivals board's hide/undo snackbar (a hidden route or alert), one at a time with the latest
 * winning. [SnackbarHostState] queues every [SnackbarHostState.showSnackbar] behind the one showing, so
 * swiping away several routes in a row would replay a snackbar per hide long after the rider moved on.
 * Instead each [show] cancels the previous one — dismissing it if it's up, dropping it if it's still
 * queued — and its undo is forfeited, as if it had timed out. Unrelated snackbars on the same host keep
 * their place in the queue.
 */
internal class UndoSnackbar(private val hostState: SnackbarHostState, private val scope: CoroutineScope) {
    private var current: Job? = null

    fun show(visuals: UndoSnackbarVisuals, onAction: (() -> Unit)?) {
        current?.cancel()
        current = scope.launch {
            if (hostState.showSnackbar(visuals) == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

/** An [UndoSnackbar] on [hostState], living as long as the calling composition. */
@Composable
internal fun rememberUndoSnackbar(hostState: SnackbarHostState): UndoSnackbar {
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { UndoSnackbar(hostState, scope) }
}

/**
 * A [SnackbarHost] that draws an [UndoSnackbarVisuals.routeMention] with the route's badge in the
 * sentence, and every other snackbar as the stock one.
 */
@Composable
fun UndoSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        val mention = (data.visuals as? UndoSnackbarVisuals)?.routeMention
        if (mention == null) {
            Snackbar(data)
            return@SnackbarHost
        }
        // Built like the stock Snackbar(data) — same outer padding and action button — with the message
        // swapped for the sentence-with-badge.
        Snackbar(
            modifier = Modifier.padding(12.dp),
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(
                        onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor)
                    ) { Text(label) }
                }
            }
        ) {
            // Read as the one sentence it is, not as fragments around a badge. Every piece sits on one
            // baseline (the badge's label on the sentence's): Snackbar takes a message whose first and last
            // baselines differ for multi-line text and pins it near the top rather than centring it, and
            // the badge's own label would otherwise be a second baseline.
            Row(Modifier.clearAndSetSemantics { contentDescription = data.visuals.message }) {
                val before = mention.before.trimEnd()
                val after = mention.after.trimStart()
                if (before.isNotEmpty()) Text(before, Modifier.alignByBaseline())
                RouteBadgeChip(
                    mention.route.shortName,
                    mention.route.routeColor,
                    // A gap only where text meets the badge, so a badge that opens the sentence starts flush.
                    Modifier.alignByBaseline().padding(
                        start = if (before.isNotEmpty()) BADGE_GAP else 0.dp,
                        end = if (after.isNotEmpty()) BADGE_GAP else 0.dp
                    ),
                    maxWidth = 120.dp
                )
                if (after.isNotEmpty()) Text(after, Modifier.alignByBaseline())
            }
        }
    }
}

private val BADGE_GAP = 4.dp
