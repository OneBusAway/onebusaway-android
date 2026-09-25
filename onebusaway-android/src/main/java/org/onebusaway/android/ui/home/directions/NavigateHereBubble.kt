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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 * the bubble's edge, and that is also the gap the bubble keeps from what it points at.
 */
private val TAIL_SIZE = 14.dp

/** How close to the edge of the map the bubble may be pushed before it is held off. */
private val EDGE_MARGIN = 12.dp

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
 * The press leaves a **pin** at [point] (dropped by [org.onebusaway.android.map.MapViewModel], drawn by
 * the map itself) and this is the bubble standing over it. It is re-projected every frame through the
 * flavor-neutral [projector] — the same seam the tour's spotlights use — so the pair reads as one thing
 * on the map: the rider can pan and zoom around the pinned place, and the bubble travels with it. Pan
 * the pin off the map and the bubble goes with it, rather than parking against the nearest edge to name
 * a place no longer in sight; the offer still *stands*, and comes back when its pin does.
 *
 * Deliberately **not** modal — that is the difference from the menu it replaces, and the reason the
 * point is a pin rather than a remembered pixel. Nothing here consumes a touch except the bubble's own
 * surface, so the map underneath keeps every gesture; dismissal is Back, or any tap on the map (which
 * the map answers itself, by putting its pin away — see `MapFeature`).
 */
@Composable
fun NavigateHereBubble(
    point: GeoPoint,
    projector: MapProjector?,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val anchor = remember(point, projector) { mutableStateOf<ScreenOffset?>(null) }
    // Re-projected once per frame, rather than on the timed poll the tour's spotlights use: those hang
    // over a map the overlay has frozen, while this one has to survive the rider dragging the map under
    // it. The camera publishes only its *idle*, so a frame is the finest signal there is — and anything
    // coarser shows as the bubble swimming behind the pin through a pan.
    //
    // Deliberately not gated on the map's gesture flag (`MapHost.cameraInteracting`, which the stop
    // loaders use): that is raised for *rider* gestures only, so a programmatic flight — the tour aiming
    // at its demo destination with the offer already up, a zoom button, a recentre — would strand the
    // bubble until the flight ended. Standing still is cheap on its own terms instead: an unchanged
    // projection writes an equal value, which invalidates nothing, and the read below is deferred to
    // layout, so an idle camera costs one projection per frame and no recomposition at all.
    LaunchedEffect(point, projector) {
        val proj = projector ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            anchor.value = proj.toScreen(point)
        }
    }
    NavigateHereOffer({ anchor.value }, onNavigate, onDismiss, modifier)
}

/**
 * The offer as drawn, over the map, wherever [anchor] currently puts the pin it belongs to — split from
 * the projection above so what the rider sees can be rendered from a fixed point in a test, rather than
 * against a live map.
 *
 * [anchor] is a **lambda**, and is read only inside the measure block below. That is what keeps a moving
 * pin from recomposing anything: a pan re-runs the placement and nothing else, while the pill and its
 * tail — and the dp→px conversions, which the measure scope supplies for free — are composed once and
 * left alone. A null anchor (or one off the map) draws nothing, and needs no separate branch: the
 * content is composed either way, and simply isn't placed.
 */
@Composable
internal fun NavigateHereOffer(
    anchor: () -> ScreenOffset?,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val tailColor = MaterialTheme.colorScheme.surfaceContainerHigh
    // How much room to leave around the point: the height of the pin this map's SDK drew there, which
    // only the flavor can say — maplibre's default marker is nearly twice the height of Google's. Kept
    // clear on *both* sides rather than only above, because the two SDKs also differ on where a marker
    // is anchored (at its tip, or centred on the point), and this way the bubble clears the pin under
    // either convention. The cost is some air below a bubble that had to flip under its point, which is
    // the rarer placement anyway.
    val pinHeight = dimensionResource(R.dimen.map_pin_height)
    // A bare layout: it carries no pointer input of its own, so every gesture it doesn't cover goes
    // straight through to the map — which is what lets the rider pan and zoom around the pinned place.
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            // The tail is composed first so the bubble draws over the half of it that laps under the
            // bubble's own edge — which is what leaves a triangle showing on whichever side the bubble
            // ended up, without the tail having to know which side that was.
            Box(Modifier.size(TAIL_SIZE).background(tailColor, DiamondShape))
            NavigateHerePill(onNavigate)
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tail = measurables[0].measure(loose)
        val bubble = measurables[1].measure(loose)
        val here = anchor()
        val placement = here?.let {
            navigateHereBubblePlacement(
                anchorX = it.x,
                anchorY = it.y,
                bubbleWidth = bubble.width,
                bubbleHeight = bubble.height,
                containerWidth = constraints.maxWidth,
                containerHeight = constraints.maxHeight,
                tailSizePx = TAIL_SIZE.toPx(),
                pinClearancePx = pinHeight.toPx(),
                marginPx = EDGE_MARGIN.toPx()
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Nothing placed — so nothing drawn — before the pin has been projected, or once it has been
            // panned off the map. Measured all the same, since the size is what decides where it fits
            // when the pin comes back.
            if (placement == null || !placement.onScreen) return@layout
            // Straddling the bubble edge it points away from, so that exactly half of it shows.
            tail.place(
                x = placement.tailCenterX - tail.width / 2,
                y = (if (placement.above) placement.y + bubble.height else placement.y) - tail.height / 2
            )
            bubble.place(placement.x, placement.y)
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
    /** Whether the bubble ended up above the pinned point (its tail then hangs off the bottom edge). */
    val above: Boolean,
    /**
     * Whether the pinned point is on the map at all. False means draw nothing: the bubble's position is
     * only meaningful beside the pin it belongs to, and the clamps below would otherwise park it against
     * the nearest edge, naming a place that has been panned out of sight.
     */
    val onScreen: Boolean
)

/**
 * Places the bubble against the pinned point: above the pin standing there by preference, below it when
 * there is no room for that, and never past the edges of the map.
 *
 * Above by preference because that is where a pin's head usually is, and because a bubble below a press
 * near the bottom of the screen would land under the arrivals drawer's peek. [pinClearancePx] is kept
 * on whichever side the bubble lands, so the pin is cleared whether its map anchors it at the tip or
 * centres it on the point. The tail follows the point, not the bubble's centre, so a bubble that had to
 * be pushed along an edge still says which pin it belongs to.
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
    pinClearancePx: Float,
    marginPx: Float
): NavigateHerePlacement {
    // Only half the tail's box shows past the bubble, so that half is the gap it keeps from the pin it
    // points at, on top of the room the pin itself takes.
    val gap = pinClearancePx + tailSizePx / 2f
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
        above = above,
        // Asked of the raw point, before any clamping: a projector reports where a point *would* be, and
        // both map SDKs answer perfectly happily for one that is off the map altogether.
        onScreen = anchorX in 0f..containerWidth.toFloat() && anchorY in 0f..containerHeight.toFloat()
    )
}
