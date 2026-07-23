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
package org.onebusaway.android.map

import android.util.Log
import org.onebusaway.android.api.ObaApi
import org.onebusaway.android.api.ObaApiException

/**
 * A one-shot map event that needs an Activity to carry out (so it can't be plain state). [MapHost]
 * emits these on [MapHost.effects] (re-exposed by the map view models); the hosting Activity collects
 * them while STARTED and shows the corresponding UI (the dialog/toast bodies are the ones that used to
 * live in the flavor host's `showOutOfRangeDialog` / `showNoLocationDialog` /
 * `showLocationPermissionDialog` / the my-location toast + the permission-launcher relay).
 */
sealed interface MapEffect {

    /** The viewport (or the device) is outside the current region — prompt the user to switch regions. */
    object OutOfRange : MapEffect

    /** Location services are off — show the "enable location" dialog (with its never-ask-again opt-out). */
    object NoLocation : MapEffect

    /** Explain why location permission is needed, before asking for it (the rationale dialog). */
    object ShowPermissionRationale : MapEffect

    /** Launch the system location-permission request (the Activity owns the result launcher). */
    object RequestLocationPermission : MapEffect

    /** We have permission + services but no fix yet — "waiting for location" toast. */
    object WaitingForLocation : MapEffect

    /**
     * A viewport/route load failed — show the map error toast for OBA status [code] (an HTTP status or
     * an `ObaApi.OBA_*` sentinel). Rendered by the UI layer, so the cold controllers don't reach for a
     * `Context` just to present the toast.
     */
    data class ShowError(val code: Int) : MapEffect {
        companion object {
            private const val TAG = "MapEffect"

            /**
             * The status code a failed load reports: an [ObaApiException]'s code, else a generic I/O error.
             *
             * This collapse is lossy — the user-facing toast is just a status code, so the underlying
             * [cause] (which HTTP/app code, or the exact parse failure and offending field) is otherwise
             * discarded here. Log it first, so a single-region load failure that reduces to the generic
             * "Unable to get stops." toast (e.g. #1462) is diagnosable from logcat / crash reports rather
             * than vanishing. An [ObaApiException] carries its own status, so the code is enough; any other
             * throwable (a transport failure, or a kotlinx `SerializationException` naming a bad field) is
             * logged with its full stack.
             */
            fun from(cause: Throwable): ShowError {
                val code = (cause as? ObaApiException)?.code ?: ObaApi.OBA_IO_EXCEPTION
                if (cause is ObaApiException) {
                    Log.w(TAG, "Map load failed with OBA status code $code")
                } else {
                    Log.w(TAG, "Map load failed (reporting code $code); underlying cause:", cause)
                }
                return ShowError(code)
            }
        }
    }
}
