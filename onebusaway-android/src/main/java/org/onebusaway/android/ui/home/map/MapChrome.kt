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
package org.onebusaway.android.ui.home.map

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.directions.util.ConversionUtils
import org.onebusaway.android.ui.compose.unitsAreMetric
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.ScriptedTutorial
import org.onebusaway.android.ui.tutorial.tutorialAnchor

/**
 * The map's Compose overlay chrome, replacing the XML my-location FAB, zoom buttons, and the
 * third-party android-fab layers speed-dial. Hosted over the map inside HomeScreen's
 * BottomSheetScaffold content; [fabBottomInsetTarget] is the sheet-driven lift target (the peek
 * height when collapsed, else 0) that the FABs animate to — replacing the legacy
 * `moveFabsLocation()` margin animation. All state + actions are supplied by [MapFeature].
 */
@Composable
fun MapChrome(
    zoomVisible: Boolean,
    leftHandMode: Boolean,
    layersVisible: Boolean,
    rentalsActive: Boolean,
    bikesActive: Boolean,
    scootersActive: Boolean,
    rentalsLoading: Boolean,
    mapLoading: Boolean,
    fabBottomInsetTarget: Dp,
    onMyLocation: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleRentals: () -> Unit,
    onToggleBikes: () -> Unit,
    onToggleScooters: () -> Unit,
    onHideRentalButton: () -> Unit
) {
    // Animate the lift here so the per-frame value only recomposes the FABs, not the hosting map
    // AndroidView / overlay cards (which are siblings in HomeScreen's Box).
    val fabBottomInset by animateDpAsState(fabBottomInsetTarget, label = "fabInset")
    val sideAlign = if (leftHandMode) Alignment.BottomStart else Alignment.BottomEnd
    val marginHorizontal = dimensionResource(R.dimen.fab_margin_horizontal)
    val marginBottom = dimensionResource(R.dimen.fab_margin_vertical)
    val accent = colorResource(R.color.theme_accent)
    Box(Modifier.fillMaxSize()) {
        // Indeterminate map-loading bar across the top (replaces the legacy XML progress_horizontal).
        if (mapLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )
        }
        if (zoomVisible) {
            ZoomControls(
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = marginBottom + fabBottomInset)
            )
        }
        if (layersVisible) {
            RentalsFab(
                active = rentalsActive,
                bikesActive = bikesActive,
                scootersActive = scootersActive,
                loading = rentalsLoading,
                onToggle = onToggleRentals,
                onToggleBikes = onToggleBikes,
                onToggleScooters = onToggleScooters,
                onHide = onHideRentalButton,
                modifier = Modifier
                    .align(sideAlign)
                    // The panel's own padding insets the FAB inside it, which would otherwise leave the
                    // rental button standing off the edge further than the my-location FAB below it.
                    // Taking that padding back out of the margin puts the two buttons on one vertical
                    // line — and works in left-hand mode unchanged, since both hug the same edge.
                    .padding(
                        horizontal = (marginHorizontal - RENTAL_SURFACE_PADDING_HORIZONTAL)
                            .coerceAtLeast(0.dp)
                    )
                    // Clear the my-location FAB below rather than guess a margin: it occupies
                    // marginBottom..marginBottom+FAB_SIZE, and the panel's own padding drops its button
                    // that much lower again, so both are subtracted back out to leave exactly
                    // RENTAL_FAB_GAP of air between the two buttons.
                    .padding(
                        bottom = marginBottom +
                            FAB_SIZE +
                            RENTAL_FAB_GAP -
                            RENTAL_SURFACE_PADDING +
                            fabBottomInset
                    )
                    // The scripted tour's micromobility step spotlights this control (#2164).
                    .tutorialAnchor(LocalTutorialState.current, ScriptedTutorial.KEY_RENTALS)
            )
        }
        // The my-location FAB always shows on the map (this chrome only composes on HOME, the map screen).
        FloatingActionButton(
            onClick = onMyLocation,
            containerColor = accent,
            contentColor = Color.White,
            modifier = Modifier
                .align(sideAlign)
                .padding(horizontal = marginHorizontal)
                .padding(bottom = marginBottom + fabBottomInset)
        ) {
            Icon(
                painterResource(R.drawable.ic_maps_my_location),
                contentDescription = stringResource(R.string.map_option_mylocation),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** A white rounded pill of zoom-out / zoom-in glyphs, mirroring the legacy zoom_buttons_layout. */
@Composable
private fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.85f),
        shadowElevation = 4.dp
    ) {
        Row {
            IconButton(onClick = onZoomOut) {
                Icon(
                    painterResource(R.drawable.ic_zoom_out),
                    contentDescription = stringResource(R.string.map_option_zoom_out),
                    tint = Color.Unspecified
                )
            }
            IconButton(onClick = onZoomIn) {
                Icon(
                    painterResource(R.drawable.ic_zoom_in),
                    contentDescription = stringResource(R.string.map_option_zoom_in),
                    tint = Color.Unspecified
                )
            }
        }
    }
}

/**
 * The rental control: a master button that shows or hides rentals, which grows into a surface holding
 * a button per mode while they are showing (#2168).
 *
 * The surface is always present and animates between transparent and opaque rather than appearing and
 * disappearing, so the master button keeps one parent across the transition — re-parenting it would
 * make it jump rather than let the panel grow around it. Growth runs upward because the whole control
 * is bottom-aligned in [MapChrome], so the mode buttons rise off the master instead of pushing it
 * down over the my-location FAB.
 *
 * The modes sit under the master rather than beside it because they are a refinement of one decision,
 * not three peers — and because both come off a single `vehicleRentalsByBbox` response, so a rider who
 * just wants to see what is around taps once. The mode toggles keep their own settings while the
 * master is off, so switching rentals back on restores what the rider had rather than turning
 * everything on.
 */
@Composable
private fun RentalsFab(
    active: Boolean,
    bikesActive: Boolean,
    scootersActive: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onToggleBikes: () -> Unit,
    onToggleScooters: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The panel only exists visually while the modes are on show; off, the master reads as a plain FAB.
    val surfaceColor by animateColorAsState(
        if (active) Color.White.copy(alpha = 0.9f) else Color.Transparent,
        label = "rentalSurfaceColor"
    )
    val surfaceElevation by animateDpAsState(
        if (active) RENTAL_SURFACE_ELEVATION else 0.dp,
        label = "rentalSurfaceElevation"
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(RENTAL_SURFACE_CORNER),
        color = surfaceColor,
        shadowElevation = surfaceElevation
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = RENTAL_SURFACE_PADDING_HORIZONTAL,
                vertical = RENTAL_SURFACE_PADDING
            ),
            // Centred rather than edge-aligned: the mode toggles are narrower than the master, so
            // centring stacks them on its axis instead of flushing them to one side of it. Which
            // screen edge the whole control hugs is [MapChrome]'s business, not this column's — which
            // is why left-hand mode no longer reaches in here.
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RENTAL_SURFACE_PADDING)
        ) {
            AnimatedVisibility(visible = active) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RENTAL_SURFACE_PADDING)
                ) {
                    ModeToggle(
                        label = stringResource(R.string.layers_bikes_label),
                        iconRes = R.drawable.ic_directions_bike,
                        selectedColor = colorResource(R.color.layer_bikeshare_color),
                        selected = bikesActive,
                        onClick = onToggleBikes
                    )
                    ModeToggle(
                        label = stringResource(R.string.layers_scooters_label),
                        iconRes = R.drawable.ic_kick_scooter,
                        selectedColor = colorResource(R.color.layer_scooters_color),
                        selected = scootersActive,
                        onClick = onToggleScooters
                    )
                }
            }
            // Same outline-vs-fill language as the mode toggles below it, in one colour throughout: off
            // is the layer's colour as an outline and glyph on white, on is that colour filled with a
            // white glyph. So the button only ever changes which side of itself is tinted, and the
            // rider never has to learn that grey means anything.
            //
            // The unselected fill is opaque white rather than transparent, unlike the mode toggles:
            // with rentals off there is no panel behind the master, so a see-through button would sit
            // directly on the basemap and lose both its shape and its glyph over dark ground.
            val rentalColor = colorResource(R.color.layer_bikeshare_color)
            val fabContainer by animateColorAsState(
                if (active) rentalColor else Color.White,
                label = "rentalFabContainer"
            )
            val fabContent by animateColorAsState(
                if (active) Color.White else rentalColor,
                label = "rentalFabContent"
            )
            val fabOutline by animateDpAsState(
                if (active) 0.dp else MODE_TOGGLE_OUTLINE,
                label = "rentalFabOutline"
            )
            // A Surface with combinedClickable rather than a FloatingActionButton: the FAB exposes no
            // long-press, and wrapping one would leave two overlapping click targets fighting over the
            // gesture. Everything a FAB gives us here — shape, elevation, container/content colour — is
            // stated above anyway, and the mode toggles below are already built this way.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                Surface(
                    modifier = Modifier
                        .size(FAB_SIZE)
                        .combinedClickable(
                            onClick = onToggle,
                            onLongClick = { menuOpen = true },
                            onLongClickLabel = stringResource(R.string.layers_rentals_hide_button)
                        ),
                    shape = RENTAL_FAB_SHAPE,
                    color = fabContainer,
                    contentColor = fabContent,
                    shadowElevation = RENTAL_SURFACE_ELEVATION,
                    // Null rather than a zero-width stroke, which still paints a hairline at some
                    // densities and would ring the filled state.
                    border = if (fabOutline > 0.dp) BorderStroke(fabOutline, rentalColor) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // The spinner replaces the glyph rather than sitting beside or around it: the
                        // button already carries the layer's colour, so a ring around the mark would
                        // read as a second, meaningless charge ring next to the ones on the markers.
                        if (loading) {
                            val loadingLabel = stringResource(R.string.layers_rentals_loading)
                            CircularProgressIndicator(
                                color = fabContent,
                                strokeWidth = RENTAL_SPINNER_STROKE,
                                modifier = Modifier
                                    .size(RENTAL_FAB_GLYPH)
                                    .semantics { contentDescription = loadingLabel }
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_bike_rental),
                                contentDescription = stringResource(
                                    if (active) R.string.layers_rentals_hide else R.string.layers_rentals_show
                                ),
                                modifier = Modifier.size(RENTAL_FAB_GLYPH)
                            )
                        }
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layers_rentals_hide_button)) },
                        onClick = {
                            menuOpen = false
                            onHide()
                        }
                    )
                }
            }
        }
    }
}

/**
 * One mode toggle: an outline when the mode is off, filling with the layer's own colour when it is on.
 *
 * The two states differ in *form*, not just tint — an unselected button is an untinted outline and a
 * selected one is a solid disc with a white glyph — so the pair reads as "one of these is on" at a
 * glance, rather than as two buttons in slightly different shades of the same thing. Every property
 * animates, so the tap is a fill rather than a swap.
 *
 * No visible label: the buttons stack on a surface directly under the master they qualify, where the
 * glyph carries it. [label] is still required and becomes the button's content description, and the
 * control is [toggleable] rather than merely clickable, so a screen reader announces the mode *and*
 * whether it is currently on.
 */
@Composable
private fun ModeToggle(
    label: String,
    @DrawableRes iconRes: Int,
    selectedColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val unselected = colorResource(R.color.layer_disabled)
    val container by animateColorAsState(
        if (selected) selectedColor else Color.Transparent,
        label = "modeToggleContainer"
    )
    val content by animateColorAsState(
        if (selected) Color.White else unselected,
        label = "modeToggleContent"
    )
    val outline by animateDpAsState(
        if (selected) 0.dp else MODE_TOGGLE_OUTLINE,
        label = "modeToggleOutline"
    )
    Surface(
        modifier = Modifier
            // Reserves the 48dp interactive minimum in *layout*, so a neighbour cannot be placed inside
            // it, while the disc itself stays 40dp. Material's own components apply this; a hand-rolled
            // Surface has to ask for it.
            .minimumInteractiveComponentSize()
            .size(MODE_TOGGLE_SIZE)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        shape = CircleShape,
        color = container,
        // Null rather than a zero-width stroke: a 0.dp border still paints a hairline on some
        // densities, which would leave a ring around the filled state.
        border = if (outline > 0.dp) BorderStroke(outline, unselected) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconRes),
                contentDescription = label,
                tint = content,
                modifier = Modifier.size(MODE_TOGGLE_GLYPH)
            )
        }
    }
}

/** The mode toggle's diameter, its glyph, and the outline it wears when off. */
private val MODE_TOGGLE_SIZE = 40.dp
private val MODE_TOGGLE_GLYPH = 20.dp
private val MODE_TOGGLE_OUTLINE = 1.5.dp

/** The rental button's spinner stroke — thin enough to read as progress at 24dp, not as a ring. */
private val RENTAL_SPINNER_STROKE = 2.dp

/**
 * The rental button's glyph, ~1.3x the 24dp a FAB icon usually takes.
 *
 * Bigger because this glyph is doing more work than a FAB icon normally does: it is the only thing on
 * the button, and in the outlined state it carries the mark against white rather than sitting white on
 * a filled disc. Applies to the spinner too, which stands in for it. Still well inside the 56dp button.
 */
private val RENTAL_FAB_GLYPH = 31.dp

/**
 * The master FAB's corner radius, and the shape built from it.
 *
 * Restated rather than read from `FloatingActionButtonDefaults.shape` because the panel's own radius
 * is derived from it ([RENTAL_SURFACE_CORNER]) and a `Shape` can't be measured back into dp without a
 * density. Both the button and the panel resolve from this one value, so they cannot drift apart —
 * the cost is that it mirrors M3's `CornerLarge` by hand, and would need updating if that token moved.
 */
private val RENTAL_FAB_CORNER = 16.dp
private val RENTAL_FAB_SHAPE = RoundedCornerShape(RENTAL_FAB_CORNER)

/**
 * The panel's vertical inset, and the gap between the buttons it stacks.
 *
 * The sides are tighter ([RENTAL_SURFACE_PADDING_HORIZONTAL]): the buttons are round and the panel is
 * nearly a capsule, so equal padding all round leaves the sides *looking* slacker than the ends even
 * when it measures the same.
 */
private val RENTAL_SURFACE_PADDING = 8.dp

/** The panel's side inset. Also what [MapChrome] takes back out of the margin to align the two FABs. */
private val RENTAL_SURFACE_PADDING_HORIZONTAL = 6.dp

/**
 * The panel's corner radius, concentric with the FAB inside it: the button's radius plus the gap
 * between them, so the two curves stay parallel instead of the outer one running tighter or slacker
 * than the shape it wraps.
 *
 * Concentric with the FAB's **side** gap specifically. The vertical inset is 2dp larger, so the panel's
 * bottom corners are strictly parallel only along the sides — equalising the two paddings is what it
 * would take to make that exact, and the asymmetry is deliberate (see [RENTAL_SURFACE_PADDING]).
 */
private val RENTAL_SURFACE_CORNER = RENTAL_FAB_CORNER + RENTAL_SURFACE_PADDING_HORIZONTAL

private val RENTAL_SURFACE_ELEVATION = 6.dp

// The my-location FAB uses @dimen/fab_margin_*; the rental control stacks above it, clearing it by
// RENTAL_FAB_GAP. The legacy layout hardcoded the whole distance as 80dp with no dimen, which left the
// two buttons flush once the rental panel gained its own padding — hence deriving it instead.

/** M3's standard FAB diameter, which the my-location button is. Not exposed as a token by Material 3. */
private val FAB_SIZE = 56.dp

/** Clear air between the rental control and the my-location FAB beneath it. */
private val RENTAL_FAB_GAP = 16.dp
