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
package org.onebusaway.android.util

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/**
 * Loads a drawable that the caller knows exists, failing with the offending resource name instead of
 * a bare `NullPointerException`.
 *
 * `ContextCompat.getDrawable` is declared nullable because it returns null for a resource id that
 * isn't present. For an `R.drawable.*` constant that can only happen if the id resolves to nothing at
 * runtime — a resource stripped by shrinking, or a flavor that overrode the drawable away — which is
 * a packaging bug, not a state a caller can recover from. So this still throws; the difference is that
 * the crash report names the resource.
 */
fun requireDrawable(context: Context, @DrawableRes resId: Int): Drawable = checkNotNull(ContextCompat.getDrawable(context, resId)) {
    "Missing drawable resource ${safeResourceName(context, resId)}"
}

/**
 * Theme-aware counterpart of [requireDrawable], for call sites that need the drawable resolved
 * against a specific theme (tinted attrs, day/night variants).
 */
fun requireDrawable(context: Context, @DrawableRes resId: Int, theme: android.content.res.Resources.Theme?): Drawable = checkNotNull(ResourcesCompat.getDrawable(context.resources, resId, theme)) {
    "Missing drawable resource ${safeResourceName(context, resId)}"
}

/**
 * Best-effort resource name for the failure message. Resolving the name itself throws when the id is
 * absent, which is exactly the case we're already reporting — so fall back to the raw id rather than
 * masking the original failure with a second one.
 */
private fun safeResourceName(context: Context, resId: Int): String = try {
    context.resources.getResourceName(resId)
} catch (_: android.content.res.Resources.NotFoundException) {
    "0x${resId.toString(16)}"
}
