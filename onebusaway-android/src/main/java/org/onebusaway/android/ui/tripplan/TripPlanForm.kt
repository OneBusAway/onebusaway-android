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
package org.onebusaway.android.ui.tripplan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.icons.AppIcons
import org.onebusaway.android.ui.tutorial.LocalTutorialState
import org.onebusaway.android.ui.tutorial.ScriptedTutorial
import org.onebusaway.android.ui.tutorial.tutorialAnchor

/** Height of one endpoint row. Android's minimum touch target — the rows are tap-to-edit. */
private val ENDPOINT_ROW_HEIGHT = 48.dp

/**
 * Material's content keyline: where the card's content begins, in both bands — the endpoint dot's
 * leading edge above, the "when" sentence's first character below. Written once and derived from,
 * rather than restated per band, for the same reason [TRAILING_GUTTER] is: two bands that lay
 * themselves out independently land on one keyline only if the keyline itself is one value.
 */
private val CONTENT_KEYLINE = 16.dp

/** Diameter of the endpoint dot, in the rail and in the suggestion row that fills that endpoint. */
private val ENDPOINT_DOT_SIZE = 12.dp

/**
 * Width of the leading rail — the dot centred in it, which is what puts its leading edge on
 * [CONTENT_KEYLINE]. Reused as the dividers' inset, so the hairline starts where the text does.
 */
private val RAIL_WIDTH = CONTENT_KEYLINE * 2 + ENDPOINT_DOT_SIZE

/** The box the endpoint dot occupies when it stands in a menu row's leading icon slot. */
private val ENDPOINT_DOT_ICON_SIZE = 22.dp

/** Clear space between an endpoint glyph and the dotted connector running to the other one. */
private val CONNECTOR_GLYPH_CLEARANCE = 12.dp

/**
 * Side of every icon button in the form, at both bands. Load-bearing beyond its own size: paired with
 * [TRAILING_GUTTER] it's what puts the reverse button in the same column as the action bar's buttons.
 */
private val ICON_BUTTON_SIZE = 40.dp

/**
 * Height of the action bar — snug around its icon buttons, which keep a full 48dp touch target
 * regardless, because Material expands it beyond their bounds.
 */
private val ACTION_BAR_HEIGHT = ICON_BUTTON_SIZE

/** [SegmentButton]'s own horizontal padding, held apart because [ACTION_BAR_START_INSET] subtracts it. */
private val SEGMENT_TEXT_INSET = 6.dp

/**
 * Where the action bar's content starts, now that the bar carries no leading glyph of its own (#2135).
 *
 * The band above opens with [RAIL_WIDTH] of endpoint rail; this one opens with the "when" sentence, so
 * it reaches [CONTENT_KEYLINE] by padding — less the padding [SegmentButton] already applies, since it
 * is the sentence's *text* that belongs on the keyline and not its press surface. The rest of what the
 * retired glyph gives back is what pays for the refresh button.
 */
private val ACTION_BAR_START_INSET = CONTENT_KEYLINE - SEGMENT_TEXT_INSET

/**
 * Gap between the card's trailing edge and the icon buttons against it. The trailing counterpart of
 * [RAIL_WIDTH], and shared by both bands for the same reason: so their buttons line up in one column.
 * Applied through [formBand] rather than by hand, so there is one gutter and not two that agree.
 */
private val TRAILING_GUTTER = 4.dp

/** Clear space between the endpoint rows' content and the reverse button beside them. */
private val REVERSE_CLEARANCE = 4.dp

/**
 * The shape the card's two bands share: full width, inset at the trailing edge by [TRAILING_GUTTER].
 * One expression rather than a gutter restated per band, because — paired with a single
 * [ICON_BUTTON_SIZE] — it is the whole reason the reverse button lands in the same column as the
 * action bar's buttons. Two bands that lay themselves out independently agree on that only if the
 * measurement they agree on is written once.
 */
private fun Modifier.formBand() = fillMaxWidth().padding(end = TRAILING_GUTTER)

/** Rider-facing order for the two mode menus; intentionally independent of enum declaration order. */
private val VEHICLE_MODE_ORDER = listOf(
    VehicleMode.ALL_TRANSIT,
    VehicleMode.BUS,
    VehicleMode.RAIL,
    VehicleMode.NONE
)
private val STREET_MODE_ORDER = listOf(
    StreetMode.WALK,
    StreetMode.BICYCLE,
    StreetMode.WALK_AND_BIKESHARE
)

/**
 * Stable UIAutomator/Compose-test handles for the trip-plan form. Surfaced as resource-ids by the
 * app-wide `testTagsAsResourceId` in HomeActivity, so the form can be driven semantically (focus a
 * field, tap a suggestion) without coordinate taps. The per-endpoint tags are `<prefix><suffix>`,
 * e.g. `tripPlanFromField`, `tripPlanToSuggestion`.
 */
object TripPlanTestTags {
    const val FROM_PREFIX = "tripPlanFrom"
    const val TO_PREFIX = "tripPlanTo"
    const val FIELD_SUFFIX = "Field"
    const val SUGGESTION_SUFFIX = "Suggestion"

    /** The two pinned rows at the head of a field's suggestion list. */
    const val MY_LOCATION_SUFFIX = "MyLocation"
    const val PICK_ON_MAP_SUFFIX = "PickOnMap"

    /** The action bar's two time segments, and its two mode pickers. */
    const val WHEN_MODE = "tripPlanWhenMode"
    const val WHEN_TIME = "tripPlanWhenTime"
    const val VEHICLE_MODE = "tripPlanVehicleMode"
    const val STREET_MODE = "tripPlanStreetMode"

    /** The pinned instant, stated in full at the head of the time segment's menu. */
    const val WHEN_TIME_HEADER = "tripPlanWhenTimeHeader"

    /** The swap-endpoints button. */
    const val REVERSE = "tripPlanReverse"

    /** The action bar's re-plan-this-same-trip button. */
    const val REFRESH = "tripPlanRefresh"

    /** The action bar's trailing button, which reverse is column-aligned with. */
    const val ADVANCED_SETTINGS = "tripPlanAdvancedSettings"

    /** The day dropdown inside the combined date/time picker ([TripDateTimeDialog]). */
    const val PICKER_DAY = "tripPlanPickerDay"
}

/**
 * What an empty endpoint field invites the rider to do. A placeholder rather than a label: the
 * compact form has no room for a floating label above each field, and the rail glyph already says
 * which end of the trip the row is.
 *
 * Lives here rather than on [TripEndpointSlot] itself to keep [TripPlanFormState]'s file free of
 * Android — the same seam [TripEndpoint.displayText] already draws for the fixed-label endpoint kinds.
 */
@get:StringRes
val TripEndpointSlot.placeholderRes: Int
    get() = when (this) {
        TripEndpointSlot.FROM -> R.string.trip_plan_from_hint
        TripEndpointSlot.TO -> R.string.trip_plan_to_hint
    }

/** The [TripPlanTestTags] prefix naming this endpoint's field. */
val TripEndpointSlot.tagPrefix: String
    get() = when (this) {
        TripEndpointSlot.FROM -> TripPlanTestTags.FROM_PREFIX
        TripEndpointSlot.TO -> TripPlanTestTags.TO_PREFIX
    }

/**
 * The trip-plan form: two endpoint rows over a single action bar (#2094).
 *
 * The layout is deliberately close to what a rider already knows from other mapping apps — a leading
 * rail whose glyph says what each endpoint *is*, two borderless fields separated by a hairline, and no
 * per-field chrome. What differs is the bottom band: transit is scheduled travel, so the form always
 * states when the trip is for, as a "Depart · now" callout whose two halves open separate pickers.
 *
 * That band doubles as the form's action bar, carrying the mode pickers and additional-preferences as
 * well. It's the reason the endpoint rows carry no per-field chrome: everything acting on the trip's
 * *terms* lives on one line, and nothing acts on a single endpoint (see [EndpointRow]). The one action
 * on the endpoints themselves — reverse — sits beside the pair, centred on the divider between them.
 * The card measures 146dp against the old layout's 216dp.
 *
 * Stateless and driven by [TripPlanFormState]; the date/time/current-location actions are platform
 * interactions launched by the host.
 */
@Composable
fun TripPlanForm(
    state: TripPlanFormState,
    onQueryChange: (TripEndpointSlot, String) -> Unit,
    onSelect: (TripEndpointSlot, TripEndpoint.Geocoded) -> Unit,
    onCurrentLocation: (TripEndpointSlot) -> Unit,
    onPickOnMap: (TripEndpointSlot) -> Unit,
    onSetArriving: (Boolean) -> Unit,
    onDepartNow: () -> Unit,
    onPickDateTime: () -> Unit,
    availableStreetModes: List<StreetMode>,
    onVehicleModeSelected: (VehicleMode) -> Unit,
    onStreetModeSelected: (StreetMode) -> Unit,
    onReverse: () -> Unit,
    onRefresh: () -> Unit,
    onAdvancedSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.formBand(), verticalAlignment = Alignment.CenterVertically) {
            // The rows take the width the button doesn't, rather than running full width beneath it —
            // which is what stops a long place name, and the divider, from sliding under the button.
            // Centring the sibling button against the resulting block lands it on that divider.
            Column(Modifier.weight(1f)) {
                // One row per endpoint, in TripEndpointSlot's declaration order (origin above
                // destination) — the enum is the list of rows.
                TripEndpointSlot.entries.forEachIndexed { index, slot ->
                    if (index > 0) {
                        // Inset past the rail so the hairline starts where the text does, leaving the
                        // origin and destination glyphs reading as one continuous column.
                        HairlineDivider(startIndent = RAIL_WIDTH, endIndent = REVERSE_CLEARANCE)
                    }
                    EndpointRow(
                        slot = slot,
                        endpoint = state.endpointAt(slot),
                        suggestions = state.suggestionsAt(slot),
                        onQueryChange = { onQueryChange(slot, it) },
                        onSelect = { onSelect(slot, it) },
                        onCurrentLocation = { onCurrentLocation(slot) },
                        onPickOnMap = { onPickOnMap(slot) }
                    )
                }
            }
            FormIconButton(
                painter = painterResource(R.drawable.ic_swap_direction),
                contentDescription = stringResource(R.string.tripplanner_reverse),
                onClick = onReverse,
                modifier = Modifier.testTag(TripPlanTestTags.REVERSE)
            )
        }
        // Full-width, unlike the one above: this one separates the endpoints from the actions, rather
        // than separating two members of the same group.
        HairlineDivider()
        TripActionBar(
            arriving = state.arriving,
            departNow = state.departNow,
            dateLabel = state.dateLabel,
            timeLabel = state.timeLabel,
            dayRelation = state.dayRelation,
            modes = state.modes,
            availableStreetModes = availableStreetModes,
            canRefresh = state.canSubmit,
            onSetArriving = onSetArriving,
            onDepartNow = onDepartNow,
            onPickDateTime = onPickDateTime,
            onVehicleModeSelected = onVehicleModeSelected,
            onStreetModeSelected = onStreetModeSelected,
            onRefresh = onRefresh,
            onAdvancedSettings = onAdvancedSettings
        )
    }
}

/**
 * The form's one icon-button shape: fixed size, muted tint, the glyph as its own label. Shared rather
 * than repeated, because reverse now sits in a different band from the rest — the two bands read as one
 * set of controls only if nothing about the buttons is restated per site and free to drift.
 */
@Composable
private fun FormIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // The tint travels as the button's content colour rather than being set on the Icon, so the
        // disabled case comes from Material's own token instead of an alpha restated here.
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier.size(ICON_BUTTON_SIZE)
    ) {
        Icon(painter = painter, contentDescription = contentDescription)
    }
}

/** A 1dp rule at the outline colour, optionally inset at either end. */
@Composable
private fun HairlineDivider(startIndent: Dp = 0.dp, endIndent: Dp = 0.dp) {
    HorizontalDivider(Modifier.padding(start = startIndent, end = endIndent))
}

/**
 * One trip-plan endpoint: a rail glyph naming the endpoint's kind, and the place itself in a
 * borderless field. There is no pill and no trailing button, which is what lets the row give its whole
 * width to the place name; the actions that used to sit here are in the suggestion list and the action
 * bar.
 *
 * The row is one persistent text field in every state — a resolved endpoint is the same field showing
 * a name, not a different kind of row. That matters beyond tidiness: swapping a read-only Text in and
 * out for a text field destroys and recreates the field on each transition, and the focus callbacks
 * and IME teardown that come out of that are indistinguishable from real user edits. Both of this
 * screen's early defects (a dropdown that flashed open and shut, and "Your location" resolving only
 * on alternate taps) were that same swap seen from two angles.
 *
 * Likewise [field] is the single authority on the text. Feeding `BasicTextField` a `TextFieldValue`
 * rebuilt during composition, rather than the one it last handed back, fights its internal state and
 * produces echoed edits; the hosted endpoint is adopted only when it genuinely differs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointRow(
    slot: TripEndpointSlot,
    endpoint: TripEndpoint,
    suggestions: List<TripEndpoint.Geocoded>,
    onQueryChange: (String) -> Unit,
    onSelect: (TripEndpoint.Geocoded) -> Unit,
    onCurrentLocation: () -> Unit,
    onPickOnMap: () -> Unit
) {
    val tagPrefix = slot.tagPrefix
    // The endpoint as the field should read it. A resolved endpoint is not a different *kind* of row
    // — it is the same field showing a name — so nothing is swapped in or out as it resolves.
    val endpointText = endpointLabel(endpoint)

    // One authority for the field's contents. BasicTextField's TextFieldValue overload requires the
    // caller to hold the value it hands back and return exactly that; synthesising a fresh value each
    // recomposition fights the field's own state and makes the IME echo edits the user never made.
    var field by remember { mutableStateOf(TextFieldValue(endpointText)) }
    var menuOpen by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Choosing any row of the menu below names the endpoint, so it ends the edit rather than being a
    // step in it: the field gives up focus and the keyboard goes with it. Hung off the choice rather
    // than off the row's state, because focus is one resource shared with the sibling row — "this row
    // isn't being edited" is also true the instant the other row takes focus, and reconciling to that
    // would pull the keyboard down mid-handoff. Wrapping the action keeps a later row from forgetting.
    fun choosing(action: () -> Unit): () -> Unit = {
        menuOpen = false
        // clearFocus() is what closes the keyboard the field raised; hide() is a cheap backstop.
        focusManager.clearFocus()
        keyboard?.hide()
        action()
    }

    // The keyboard's own accept key is a choice like any other: by the time it is pressed the geocoder
    // has already answered, and the rider is confirming the result they can see at the top of the list
    // rather than asking for it. With nothing to confirm it just ends the edit — which is all it did
    // before, having been wired to nothing at all.
    val acceptTopSuggestion = choosing { suggestions.firstOrNull()?.let(onSelect) }

    // Adopt the hosted endpoint only when it actually says something different — a suggestion picked,
    // a location filled in, a reversed trip. While the user types, the endpoint is echoing the text
    // that came from this field, so there is nothing to adopt and the cursor is left alone.
    LaunchedEffect(endpointText) {
        if (endpointText != field.text) {
            field = TextFieldValue(endpointText, selection = TextRange(endpointText.length))
        }
    }

    ExposedDropdownMenuBox(
        expanded = menuOpen,
        onExpandedChange = { menuOpen = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ENDPOINT_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EndpointRail(endpoint = endpoint, slot = slot)
            BasicTextField(
                value = field,
                onValueChange = { value ->
                    // A TextFieldValue changes for selection and cursor moves as well as for edits;
                    // only an edit is a new search query.
                    val edited = value.text != field.text
                    field = value
                    if (edited) {
                        onQueryChange(value.text)
                        menuOpen = true
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { acceptTopSuggestion() }),
                // The device's own position is named in its own colour, matching its rail dot, so the
                // one endpoint the rider didn't choose is identifiable without reading it. A chosen
                // place is ordinary text — colouring every endpoint would say nothing.
                textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
                    .copy(
                        color = if (endpoint is TripEndpoint.CurrentLocation) {
                            endpointColor(endpoint, slot)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    // Full row height, so the tap target is the whole 48dp band rather than the ~20dp
                    // the text line occupies.
                    .fillMaxHeight()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            // Select the whole name, so typing replaces the place rather than appending
                            // to it, and offer the actions straight away rather than after a keystroke.
                            field = field.copy(selection = TextRange(0, field.text.length))
                            menuOpen = true
                        } else {
                            menuOpen = false
                        }
                    }
                    .testTag(tagPrefix + TripPlanTestTags.FIELD_SUFFIX),
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) PlaceholderText(stringResource(slot.placeholderRes))
                        innerField()
                    }
                }
            )
            Spacer(Modifier.width(REVERSE_CLEARANCE))
        }

        // The two ways to fill an endpoint that aren't typing. They used to be permanent icon buttons on
        // every field — four of them across the card, its loudest chrome. Here they cost nothing until
        // the rider is actually choosing a place, which is the only moment they mean anything.
        ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tripplanner_current_location)) },
                leadingIcon = { CurrentLocationDotIcon() },
                onClick = choosing(onCurrentLocation),
                modifier = Modifier.testTag(tagPrefix + TripPlanTestTags.MY_LOCATION_SUFFIX)
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trip_plan_pick_on_map)) },
                // The crosshair the pick overlay itself puts at the centre of the map, so the row
                // shows the tool it opens.
                leadingIcon = { PinnedActionIcon(painterResource(R.drawable.ic_my_location)) },
                onClick = choosing(onPickOnMap),
                modifier = Modifier.testTag(tagPrefix + TripPlanTestTags.PICK_ON_MAP_SUFFIX)
            )
            if (suggestions.isNotEmpty()) {
                HairlineDivider()
                suggestions.forEach { place ->
                    DropdownMenuItem(
                        text = { Text(place.displayName) },
                        leadingIcon = if (place.isTransit) {
                            { BusIcon() }
                        } else {
                            null
                        },
                        onClick = choosing { onSelect(place) },
                        modifier = Modifier.testTag(tagPrefix + TripPlanTestTags.SUGGESTION_SUFFIX)
                    )
                }
            }
        }
    }
}

/**
 * The colour that says which end of the trip an endpoint is: the theme's primary for the origin
 * (green in the OBA brand, whatever a white-label brand sets) and the fixed red for the destination.
 *
 * Public because the rail is not the only place a rider names an endpoint — the map long-press menu
 * offers the same two ends, and marks its rows with these same hues so the row and the endpoint it
 * fills read as one thing.
 */
@Composable
fun endpointSlotColor(slot: TripEndpointSlot): Color = when (slot) {
    TripEndpointSlot.FROM -> MaterialTheme.colorScheme.primary
    TripEndpointSlot.TO -> colorResource(R.color.trip_destination_marker)
}

/**
 * The colour that identifies an endpoint. Blue wherever the trip is anchored to the device's own
 * position, at either end — that is a different kind of fact from a chosen place, so it outranks the
 * start/destination distinction rather than sitting beside it. Otherwise it is the slot's own colour.
 */
@Composable
private fun endpointColor(endpoint: TripEndpoint, slot: TripEndpointSlot): Color = when (endpoint) {
    is TripEndpoint.CurrentLocation -> colorResource(R.color.trip_plan_endpoint_current_location)
    else -> endpointSlotColor(slot)
}

/**
 * The leading glyph column: a dot in the endpoint's own colour, so origin, destination and "my
 * position" are told apart by hue rather than by shape. A transit endpoint keeps the bus glyph
 * instead — that it is a stop rather than an address is something no colour in the set encodes.
 */
@Composable
private fun EndpointRail(endpoint: TripEndpoint, slot: TripEndpointSlot) {
    val isOrigin = slot == TripEndpointSlot.FROM
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier.width(RAIL_WIDTH).height(ENDPOINT_ROW_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        // Half of the dotted connector between the two glyphs, drawn from this row's glyph to the edge
        // it shares with the other row. The two halves meet because the divider above is inset past the
        // rail, so nothing crosses the gap. This is what makes origin and destination read as two ends
        // of one trip rather than two unrelated fields.
        Box(
            Modifier.matchParentSize().drawWithCache {
                // Built once per size/density rather than per draw: a dash effect allocates a native
                // DashPathEffect, and this rail repaints with the whole card — on every keystroke and
                // every blink of the text cursor — for a line that never changes.
                val dashes = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                val gap = CONNECTOR_GLYPH_CLEARANCE.toPx()
                val midY = size.height / 2
                val start = if (isOrigin) midY + gap else 0f
                val end = if (isOrigin) size.height else midY - gap
                onDrawBehind {
                    if (end <= start) return@onDrawBehind
                    drawLine(
                        color = connectorColor,
                        start = Offset(size.width / 2, start),
                        end = Offset(size.width / 2, end),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = dashes
                    )
                }
            }
        )
        if (endpoint.isTransit) {
            BusIcon()
        } else {
            EndpointDot(endpointColor(endpoint, slot))
        }
    }
}

/** The filled circle that marks an endpoint, in the colour [endpointColor] gives it. */
@Composable
private fun EndpointDot(color: Color) {
    Box(Modifier.size(ENDPOINT_DOT_SIZE).background(color, CircleShape))
}

/**
 * The dot in a menu row's leading icon slot. Every row that *offers* to fill an endpoint shows the
 * mark that endpoint will carry once filled, rather than a separate glyph that turns into a dot on
 * being chosen.
 */
@Composable
private fun EndpointDotIcon(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(ENDPOINT_DOT_ICON_SIZE), contentAlignment = Alignment.Center) {
        EndpointDot(color)
    }
}

/** [EndpointDotIcon] for the suggestion row that fills an endpoint with the device's position. */
@Composable
private fun CurrentLocationDotIcon() {
    EndpointDotIcon(colorResource(R.color.trip_plan_endpoint_current_location))
}

/**
 * [EndpointDotIcon] for a row elsewhere that offers to fill one named end of the trip — the map
 * long-press menu's "directions from/to here" (#2112). Takes a [modifier] rather than tagging
 * itself: the tag belongs to whichever feature draws the row, since more than one of them can be
 * on screen at once.
 */
@Composable
fun TripEndpointDotIcon(slot: TripEndpointSlot, modifier: Modifier = Modifier) {
    EndpointDotIcon(endpointSlotColor(slot), modifier)
}

@Composable
private fun PlaceholderText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PinnedActionIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
    )
}

/**
 * The bottom band: when the trip is for, and how it may be travelled.
 *
 * The time reads as one sentence — "Depart · now" — split into two separately-tappable segments, each
 * opening its own menu. The mode pickers, refresh and additional preferences sit at the trailing edge;
 * reverse is not here, because it acts on the two endpoints rather than on the trip's terms, and so
 * lives beside them (#2110).
 *
 * The bar opened with a clock glyph in a [RAIL_WIDTH] rail until #2135. It said nothing the sentence
 * beside it didn't already say in words, and the bar had no width to spare for the refresh button, so
 * it is the glyph that went — see [ACTION_BAR_START_INSET] for what keeps the sentence on its keyline.
 */
@Composable
private fun TripActionBar(
    arriving: Boolean,
    departNow: Boolean,
    dateLabel: String,
    timeLabel: String,
    dayRelation: TripDay,
    modes: TripModeSelection,
    availableStreetModes: List<StreetMode>,
    canRefresh: Boolean,
    onSetArriving: (Boolean) -> Unit,
    onDepartNow: () -> Unit,
    onPickDateTime: () -> Unit,
    onVehicleModeSelected: (VehicleMode) -> Unit,
    onStreetModeSelected: (StreetMode) -> Unit,
    onRefresh: () -> Unit,
    onAdvancedSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .formBand()
            .padding(start = ACTION_BAR_START_INSET)
            .height(ACTION_BAR_HEIGHT)
            // The scripted tour's "narrow it down" step rings this row (#2164) — the when/mode/advanced
            // controls, not the endpoints above them.
            .tutorialAnchor(LocalTutorialState.current, ScriptedTutorial.KEY_TRIP_OPTIONS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhenModeSegment(arriving = arriving, onSetArriving = onSetArriving)
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WhenTimeSegment(
            // The bar's one flexible slot, and the only part of it that can grow: a pinned label is a
            // full date and time, longer in other locales and at a large font scale. Taking all the
            // remaining width does both jobs — it holds the trailing buttons against the right
            // edge, and it caps the label so it ellipsizes rather than shoving them off. The button
            // inside stays its own width at the slot's start, so the tap target doesn't span the gap.
            modifier = Modifier.weight(1f),
            departNow = departNow,
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            dayRelation = dayRelation,
            onDepartNow = onDepartNow,
            onPickDateTime = onPickDateTime
        )
        ModePicker(
            selected = modes.vehicle,
            options = VEHICLE_MODE_ORDER,
            icon = { vehicleModeIcon(it) },
            label = { vehicleModeLabel(it) },
            testTag = TripPlanTestTags.VEHICLE_MODE,
            onSelected = onVehicleModeSelected
        )
        ModePicker(
            // Display-only substitution, deliberately not written back to the selection: a street mode
            // this region can't serve degrades on the way to a request
            // ([TripModeSelection.availableIn]) and never by rewriting what the rider chose, so a
            // rider who picks their own bike at home still has it after passing through an OTP1
            // region. Showing walking here matches the trip that will actually be planned.
            selected = modes.street.takeIf { it in availableStreetModes } ?: StreetMode.WALK,
            options = STREET_MODE_ORDER.filter { it in availableStreetModes },
            icon = { streetModeIcon(it) },
            label = { streetModeLabel(it) },
            testTag = TripPlanTestTags.STREET_MODE,
            onSelected = onStreetModeSelected
        )
        FormIconButton(
            painter = painterResource(R.drawable.ic_action_navigation_refresh),
            contentDescription = stringResource(R.string.trip_plan_refresh),
            onClick = onRefresh,
            // Nothing to re-plan until the form names both ends of a trip. Disabled rather than absent,
            // so the bar's trailing buttons don't shuffle sideways as the rider fills the form in.
            enabled = canRefresh,
            modifier = Modifier.testTag(TripPlanTestTags.REFRESH)
        )
        FormIconButton(
            painter = rememberVectorPainter(AppIcons.Settings),
            contentDescription = stringResource(R.string.trip_plan_advanced_settings),
            onClick = onAdvancedSettings,
            modifier = Modifier.testTag(TripPlanTestTags.ADVANCED_SETTINGS)
        )
    }
}

/** A selected mode glyph that expands to icon-and-label choices, then collapses again. */
@Composable
private fun <T> ModePicker(
    selected: T,
    options: List<T>,
    icon: @Composable (T) -> Int,
    label: @Composable (T) -> String,
    testTag: String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = label(selected)
    Box {
        FormIconButton(
            painter = painterResource(icon(selected)),
            contentDescription = selectedLabel,
            onClick = { expanded = true },
            modifier = Modifier.testTag(testTag)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(icon(option)),
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(AppIcons.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun vehicleModeIcon(mode: VehicleMode): Int = when (mode) {
    VehicleMode.ALL_TRANSIT -> R.drawable.ic_bus_railway
    VehicleMode.BUS -> R.drawable.ic_directions_bus
    VehicleMode.RAIL -> R.drawable.ic_train
    VehicleMode.NONE -> R.drawable.ic_no_transfer
}

@Composable
private fun vehicleModeLabel(mode: VehicleMode): String = stringResource(
    when (mode) {
        VehicleMode.ALL_TRANSIT -> R.string.vehicle_mode_all_transit
        VehicleMode.BUS -> R.string.vehicle_mode_bus
        VehicleMode.RAIL -> R.string.vehicle_mode_rail
        VehicleMode.NONE -> R.string.vehicle_mode_none
    }
)

private fun streetModeIcon(mode: StreetMode): Int = when (mode) {
    StreetMode.WALK -> R.drawable.ic_directions_walk
    StreetMode.BICYCLE -> R.drawable.ic_directions_bike
    StreetMode.WALK_AND_BIKESHARE -> R.drawable.ic_bike_rental
}

@Composable
private fun streetModeLabel(mode: StreetMode): String = stringResource(
    when (mode) {
        StreetMode.WALK -> R.string.street_mode_walk
        StreetMode.BICYCLE -> R.string.street_mode_bicycle
        StreetMode.WALK_AND_BIKESHARE -> R.string.street_mode_walk_and_bikeshare
    }
)

/** Leaving vs arriving — the first half of the "when" sentence. */
@Composable
private fun WhenModeSegment(arriving: Boolean, onSetArriving: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val leaving = stringResource(R.string.trip_plan_leaving)
    val arrivingLabel = stringResource(R.string.trip_plan_arriving)
    Box {
        SegmentButton(
            text = if (arriving) arrivingLabel else leaving,
            emphasized = true,
            testTag = TripPlanTestTags.WHEN_MODE,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(leaving) }, onClick = {
                expanded = false
                onSetArriving(false)
            })
            DropdownMenuItem(text = { Text(arrivingLabel) }, onClick = {
                expanded = false
                onSetArriving(true)
            })
        }
    }
}

/**
 * When the trip is for — the second half of the sentence. Reads "now" until an instant is pinned, then
 * that instant. The menu is the two answers a rider actually has — leave now, or say when — with the
 * date and the time settled together in one dialog ([TripDateTimeDialog], #2117) rather than as two
 * menu rows that each set the whole instant from one of its halves.
 */
@Composable
private fun WhenTimeSegment(
    modifier: Modifier = Modifier,
    departNow: Boolean,
    dateLabel: String,
    timeLabel: String,
    dayRelation: TripDay,
    onDepartNow: () -> Unit,
    onPickDateTime: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val nowLabel = stringResource(R.string.trip_plan_now)
    Box(modifier) {
        SegmentButton(
            text = if (departNow) nowLabel else whenLabel(dayRelation, dateLabel, timeLabel),
            emphasized = false,
            testTag = TripPlanTestTags.WHEN_TIME,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (!departNow) {
                // The pinned instant in full, stated once at the head of the menu. The callout is the
                // action bar's one elastic slot and routinely ellipsizes — its abbreviated label is
                // what fits, not necessarily what the rider needs to check — so the menu it opens is
                // where the whole date and time are legible. A "now" trip has no such instant to state.
                Text(
                    text = stringResource(R.string.trip_plan_date_time, dateLabel, timeLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag(TripPlanTestTags.WHEN_TIME_HEADER)
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(nowLabel) },
                trailingIcon = if (departNow) {
                    {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    onDepartNow()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trip_plan_choose_date_time)) },
                trailingIcon = if (!departNow) {
                    {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    onPickDateTime()
                }
            )
        }
    }
}

/**
 * A pinned instant as the callout states it (#2185): the time first, then the day — but only when the
 * day needs saying at all. Most pinned trips are for later today, where the date was pure noise
 * crowding out the one part the rider is reading for; tomorrow has a word, so it gets the word; and
 * anything further out falls back to its date. The full instant is still one tap away, at the head of
 * the menu this segment opens.
 */
@Composable
private fun whenLabel(dayRelation: TripDay, dateLabel: String, timeLabel: String): String = when (dayRelation) {
    TripDay.TODAY -> timeLabel
    TripDay.TOMORROW -> stringResource(R.string.trip_plan_time_tomorrow, timeLabel)
    TripDay.OTHER -> stringResource(R.string.trip_plan_time_date, timeLabel, dateLabel)
}

/** One tappable half of the "when" sentence: text plus a small chevron, on a rounded press surface. */
@Composable
private fun SegmentButton(
    text: String,
    emphasized: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(32.dp)
            // The value is the label, so TalkBack reads it as-is; the click label supplies the verb the
            // bare text can't ("Depart" alone doesn't say it's changeable).
            .clickable(onClickLabel = stringResource(R.string.trip_plan_change_when), onClick = onClick)
            .padding(horizontal = SEGMENT_TEXT_INSET)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            imageVector = AppIcons.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).clearAndSetSemantics {}
        )
    }
}

/** The user-visible label for an endpoint; fixed kinds resolve a string resource. */
@Composable
private fun endpointLabel(endpoint: TripEndpoint): String = endpoint.displayText ?: stringResource(endpoint.fixedLabelRes())

/**
 * The string resource naming an endpoint that carries no text of its own — the two fixed-label kinds.
 * A pure rule rather than a `when` inside a composable, so the pinned-trip card
 * ([org.onebusaway.android.ui.tripplan.pinned.pinnedDestinationLabel]) names the same place the form
 * does without either restating it.
 */
@StringRes
internal fun TripEndpoint.fixedLabelRes(): Int = when (this) {
    is TripEndpoint.MapPoint -> R.string.trip_plan_map_location
    // Only the fixed-label kinds (CurrentLocation/MapPoint) have a null displayText.
    else -> R.string.tripplanner_current_location
}

@Composable
private fun BusIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_bus),
        contentDescription = null,
        tint = colorResource(R.color.material_gray)
    )
}

@Preview(showBackground = true)
@Composable
private fun TripPlanFormPreview() {
    ObaTheme {
        TripPlanForm(
            state = TripPlanFormState(
                from = TripEndpoint.CurrentLocation(lat = 47.6, lon = -122.3),
                to = TripEndpoint.Geocoded("Pike Place Market", lat = 47.6, lon = -122.34),
                dateTimeMillis = 0L,
                departNow = true,
                dateLabel = "June 10",
                timeLabel = "3:45 PM"
            ),
            onQueryChange = { _, _ -> }, onSelect = { _, _ -> },
            onCurrentLocation = {}, onPickOnMap = {},
            onSetArriving = {}, onDepartNow = {},
            onPickDateTime = {},
            availableStreetModes = StreetMode.entries,
            onVehicleModeSelected = {}, onStreetModeSelected = {},
            onReverse = {}, onRefresh = {}, onAdvancedSettings = {}
        )
    }
}
