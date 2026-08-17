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
package org.onebusaway.android.ui.tutorial

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.components.ArrivalLegend
import org.onebusaway.android.ui.icons.AppIcons

/**
 * One step of a spotlight tutorial: a caption (title + body, optional trailing [bodyIcon]) anchored to
 * a UI target by [anchorId]. [id] is the step's own identity and, for persisted tutorials, its
 * "already shown" preference key.
 *
 * [anchorId] defaults to [id], which is the right thing for a tutorial whose steps each spotlight a
 * different target — the two were one field until the scripted tour (#2164) needed several consecutive
 * steps to say different things about the *same* target (three about the itinerary list, two about the
 * ETA pill). Sharing the id outright would have made those steps indistinguishable to everything that
 * keys on it, starting with the overlay's own step-change animation.
 *
 * [extra] is optional content the caption renders under its body — the colour legend, for the step
 * that explains it. [gesture] mimes an action over the target for a step whose subject is a gesture
 * rather than a control, and [captionAtTop] overrides where the caption sits.
 *
 * [action] is what the app should *do* when this step opens — the mechanism the scripted tour (#2164)
 * is built on. A step that says "tap a route to see its vehicles" performs that navigation itself, so
 * what the caption describes is always what is on screen behind it, whatever the user does. Null (the
 * default) is a step that only narrates what is already there, which is every step of the older
 * opportunistic tutorials.
 */
data class TutorialStep(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:DrawableRes val bodyIcon: Int? = null,
    val action: TutorialAction? = null,
    val anchorId: String = id,
    val extra: TutorialExtra? = null,
    val gesture: TutorialGesture? = null,
    /**
     * Forces the caption to the top of the screen, overriding the automatic placement.
     *
     * That placement only knows where the *target* is, and keeps the caption on the opposite half. It
     * has no idea what else is on screen — so a step whose target sits mid-screen while the arrivals
     * drawer fills the bottom gets a caption laid over the drawer, which is usually the thing the next
     * step is about.
     */
    val captionAtTop: Boolean = false
)

/**
 * Extra content a step's caption renders beneath its body.
 *
 * A closed set rather than a `@Composable` slot on [TutorialStep], so a step stays plain data that a
 * sequence can declare, compare and test without dragging composition into it.
 */
enum class TutorialExtra {
    /** The arrivals colour + glyph legend, shown inline by the "what the colours mean" step. */
    ARRIVAL_LEGEND
}

/**
 * A gesture the overlay mimes over the step's target, for a step that teaches an action with no
 * control to point at.
 *
 * A long press leaves nothing on screen to ring — the affordance *is* the gesture — so the tour shows
 * it being performed instead of describing it and hoping.
 */
enum class TutorialGesture {
    /** A finger pressing and holding: a dot that swells, with a ring radiating out of it. */
    LONG_PRESS
}

/**
 * Drives a spotlight tutorial: the active step sequence, the current index, and the on-screen bounds
 * each anchored target reports. UI-only state (no persistence) — the caller decides which steps to
 * [start] (e.g. filtering out already-shown ones) and records them as shown. Targets register their
 * bounds via [Modifier.tutorialAnchor]; [TutorialOverlay] reads [current] + [boundsFor] to draw.
 */
@Stable
class TutorialState {
    var steps by mutableStateOf<List<TutorialStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set

    private val bounds = mutableStateMapOf<String, Rect>()

    /** The step being shown, or null when no tutorial is active. */
    val current: TutorialStep? get() = steps.getOrNull(index)

    /** True while a tutorial is running (an overlay is up, intercepting touches). */
    val active: Boolean get() = current != null

    /** True when [current] is the last step (the caption shows "done" rather than "next"). */
    val isLast: Boolean get() = index >= steps.size - 1

    /**
     * True while the final step plays its expand-to-fill finish flourish: [current] keeps returning that
     * step (so the overlay stays up and animating) until [onFinishExpanded] clears it. A skip / "X"
     * ([dismiss]) doesn't set it — the flourish is the reward for reaching the end.
     */
    var finishing by mutableStateOf(false)
        private set

    /** Begin a sequence at its first step. A no-op for an empty list. */
    fun start(steps: List<TutorialStep>) {
        if (steps.isEmpty()) return
        this.steps = steps
        index = 0
    }

    /**
     * Advance to the next step. Advancing past the last one enters the [finishing] flourish, which
     * clears once the overlay reports it has played ([onFinishExpanded]). A no-op while already
     * [finishing].
     */
    fun advance() {
        if (finishing) return
        if (index < steps.size - 1) index++ else finishing = true
    }

    /** True when there is an earlier step to return to. False on the first step, and while [finishing]. */
    val canGoBack: Boolean get() = !finishing && index > 0

    /** Step back to the previous step. A no-op on the first step, or while [finishing]. */
    fun back() {
        if (!canGoBack) return
        index--
    }

    /** End the tutorial immediately (skip / "X"), without playing the finish flourish. */
    fun dismiss() = clear()

    /** The overlay calls this once the finish flourish has expanded off-screen, ending the tutorial. */
    fun onFinishExpanded() = clear()

    private fun clear() {
        steps = emptyList()
        index = 0
        finishing = false
    }

    /**
     * An anchored target reports its current on-screen bounds (root coordinates). Unchanged values are
     * dropped so a re-reporting source (e.g. the map-stop poll) doesn't needlessly recompose the overlay.
     */
    fun reportBounds(id: String, rect: Rect) {
        if (bounds[id] != rect) bounds[id] = rect
    }

    /** The last-reported bounds of the target keyed by [id], or null if it hasn't laid out yet. */
    fun boundsFor(id: String): Rect? = bounds[id]
}

@Composable
fun rememberTutorialState(): TutorialState = remember { TutorialState() }

/** An ease-out-back curve (overshoots past 1) that gives the annulus pulse its springy "bounce". */
private val SPOTLIGHT_BOUNCE_EASING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

// A step change transitions the spotlight in place rather than sliding it across the screen: the cutout
// shrinks shut over the old target, swaps to the new one, then springs back open. The two halves run
// back-to-back, ~300 ms total.
private const val SPOTLIGHT_CLOSE_MILLIS = 130
private const val SPOTLIGHT_OPEN_MILLIS = 170

// On a genuine finish the ring sweeps out past the screen edges and the overlay tears down.
private const val SPOTLIGHT_FINISH_MILLIS = 300

/** The crisp inner outline drawn right at the target's edge. */
private val RING_WIDTH = 3.dp

/** A softer, wider outline behind it, so the pointer reads over a busy map as well as a flat panel. */
private val RING_HALO_WIDTH = 10.dp

/** Air between the target's bounds and the outline. */
private val RING_PAD = 6.dp

/** How far past its resting size the outline springs on each pulse. */
private val RING_PULSE = 5.dp

/** Corner rounding. A target smaller than twice this gets a fully rounded outline — i.e. a circle. */
private val RING_MAX_CORNER = 28.dp

/** How far inside the screen a full-screen target's outline is drawn, so it stays visible. */
private val RING_EDGE_INSET = 10.dp

/**
 * Provides the active [TutorialState] to deep composables so a target can anchor itself via
 * [Modifier.tutorialAnchor] without threading the state through every signature. Null when no tutorial
 * host is present (the anchor modifier then no-ops), so reused composables (e.g. the legend's EtaPill)
 * stay unaffected.
 */
val LocalTutorialState = staticCompositionLocalOf<TutorialState?> { null }

/**
 * Reports this composable's on-screen bounds to [state] under [id] so [TutorialOverlay] can spotlight
 * it. No-ops when [state] is null (no tutorial host) — safe to leave on a reused composable.
 */
fun Modifier.tutorialAnchor(state: TutorialState?, id: String): Modifier = if (state == null) {
    this
} else {
    onGloballyPositioned { state.reportBounds(id, it.boundsInRoot()) }
}

/**
 * The spotlight overlay for the active tutorial step: a pulsing ring around the step's target, plus a
 * caption card (title + body + Back/Next). Only those buttons move the tour, and the corner "X" ends
 * it. Renders nothing when no tutorial is active, so it stops intercepting touches the moment the
 * sequence finishes.
 *
 * **The ring is the whole affordance — there is no scrim.** This used to dim everything outside the
 * target with a translucent green wash (the legacy ShowcaseView look), which worked for a tutorial that
 * only pointed at static controls and failed for one that *drives the app*: the scripted tour (#2164)
 * opens a route on the map behind the caption, and the wash hid the very change the step was narrating.
 * Attention is directed by the pulsing ring alone, so the app stays fully legible underneath.
 *
 * Drawn as a sibling over the rest of the screen. Targets report their bounds in *root* coordinates;
 * this overlay records its own root position ([positionInRoot]) and translates each target into its
 * local space, so the ring lines up wherever the overlay sits in the hierarchy. Until a target has laid
 * out, no ring is drawn — the caption simply stands alone, which is the honest rendering for "there is
 * nothing to point at yet".
 */
@Composable
fun TutorialOverlay(state: TutorialState) {
    val step = state.current ?: return
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    // The step the spotlight is currently drawn around. It lags [step] across a transition: when the step
    // changes the cutout shrinks shut over the old target, this flips to the new step, and the cutout grows
    // back open over the new target — so the spotlight never slides between the two positions.
    var spotlightStep by remember { mutableStateOf(step) }
    // 0 = closed (no cutout, full scrim); 1 = fully open. Scales the cutout radius (see the Canvas below).
    val openFraction = remember { Animatable(0f) }
    LaunchedEffect(step.id) {
        if (step.id != spotlightStep.id) {
            // Consecutive steps about the *same* target (the scripted tour's three itinerary-list steps)
            // keep the cutout open and just swap the caption: shutting and reopening it over unchanged
            // bounds reads as a glitch rather than as a move.
            if (step.anchorId != spotlightStep.anchorId) {
                openFraction.animateTo(0f, tween(SPOTLIGHT_CLOSE_MILLIS, easing = FastOutLinearInEasing))
            }
            spotlightStep = step
        }
        // The springy reopen — also the initial pop-in, where there's no close half to run first.
        openFraction.animateTo(1f, tween(SPOTLIGHT_OPEN_MILLIS, easing = SPOTLIGHT_BOUNCE_EASING))
    }

    // The finish flourish: on a genuine finish the cutout grows past the screen edges (see the Canvas),
    // clearing the scrim, and then the overlay tears itself down.
    val finishProgress = remember { Animatable(0f) }
    LaunchedEffect(state.finishing) {
        if (state.finishing) {
            finishProgress.animateTo(1f, tween(SPOTLIGHT_FINISH_MILLIS, easing = FastOutSlowInEasing))
            state.onFinishExpanded()
        }
    }

    // The cutout tracks the transitioning [spotlightStep]; the caption jumps straight to the incoming
    // step (its text is the next thing to read), so its placement keys off that target.
    val spotlightTarget = state.boundsFor(spotlightStep.anchorId)?.translate(-overlayOrigin.x, -overlayOrigin.y)
    val captionTarget = state.boundsFor(step.anchorId)?.translate(-overlayOrigin.x, -overlayOrigin.y)

    // A gentle, continuous "bounce" for the annulus around the cutout — an overshooting ease-in-out so
    // the ring springs out a little past its resting thickness and settles back, repeatedly.
    val pulse by rememberInfiniteTransition(label = "spotlight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = SPOTLIGHT_BOUNCE_EASING),
            repeatMode = RepeatMode.Reverse
        ),
        label = "annulusPulse"
    )

    // The long-press mime: one 0->1 sweep per cycle, restarting rather than reversing so the press
    // reads as repeated taps-and-holds rather than a ring breathing in and out. Only collected while a
    // step actually asks for a gesture.
    val gesturePhase by rememberInfiniteTransition(label = "gesture").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LONG_PRESS_CYCLE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "longPress"
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            // The overlay swallows every touch that isn't one of the caption's own buttons, so the app
            // underneath can't be operated out from under the script — but it no longer *advances* on
            // one. Tap-anywhere-to-continue was inherited from the legacy ShowcaseView, and it stopped
            // being safe the moment there was a Back button to miss: a tap a few pixels off it moved
            // the tour the other way, which is precisely the mistake Back exists to undo.
            .pointerInput(step.id) { detectTapGestures { } }
    ) {
        // The ring: a crisp brand-color outline hugging the target, backed by a softer, wider halo so it
        // reads against a busy map as well as against a flat panel. Both pulse together.
        val ringColor = colorResource(R.color.theme_primary_variant)
        val haloColor = colorResource(R.color.tutorial_background)
        // The target's bounds plus a little air. Kept tight — the ring is a pointer, and a loose one
        // stops naming which control it means.
        val ringPadPx = with(LocalDensity.current) { RING_PAD.toPx() }
        val maxCornerPx = with(LocalDensity.current) { RING_MAX_CORNER.toPx() }
        val edgeInsetPx = with(LocalDensity.current) { RING_EDGE_INSET.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val target = spotlightTarget
            if (target != null && !target.isEmpty) {
                // A rounded *rectangle* rather than a circle, which is the same thing for a square
                // target — a stop marker, a star, a route badge all still get a circle — but hugs a wide
                // one instead of swallowing its surroundings. A circle round the itinerary strip or the
                // planner's action bar had to be as wide as the row and was therefore taller than the
                // screen could show, which named everything and so named nothing.
                val bounds = target.inflate(ringPadPx)
                // The bounce: the outline springs a little past its resting size and settles back, so
                // the eye is drawn by motion rather than by dimming everything else.
                val pulsePx = RING_PULSE.toPx() * pulse
                // On finish the outline sweeps out past the screen and is gone.
                val burstPx = if (finishProgress.value == 0f) {
                    0f
                } else {
                    finishProgress.value * (size.maxDimension + RING_MAX_CORNER.toPx())
                }
                // Opening: grow from nothing to full size, so a step change pops rather than slides.
                val grown = bounds.inflate(pulsePx + burstPx)
                val rect = grown.scaledAbout(grown.center, openFraction.value)
                    // Kept inside the screen so a target as large as the map — the opening "this is the
                    // map" step — still shows its outline rather than drawing it off every edge.
                    .clampedInto(size, edgeInsetPx)
                val corner = minOf(rect.minDimension / 2f, maxCornerPx)
                val fade = 1f - finishProgress.value
                drawRoundRect(
                    color = haloColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(corner, corner),
                    alpha = fade,
                    style = Stroke(width = RING_HALO_WIDTH.toPx())
                )
                drawRoundRect(
                    color = ringColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(corner, corner),
                    alpha = fade,
                    style = Stroke(width = RING_WIDTH.toPx())
                )
                if (step.gesture == TutorialGesture.LONG_PRESS && finishProgress.value == 0f) {
                    drawLongPress(rect.center, gesturePhase, ringColor)
                }
            }
        }

        // The caption lives at the **bottom** unless the target is down there too, in which case it moves
        // to the top to stay clear.
        //
        // Bottom is also what an unresolved target gets, rather than centre. A target's bounds arrive a
        // frame or two after the step opens, so a centred default meant the card popped up mid-screen and
        // then slid down the moment the bounds landed — the "tap a stop" step visibly threw its caption
        // across the screen on arrival. Defaulting to where it will almost always end up makes that
        // settle invisible.
        val rootHeightPx = constraints.maxHeight.toFloat()
        val alignment = when {
            step.captionAtTop -> Alignment.TopCenter
            captionTarget == null || captionTarget.isEmpty -> Alignment.BottomCenter
            captionTarget.center.y > rootHeightPx / 2f -> Alignment.TopCenter
            else -> Alignment.BottomCenter
        }
        // The caption goes the moment the finish flourish starts — the spotlight blooming open is the
        // whole "you're done" gesture, so a lingering card would only be in the way.
        if (!state.finishing) {
            TutorialCaption(
                step = step,
                isLast = state.isLast,
                canGoBack = state.canGoBack,
                onNext = state::advance,
                onBack = state::back,
                onClose = state::dismiss,
                modifier = Modifier.align(alignment)
            )
        }
    }
}

@Composable
private fun TutorialCaption(
    step: TutorialStep,
    isLast: Boolean,
    canGoBack: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .widthIn(max = 360.dp),
        shape = RoundedCornerShape(16.dp),
        // The brand green, not the theme surface. With the scrim gone (see [TutorialOverlay]) a card
        // painted the ordinary surface colour is the same near-black as the dark-mode map behind it, so
        // the one thing the rider is meant to read had no edge at all. Branded rather than a fixed
        // green so a white-label build's own colour carries through, exactly as the ring's does.
        //
        // The *dark* brand variant, and fully opaque: `tutorial_background` is the same hue at 87%
        // alpha, which is right for a scrim covering the screen and wrong for a card — the arrivals
        // rows behind it read straight through the caption text.
        color = colorResource(R.color.theme_primary_variant),
        contentColor = Color.White,
        shadowElevation = 8.dp
    ) {
        // Pass the app name as a format arg to both strings so a branded welcome title ("Welcome to
        // %1$s!") fills in; strings without a placeholder simply ignore the extra arg (white-label).
        val appName = stringResource(R.string.app_name)
        Column(Modifier.padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 16.dp)) {
            // Title with a corner "X" that ends the whole tutorial.
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(step.title, appName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(top = 12.dp)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = stringResource(R.string.tutorial_button_close)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = stringResource(step.body, appName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                step.bodyIcon?.let { icon ->
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = LocalContentColor.current
                    )
                }
            }
            // A step's extra content sits inside the card. The legend used to be shown by opening the
            // app's Legend *dialog* over the tutorial, which stacked one modal on another and hid the
            // arrivals the legend was describing.
            when (step.extra) {
                TutorialExtra.ARRIVAL_LEGEND -> ArrivalLegend(
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                    compact = true
                )
                null -> Unit
            }
            // Back on the left, advance on the right: "Next", or "Finish" on the last step. The corner
            // "X" ends the tutorial outright. Back only appears once there is somewhere to go, rather
            // than sitting there disabled on the first step.
            val advanceLabel = if (isLast) R.string.tutorial_button_finish else R.string.pager_button_next
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp),
                // Back sits at the far left when it's there; with only the advance button, it holds the
                // right edge on its own rather than being pushed there by an empty spacer.
                horizontalArrangement = if (canGoBack) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) {
                    TextButton(onClick = onBack, colors = captionButtonColors()) {
                        Text(stringResource(R.string.tutorial_button_back))
                    }
                }
                TextButton(onClick = onNext, colors = captionButtonColors()) {
                    Text(stringResource(advanceLabel))
                }
            }
        }
    }
}

/**
 * Mimes a finger pressing and holding at [center]: a dot that swells and holds, with a ring radiating
 * out of it and fading. [phase] runs 0..1 once per cycle.
 *
 * The dot holds at full size for the back half of the cycle, which is what makes this read as a *long*
 * press rather than a tap — the pause is the whole message.
 */
private fun DrawScope.drawLongPress(center: Offset, phase: Float, color: Color) {
    // The press: swell over the first fifth of the cycle, then hold.
    val press = (phase / LONG_PRESS_SWELL_FRACTION).coerceAtMost(1f)
    val dotRadius = LONG_PRESS_DOT_RADIUS.toPx() * (0.55f + 0.45f * press)
    // The ripple: starts once the finger is down and expands out, fading as it goes.
    val ripple = ((phase - LONG_PRESS_SWELL_FRACTION) / (1f - LONG_PRESS_SWELL_FRACTION)).coerceIn(0f, 1f)
    if (ripple > 0f) {
        drawCircle(
            color = color,
            radius = lerp(dotRadius, LONG_PRESS_RIPPLE_RADIUS.toPx(), ripple),
            center = center,
            alpha = 1f - ripple,
            style = Stroke(width = RING_WIDTH.toPx())
        )
    }
    drawCircle(color = color, radius = dotRadius, center = center, alpha = 0.9f)
}

/** How long one press-and-hold cycle of the long-press mime takes. */
private const val LONG_PRESS_CYCLE_MILLIS = 1600

/** The fraction of that cycle spent pressing down; the rest is the hold and the ripple. */
private const val LONG_PRESS_SWELL_FRACTION = 0.2f

private val LONG_PRESS_DOT_RADIUS = 14.dp
private val LONG_PRESS_RIPPLE_RADIUS = 46.dp

/**
 * The caption's buttons. A [TextButton] takes its label colour from the theme's primary, which is the
 * brand green — invisible on a brand-green card — so both buttons take the card's own content colour.
 */
@Composable
private fun captionButtonColors() = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)

/** This rect scaled about [about] by [factor] — the outline's grow-in on a step change. */
private fun Rect.scaledAbout(about: Offset, factor: Float): Rect = Rect(
    left = about.x + (left - about.x) * factor,
    top = about.y + (top - about.y) * factor,
    right = about.x + (right - about.x) * factor,
    bottom = about.y + (bottom - about.y) * factor
)

/**
 * This rect held inside a [size]-sized area, [inset] in from its edges.
 *
 * Only ever shrinks: a target already on screen is untouched, while one as large as the whole map is
 * pulled back to where its outline can actually be seen. A rect that would invert under the inset
 * collapses to its centre rather than turning inside out.
 */
private fun Rect.clampedInto(size: Size, inset: Float): Rect {
    val minLeft = inset
    val minTop = inset
    val maxRight = (size.width - inset).coerceAtLeast(minLeft)
    val maxBottom = (size.height - inset).coerceAtLeast(minTop)
    val l = left.coerceIn(minLeft, maxRight)
    val t = top.coerceIn(minTop, maxBottom)
    return Rect(l, t, right.coerceIn(l, maxRight), bottom.coerceIn(t, maxBottom))
}
