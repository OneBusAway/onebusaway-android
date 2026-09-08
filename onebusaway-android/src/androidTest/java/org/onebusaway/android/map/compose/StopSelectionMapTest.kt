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
package org.onebusaway.android.map.compose

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.adapters.ObaStopElement
import org.onebusaway.android.map.RouteStopPresentation
import org.onebusaway.android.map.applyRouteStopPresentation
import org.onebusaway.android.map.render.MapProjector
import org.onebusaway.android.map.render.MapRenderState
import org.onebusaway.android.map.render.ScreenOffset
import org.onebusaway.android.map.render.StopMarker
import org.onebusaway.android.models.RouteDirectionKey
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.util.GeoPoint

class StopSelectionMapTest {
    @get:Rule val composeRule = createUnconfinedComposeRule()
    private val selected = mutableListOf<String>()
    private lateinit var deliveredCallbacks: ObaMapCallbacks
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun insetNativeMarkerTapUsesRootCoordinatesExactlyOnce() {
        render(enabled = true, nativeMarkerTap = true)
        tapMap()
        assertCorrectChoices()
        composeRule.onNodeWithText("Nearby").performClick()
        assertEquals(listOf("2"), selected)
    }

    @Test
    fun insetEmptyMapTapUsesTheSameRootTouchTarget() {
        render(enabled = true, nativeMarkerTap = false)
        tapMap()
        assertCorrectChoices()
    }

    @Test
    fun westboundFocusStillOffersAndSelectsTheNearbyEastboundStop() {
        render(enabled = true, stopFocused = true)
        tapMap()
        assertCorrectChoices()
        composeRule.onNodeWithText(context.getString(R.string.map_stop_currently_selected)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.stop_details_code, "2") + " · " + context.getString(R.string.direction_e)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Nearby").performClick()
        assertEquals(listOf("2"), selected)
        composeRule.onNodeWithText(context.getString(R.string.map_choose_stop)).assertDoesNotExist()
    }

    @Test
    fun coordinatePickerPassesThroughCallbacksWithoutOpeningAChooser() {
        render()
        composeRule.runOnIdle { assertSame(NoOpObaMapCallbacks, deliveredCallbacks) }
        tapMap()
        composeRule.onNodeWithText(context.getString(R.string.map_choose_stop)).assertDoesNotExist()
        composeRule.runOnIdle { deliveredCallbacks.onMapClick(GeoPoint(1.0, 1.0)) }
        composeRule.onNodeWithText(context.getString(R.string.map_choose_stop)).assertDoesNotExist()
        assertEquals(emptyList<String>(), selected)
    }

    private fun tapMap() = composeRule.onNodeWithTag("map").performTouchInput { click(center) }

    private fun assertCorrectChoices() {
        composeRule.onNodeWithText("Native").assertIsDisplayed()
        composeRule.onNodeWithText("Nearby").assertIsDisplayed()
        composeRule.onNodeWithText("Below the tap").assertDoesNotExist()
    }

    private fun render(enabled: Boolean = false, nativeMarkerTap: Boolean = true, stopFocused: Boolean = false) {
        val native = stop("1", "Native", "W")
        val nearby = stop("2", "Nearby", "E")
        val below = stop("3", "Below the tap")
        val stops = listOf(native, nearby, below)
        val state = MapRenderState().apply {
            if (stopFocused) setFocusedStopId(native.id)
            setStops(
                if (stopFocused) {
                    applyRouteStopPresentation(
                        stops,
                        native.id,
                        RouteStopPresentation(
                            stops = listOf(native.stop),
                            routes = emptyList(),
                            routeDirectionsByStopId = mapOf(native.id to setOf(RouteDirectionKey("11", 0))),
                            keepNearbyStops = true
                        ),
                        markerFor = { stop -> stops.single { it.id == stop.id } }
                    )
                } else {
                    stops
                }
            )
        }
        val callbacks = if (enabled) {
            object : ObaMapCallbacks by NoOpObaMapCallbacks {
                override fun onStopClick(marker: StopMarker) {
                    selected += marker.id
                }
            }
        } else {
            NoOpObaMapCallbacks
        }
        composeRule.setContent {
            ObaTheme {
                Column {
                    // Model a map below a toolbar. The real Android view receives the same touch
                    // event bridge as either map SDK, without a network or map-renderer dependency.
                    Spacer(Modifier.height(80.dp))
                    StopSelectionMap(state, callbacks, Modifier.fillMaxWidth().height(200.dp), enabled) { mapCallbacks, modifier ->
                        deliveredCallbacks = requireNotNull(mapCallbacks)
                        AndroidView(
                            factory = { viewContext ->
                                View(viewContext).apply {
                                    setOnClickListener {
                                        if (nativeMarkerTap) mapCallbacks.onStopClick(native) else mapCallbacks.onMapClick(native.point)
                                    }
                                }
                            },
                            modifier = modifier.testTag("map").onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInRoot()
                                state.setProjector(
                                    MapProjector { point ->
                                        when (point) {
                                            native.point -> ScreenOffset(bounds.center.x, bounds.center.y)
                                            nearby.point -> ScreenOffset(bounds.center.x + 8f, bounds.center.y)
                                            // Adding the inset twice incorrectly captures this stop.
                                            else -> ScreenOffset(bounds.center.x, bounds.center.y + bounds.top)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun stop(id: String, name: String, direction: String = "NW") = StopMarker(
        id,
        GeoPoint(id.toDouble(), 1.0),
        direction,
        3,
        ObaStopElement(id = id, code = id, name = name, direction = direction, lat = id.toDouble(), lon = 1.0)
    )
}
