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
package org.onebusaway.android.map.maplibre.compose

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.Composable
import org.maplibre.android.annotations.Marker
import org.maplibre.android.maps.MapLibreMap
import org.onebusaway.android.map.compose.ComposeBitmapRenderer

/**
 * Renders the shared vehicle/bike info-window composables as the maplibre info window (replacing the
 * classic title/snippet), so both map flavors show identical content.
 *
 * maplibre's classic [InfoWindow][org.maplibre.android.annotations.InfoWindow] **measures the content
 * view synchronously when it opens** to anchor it (bottom-center) over the marker, but a ComposeView
 * has no size until it composes + lays out (an async pass), so it opens unanchored (top-left at the
 * marker) and only the horizontal centre settles on the next camera move. So, like the Google flavor, a
 * window is described by a [content] composable pre-rendered to a [Bitmap] via [ComposeBitmapRenderer]:
 * [open] captures it, then [selectMarker]s to show an [ImageView] of that bitmap — a view with a real
 * synchronous size, which maplibre anchors correctly on the first frame. [clear] drops the tracked
 * window (a tap away / another marker). Markers with no content (the trip-focus estimate markers) fall
 * through to the SDK's default title window.
 */
class MapLibreInfoWindows(
    private val activity: Activity,
    container: ViewGroup,
    private val map: MapLibreMap,
) : MapLibreMap.InfoWindowAdapter {

    private val preRenderer = ComposeBitmapRenderer(activity, container)
    private var shownMarker: Marker? = null
    private var bitmap: Bitmap? = null

    /** Open [marker]'s info window, pre-rendering [content] to a bitmap and then selecting the marker. */
    fun open(marker: Marker, content: @Composable () -> Unit) {
        preRenderer.render(content) { captured ->
            // Don't recycle the previous bitmap: maplibre keeps the live ImageView (it doesn't snapshot
            // it like the Google SDK), so a still-attached window could draw a recycled bitmap. Dropping
            // the reference is enough — GC reclaims it (info-window bitmaps are small).
            bitmap = captured
            shownMarker = marker
            map.selectMarker(marker) // shows the window -> getInfoWindow returns the bitmap below
        }
    }

    /** Forget the tracked window (a tap away / another marker dismissed it). */
    fun clear() {
        preRenderer.cancel()
        shownMarker = null
        bitmap = null
    }

    override fun getInfoWindow(marker: Marker): View? {
        val bmp = bitmap
        if (marker === shownMarker && bmp != null) {
            return ImageView(activity).apply {
                setImageBitmap(bmp)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        return null // default title window (the trip-focus estimate markers)
    }
}
