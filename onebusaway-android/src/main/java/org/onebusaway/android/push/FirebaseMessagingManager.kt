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
package org.onebusaway.android.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * Owns the FCM registration token: fetching it at startup and exposing the stored value. Extracted
 * from `Application` (the god-object) so onCreate just kicks [fetchAndStoreToken]. The token is
 * persisted through [PreferencesRepository] — the same seam `MyFirebaseMessagingService.onNewToken`
 * uses — so the startup fetch and later token rotations write the one `firebase_messaging_token` slot.
 */
@Singleton
class FirebaseMessagingManager @Inject constructor(
    private val prefs: PreferencesRepository,
) {

    /**
     * Fetches the current FCM registration token and persists it. Fire-and-forget; called once from
     * `Application.onCreate` so a token obtained before the app first read it is captured.
     */
    fun fetchAndStoreToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            prefs.setString(R.string.firebase_messaging_token, task.result)
        }
    }

    /** The FCM registration token, or "" if one hasn't been obtained yet. */
    fun userPushId(): String = prefs.getString(R.string.firebase_messaging_token, null) ?: ""

    /** Whether an FCM push token has been obtained yet. */
    fun hasPushToken(): Boolean = userPushId().isNotEmpty()

    private companion object {
        const val TAG = "FirebaseMessagingManager"
    }
}
