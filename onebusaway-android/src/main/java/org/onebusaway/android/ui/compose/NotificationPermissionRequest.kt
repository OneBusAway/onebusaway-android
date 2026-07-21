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
package org.onebusaway.android.ui.compose

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.onebusaway.android.app.di.PushRegistrationEntryPoint

/**
 * Remembers the in-app POST_NOTIFICATIONS opt-in, returning a `() -> Unit` that requests the
 * permission and, once the user responds, resyncs this device's push registration.
 *
 * The system permission dialog only pauses (never stops) the activity, so PushRegistrationManager's
 * `ProcessLifecycleOwner` `ON_START` resync never fires for an in-flow grant. Resyncing straight from
 * the result callback registers the device the moment the user opts in — the highest-value moment for
 * the push feature — instead of waiting for the next app launch. Wiring this into one shared helper
 * means every opt-in site inherits the resync by construction rather than re-deriving it (and risking
 * the old fire-and-forget bug).
 *
 * The returned action is a no-op below API 33, where POST_NOTIFICATIONS is granted implicitly at
 * install, so callers can invoke it unconditionally.
 */
@Composable
internal fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { PushRegistrationEntryPoint.get(context).resync() }
    // Remembered so the returned action keeps a stable identity across recompositions — callers pass it
    // into other composables (see TripDestinations), which could not skip if it were a fresh lambda
    // every time.
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
