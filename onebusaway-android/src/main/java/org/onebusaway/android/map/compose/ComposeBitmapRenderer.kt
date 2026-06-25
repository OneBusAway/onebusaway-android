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

import android.app.Activity
import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.drawToBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * Renders a [Composable] to a [Bitmap] off-screen, for use as a map info window.
 *
 * Both map SDKs snapshot/measure an info-window view the moment it shows, but a [ComposeView] has no
 * size until it has composed and laid out — an async pass. So [render] attaches [content] to
 * [container] invisibly (in [ObaTheme]), waits for it to lay out, captures it with [drawToBitmap], and
 * hands the bitmap to [onReady] on the main thread. Each flavor's info-window adapter wraps the bitmap
 * in an `ImageView` and supplies its own SDK-specific show/anchor glue; this is the shared pre-render
 * the Google and maplibre adapters both build on. Bitmap ownership passes to [onReady] (the caller
 * decides when to recycle). Single-threaded: drive it from the main thread.
 */
class ComposeBitmapRenderer(
    private val activity: Activity,
    private val container: ViewGroup,
) {

    // The in-flight pre-render (one at a time), so a second render tears the first down.
    private var pendingView: ComposeView? = null
    private var pendingListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** Pre-render [content] to a bitmap, invoking [onReady] once it's laid out. Cancels any in-flight render. */
    fun render(content: @Composable () -> Unit, onReady: (Bitmap) -> Unit) {
        cancel()
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity as LifecycleOwner)
            setViewTreeViewModelStoreOwner(activity as ViewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            alpha = 0f // attached so it composes, but invisible until we remove it
            setContent { ObaTheme { content() } }
        }
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (composeView.width == 0 || composeView.height == 0) return // wait for composed content
                val bitmap = composeView.drawToBitmap()
                cancel()
                onReady(bitmap)
            }
        }
        pendingView = composeView
        pendingListener = listener
        container.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        composeView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    /** Tear down any in-flight render (remove the off-screen view + its layout listener). */
    fun cancel() {
        val view = pendingView ?: return
        pendingListener?.let { view.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        container.removeView(view)
        pendingView = null
        pendingListener = null
    }
}
