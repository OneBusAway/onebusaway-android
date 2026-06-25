/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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
package org.onebusaway.android.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import org.onebusaway.android.R
import org.onebusaway.android.ui.arrivals.ArrivalsListLauncher
import org.onebusaway.android.ui.routeinfo.RouteInfoLauncher
import org.onebusaway.android.util.ViewUtils

/**
 * Helpers for creating launcher shortcuts (stops, routes, and arbitrary destinations).
 */
object Shortcuts {

    /**
     * Creates a new shortcut for the provided stop, and returns the ShortcutInfo for that shortcut
     * @param context Context used to create the shortcut
     * @param shortcutName the shortcutName for the stop shortcut
     * @param builder Instance of ArrivalsListLauncher.Builder for the provided stop
     * @return the ShortcutInfo for the created shortcut
     */
    fun createStopShortcut(
        context: Context,
        shortcutName: String?,
        builder: ArrivalsListLauncher.Builder
    ): ShortcutInfoCompat {
        val shortcut = makeShortcutInfo(
            context,
            shortcutName,
            builder.intent,
            R.drawable.ic_stop_flag_triangle
        )
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        return shortcut
    }

    /**
     * Creates a new shortcut for the provided route, and returns the ShortcutInfo for that shortcut
     * @param context Context used to create the shortcut
     * @param routeId ID of the route
     * @param routeName short name of the route
     * @return the ShortcutInfo for the created shortcut
     */
    fun createRouteShortcut(
        context: Context,
        routeId: String,
        routeName: String
    ): ShortcutInfoCompat {
        val shortcut = makeShortcutInfo(
            context,
            routeName,
            RouteInfoLauncher.makeIntent(context, routeId),
            R.drawable.ic_trip_details
        )
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        return shortcut
    }

    /**
     * Default implementation for making a ShortcutInfoCompat object.  Note that this method doesn't
     * create the actual shortcut on the launcher - ShortcutManagerCompat.requestPinShortcut() must
     * be called with the ShortcutInfoCompat returned from this method to create the shortcut
     * on the launcher.
     *
     * @param name       The name of the shortcut
     * @param destIntent The destination intent
     * @param icon       Resource ID for the shortcut icon - should be black so it can be tinted and
     *                   60dp (2dp of asset padding) for high resolution on launcher screens
     * @return ShortcutInfoCompat that can be used to request pinning the shortcut
     */
    @JvmStatic
    fun makeShortcutInfo(
        context: Context,
        name: String?,
        destIntent: Intent,
        @DrawableRes icon: Int
    ): ShortcutInfoCompat {
        // Launcher shortcuts must open a fresh task rooted at the destination; without
        // CLEAR_TASK, tapping a shortcut while the app is in the background just resumes
        // the app's last screen (#1564 — supersedes the CLEAR_TOP-only flag from #626).
        destIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        destIntent.action = Intent.ACTION_VIEW

        val drawableIcon: Drawable = ResourcesCompat
            .getDrawable(context.resources, icon, context.theme)!!
        drawableIcon.setColorFilter(
            ContextCompat.getColor(context, R.color.shortcut_icon),
            PorterDuff.Mode.SRC_IN
        )
        val drawableBackground: Drawable = ResourcesCompat
            .getDrawable(context.resources, R.drawable.launcher_background, context.theme)!!

        val layerDrawable = LayerDrawable(arrayOf(drawableBackground, drawableIcon))

        val backgroundInset = ViewUtils.dpToPixels(context, 2.0f)
        layerDrawable.setLayerInset(
            0,
            backgroundInset,
            backgroundInset,
            backgroundInset,
            backgroundInset
        )
        val iconInset = ViewUtils.dpToPixels(context, 7.0f)
        layerDrawable.setLayerInset(1, iconInset, iconInset, iconInset, iconInset)

        val b = Bitmap.createBitmap(
            layerDrawable.intrinsicWidth,
            layerDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(b)
        layerDrawable.setBounds(0, 0, canvas.width, canvas.height)
        layerDrawable.draw(canvas)

        val label = name.orEmpty()
        return ShortcutInfoCompat.Builder(context, label)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithBitmap(b))
            .setIntent(destIntent)
            .build()
    }
}
