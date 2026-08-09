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
package org.onebusaway.android.map.googlemapsv2.compose

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.runtime.Composable
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import org.onebusaway.android.map.compose.ComposeBitmapRenderer

/**
 * Renders the shared rental / pinned-trip info-window composables as the Google Maps info window.
 *
 * Google's `InfoWindowAdapter` draws the returned View into a **static bitmap of a detached view**, so
 * a bare ComposeView returned directly would render blank (composition is async and only runs while
 * attached). So a window is described by a content provider (`@Composable () -> Unit`): [open] stores
 * it and pre-renders it to a bitmap (via [ComposeBitmapRenderer]), and the adapter calls [clear] when
 * the window is dismissed (a tap away, or another marker). Markers with no provider (the trip-focus /
 * most-recent-data markers) fall through to the SDK's default title/snippet window.
 *
 * The provider was once re-read on each poll to keep the vehicle bubble current; nothing needs that
 * now that the vehicle marker says its own piece (#2194) and the remaining windows' content is static.
 */
class GoogleInfoWindows(
    private val activity: Activity,
    container: ViewGroup
) : GoogleMap.InfoWindowAdapter {

    private val preRenderer = ComposeBitmapRenderer(activity, container)
    private var shownMarker: Marker? = null
    private var bitmap: Bitmap? = null

    /** Open [marker]'s info window, pre-rendering [content] to a bitmap and showing it once captured. */
    fun open(marker: Marker, content: @Composable () -> Unit) {
        shownMarker = marker
        preRenderer.render(content) { captured ->
            if (shownMarker === marker) {
                // Recycle any prior capture before replacing it: the SDK snapshots our view into its own
                // static bitmap when the window shows, so an earlier capture is no longer referenced.
                bitmap?.recycle()
                bitmap = captured
                marker.showInfoWindow()
            } else {
                captured.recycle() // selection changed mid-render; this capture is never shown
            }
        }
    }

    /** Forget the tracked window (the SDK window was dismissed by a tap away / another marker). */
    fun clear() {
        preRenderer.cancel()
        shownMarker = null
        // Safe to recycle: clear() runs when the window is dismissed, so the SDK (which already has its
        // own snapshot) no longer references our bitmap.
        bitmap?.recycle()
        bitmap = null
    }

    override fun getInfoWindow(marker: Marker): View? {
        val bmp = bitmap
        if (marker == shownMarker && bmp != null) {
            return ImageView(activity).apply {
                setImageBitmap(bmp)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        return null // default title/snippet window (trip-focus + most-recent-data markers)
    }

    override fun getInfoContents(marker: Marker): View? = null
}
