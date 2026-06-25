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
 * Renders the shared vehicle/bike info-window composables as the Google Maps info window.
 *
 * Google's `InfoWindowAdapter` draws the returned View into a **static bitmap of a detached view**, so
 * a bare ComposeView returned directly would render blank (composition is async and only runs while
 * attached). So a window is described by a **live content provider** (`@Composable () -> Unit` that
 * reads the current marker state): [open] stores it and pre-renders it to a bitmap (via
 * [ComposeBitmapRenderer]); [refresh] re-renders it from the same provider (e.g. after a poll, so an
 * open bubble reflects fresh data); and the adapter calls [clear] when the window is dismissed (a tap
 * away, or another marker). Markers with no provider (the trip-focus / most-recent-data markers) fall
 * through to the SDK's default title/snippet window.
 */
class GoogleInfoWindows(
    private val activity: Activity,
    container: ViewGroup,
) : GoogleMap.InfoWindowAdapter {

    private val preRenderer = ComposeBitmapRenderer(activity, container)
    private var shownMarker: Marker? = null
    private var content: (@Composable () -> Unit)? = null
    private var bitmap: Bitmap? = null

    /** Open [marker]'s info window, rendering [content] (re-read live on every render). */
    fun open(marker: Marker, content: @Composable () -> Unit) {
        shownMarker = marker
        this.content = content
        render()
    }

    /** Re-render the currently open window from its (live) provider — call when its data changes. */
    fun refresh() {
        if (shownMarker != null && content != null) render()
    }

    /** Whether [marker]'s info window is the one currently tracked/open (so callers don't re-[open] it). */
    fun isShowing(marker: Marker): Boolean = shownMarker === marker

    /** Forget the tracked window (the SDK window was dismissed by a tap away / another marker). */
    fun clear() {
        preRenderer.cancel()
        shownMarker = null
        content = null
        // Safe to recycle: clear() runs when the window is dismissed, so the SDK (which already has its
        // own snapshot) no longer references our bitmap.
        bitmap?.recycle()
        bitmap = null
    }

    /** Pre-render the live provider to a bitmap, then (re)show the window once it's captured. */
    private fun render() {
        val marker = shownMarker ?: return
        val provider = content ?: return
        preRenderer.render(provider) { captured ->
            if (shownMarker === marker) {
                // Recycle the prior capture before replacing it: the SDK snapshots our view into its
                // own static bitmap when the window shows, so the previous bitmap (from an earlier
                // render cycle, already snapshotted) is no longer referenced. Without this, each
                // refresh while a bubble is open leaks a full-view bitmap until GC.
                bitmap?.recycle()
                bitmap = captured
                marker.showInfoWindow()
            } else {
                captured.recycle() // selection changed mid-render; this capture is never shown
            }
        }
    }

    override fun getInfoWindow(marker: Marker): View? {
        val bmp = bitmap
        if (marker == shownMarker && bmp != null) {
            return ImageView(activity).apply {
                setImageBitmap(bmp)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
        return null // default title/snippet window (trip-focus + most-recent-data markers)
    }

    override fun getInfoContents(marker: Marker): View? = null
}
