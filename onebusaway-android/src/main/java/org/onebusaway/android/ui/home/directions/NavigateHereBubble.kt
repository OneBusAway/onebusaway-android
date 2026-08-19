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
package org.onebusaway.android.ui.home.directions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.onebusaway.android.R
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.ui.tripplan.TripEndpointDotIcon
import org.onebusaway.android.ui.tripplan.TripEndpointSlot
import org.onebusaway.android.util.GeoPoint

/** Stable handles for the bubble and the endpoint dot it is marked with, so a render can sample them. */
object NavigateHereBubbleTestTags {
    const val BUBBLE = "navigateHereBubble"
    const val DOT = "navigateHereDot"
}

/**
 * The tail's box, a diamond of which half is covered by the bubble. So it reaches half this far past
 * the bubble's edge, and that is also the gap the bubble keeps from the point it names.
 */
private val TAIL_SIZE = 14.dp

/** How close to the edge of the map the bubble may be pushed before it is held off. */
private val EDGE_MARGIN = 12.dp

/** How often the bubble re-asks the map where its point is now; the tour's spotlights poll the same. */
private const val PROJECTION_POLL_MILLIS = 120L

/**
 * What a long press on the map offers: **navigate here** — a trip from where the rider is to the point
 * they pressed (#2243).
 *
 * One option, at the press. It replaces the centered "directions from here / directions to here" modal:
 * two options is a question the rider almost never wanted asked (a long press on the map is how you say
 * "take me *there*"; a journey between two other places is what the trip form is for), and a card in the
 * middle of the screen made the rider look away from the very point they had pressed in order to answer
 * it. So the offer is drawn at the point instead, with a tail pointing back at it — which is also what
 * says *where* the press landed, on a map that draws nothing there.
 *
 * This is the one long press in the app that deliberately does not use `CenteredLongPressMenu` (#2112).
 * That component exists to give a row's menu one consistent position however the row was reached; here
 * the press has a *place*, and that place is the whole content of the offer.
 *
 * [point] is followed live through the flavor-neutral [projector] — the same seam the tour's spotlights
 * use — so the bubble stays on its point rather than on the pixel that was pressed. It draws nothing
 * while the projection is unavailable (the map not laid out yet, or the point panned off screen), which
 * is the honest rendering for "there is nothing on screen to point at"; the offer itself stands until it
 * is taken or dismissed.
 *
 * Dismissal is a tap anywhere outside it, or Back. Those taps are swallowed rather than passed to the
 * map beneath, exactly as the modal this replaces swallowed them: a press that raised an offer should be
 * answerable — including by ignoring it — without the answer also focusing a stop.
 */
@Composable
fun NavigateHereBubble(
    point: GeoPoint,
    projector: MapProjector?,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var anchor by remember(point, projector) { mutableStateOf<ScreenOffset?>(null) }
    // Re-projected on a short poll, as the tour's spotlights are: the map publishes only its camera
    // *idle*, so a flight in progress would otherwise leave the bubble behind at where the point was.
    LaunchedEffect(point, projector) {
        val proj = projector ?: return@LaunchedEffect
        while (true) {
            anchor = proj.toScreen(point)
            delay(PROJECTION_POLL_MILLIS)
        }
    }
    NavigateHereOffer(anchor, onNavigate, onDismiss, modifier)
}

/**
 * The offer as drawn, over the map, once the pressed point has [anchor] to hang off — split from the
 * projection above so what the rider sees can be rendered from a fixed point in a test, rather than
 * against a live map.
 */
@Composable
internal fun NavigateHereOffer(
    anchor: ScreenOffset?,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier
            .fillMaxSize()
            // Every tap out here dismisses the offer, and goes no further; see the doc above.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        val here = anchor ?: return@Box
        val density = LocalDensity.current
        val tailSizePx = with(density) { TAIL_SIZE.toPx() }
        val marginPx = with(density) { EDGE_MARGIN.toPx() }
        val tailColor = MaterialTheme.colorScheme.surfaceContainerHigh

        Layout(
            content = {
                // The tail is composed first so the bubble draws over the half of it that laps under the
                // bubble's own edge — which is what leaves a triangle showing on whichever side the
                // bubble ended up, without the tail having to know which side that was.
                Box(Modifier.size(TAIL_SIZE).background(tailColor, DiamondShape))
                NavigateHerePill(onNavigate)
            }
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val tail = measurables[0].measure(loose)
            val bubble = measurables[1].measure(loose)
            val placement = navigateHereBubblePlacement(
                anchorX = here.x,
                anchorY = here.y,
                bubbleWidth = bubble.width,
                bubbleHeight = bubble.height,
                containerWidth = constraints.maxWidth,
                containerHeight = constraints.maxHeight,
                tailSizePx = tailSizePx,
                marginPx = marginPx
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                // Straddling the bubble edge it points away from, so that exactly half of it shows.
                tail.place(
                    x = placement.tailCenterX - tail.width / 2,
                    y = (if (placement.above) placement.y + bubble.height else placement.y) - tail.height / 2
                )
                bubble.place(placement.x, placement.y)
            }
        }
    }
}

/** The offer itself: the destination dot the trip form will show, and what pressing it does. */
@Composable
private fun NavigateHerePill(onNavigate: () -> Unit) {
    Surface(
        onClick = onNavigate,
        modifier = Modifier.testTag(NavigateHereBubbleTestTags.BUBBLE),
        shape = MaterialTheme.shapes.large,
        // Flat-toned on purpose: a tonal elevation would tint the pill away from the plain container
        // colour, and the tail — which is a bare shape, not a Surface — would no longer match it.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The mark the pressed point will carry once the trip is planned — the trip-plan rail's own
            // destination dot, kept from the menu this replaces so the offer and the filled form agree.
            TripEndpointDotIcon(
                TripEndpointSlot.TO,
                Modifier.testTag(NavigateHereBubbleTestTags.DOT)
            )
            Text(
                text = stringResource(R.string.map_navigate_here),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * The tail's shape: a diamond rather than a triangle, so one shape serves a bubble above the press and
 * one below it. Half of it is always covered by the bubble; the half that shows is a triangle pointing
 * at the pressed point.
 */
private val DiamondShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** Where the bubble and its tail sit, in the coordinates of the surface the offer is drawn on. */
internal data class NavigateHerePlacement(
    val x: Int,
    val y: Int,
    val tailCenterX: Int,
    /** Whether the bubble ended up above the pressed point (its tail then hangs off the bottom edge). */
    val above: Boolean
)

/**
 * Places the bubble against the pressed point: above it by preference, below it when there is no room,
 * and never past the edges of the map.
 *
 * Above by preference because the finger that made the press is still over the point, and because a
 * bubble below a press near the bottom of the screen would land under the arrivals drawer's peek. The
 * tail follows the *point*, not the bubble's centre, so a bubble that had to be pushed along an edge
 * still says which point it is about.
 *
 * Pure, so the placement is unit-testable without composing anything — see NavigateHereBubbleTest.
 */
internal fun navigateHereBubblePlacement(
    anchorX: Float,
    anchorY: Float,
    bubbleWidth: Int,
    bubbleHeight: Int,
    containerWidth: Int,
    containerHeight: Int,
    tailSizePx: Float,
    marginPx: Float
): NavigateHerePlacement {
    // Only half the tail's box shows past the bubble, so that half is the gap it keeps from the point.
    val gap = tailSizePx / 2f
    val above = anchorY - gap - bubbleHeight >= marginPx
    val y = if (above) anchorY - gap - bubbleHeight else anchorY + gap
    val x = anchorX - bubbleWidth / 2f
    // Each clamp holds the far edge first and the near edge second, so a bubble with less room than it
    // needs is pinned against the near margin rather than pushed off the other side of the screen.
    val placedX = x.coerceAtMost(containerWidth - marginPx - bubbleWidth).coerceAtLeast(marginPx)
    val placedY = y.coerceAtMost(containerHeight - marginPx - bubbleHeight).coerceAtLeast(marginPx)
    // Held inside the bubble's own edges by half the tail, so the whole triangle sits against it.
    val tailCenterX = anchorX
        .coerceAtLeast(placedX + gap)
        .coerceAtMost(placedX + bubbleWidth - gap)
    return NavigateHerePlacement(
        x = placedX.toInt(),
        y = placedY.toInt(),
        tailCenterX = tailCenterX.toInt(),
        above = above
    )
}
