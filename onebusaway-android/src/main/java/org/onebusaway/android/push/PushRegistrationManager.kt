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

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.PushRegistrationWebService
import org.onebusaway.android.app.di.AppScope
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.runCatchingCancellable

/**
 * Keeps this device's OBACloud push registration (issue #1957) in sync with the app's current state so
 * that service-alert notifications reach every rider who has notifications enabled — not only those who
 * happen to create a trip alarm.
 *
 * The desired state is derived from three inputs: whether notifications are enabled at the OS level
 * (the opt-in signal — there is no separate in-app toggle), the current region (its id + sidecar host),
 * and the FCM token. Whenever any of them changes we [sync]: [decidePushRegistration] compares the desired
 * [PushRegistration] against the one last sent to the server and yields a register / re-register /
 * unregister / no-op action, which [apply] performs against [PushRegistrationWebService].
 *
 * The last successful registration is persisted (the `push_reg_*` prefs slots) so we know exactly what
 * to DELETE on opt-out and can skip redundant POSTs — important given the endpoint's 30 req/min/IP rate
 * limit (see [PushRegistrationWebService]).
 */
@Singleton
class PushRegistrationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val service: PushRegistrationWebService,
    private val regionRepository: RegionRepository,
    private val firebaseMessagingManager: FirebaseMessagingManager,
    private val prefs: PreferencesRepository,
    @param:AppScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    // Serializes reconciliation so overlapping triggers (region change + a token write arriving together)
    // can't interleave two syncs and race on the persisted last-registration record.
    private val mutex = Mutex()

    /**
     * Begins reconciling in the background, idempotently (safe to call more than once). Kicked from
     * `Application.onCreate` (on the main thread). Two triggers, covering every input that can change:
     *
     * - A collector over the region flow and the two preferences this feature reads (the FCM token and
     *   the test-device flag), running one [sync] per change. All three replay on launch, and each
     *   later change (token rotation, region switch, toggling the test flag) re-emits. Observing those
     *   keys specifically — rather than every app-wide preference write — avoids a needless
     *   `areNotificationsEnabled()` IPC on unrelated writes.
     * - A process-lifecycle `ON_START` observer that [resync]s whenever the app comes to the foreground,
     *   catching an OS notification-permission change made in system settings (which touches neither a
     *   preference nor the region, so the collector can't see it) regardless of which activity resumes.
     */
    fun start() {
        if (started.getAndSet(true)) return
        scope.launch {
            combine(
                regionRepository.region,
                prefs.observeString(R.string.firebase_messaging_token, null),
                prefs.observeBoolean(R.string.preference_key_push_test_device, false),
            ) { _, _, _ -> }.collect { sync() }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = resync()
        })
    }

    /** Re-runs reconciliation once, off the main thread; a no-op when nothing changed. */
    fun resync() {
        scope.launch { sync() }
    }

    /** Reconciles the on-record registration with the currently desired one. */
    private suspend fun sync() = mutex.withLock {
        when (val action = decidePushRegistration(currentTarget(), loadLast())) {
            PushRegistrationAction.NoOp -> Unit
            is PushRegistrationAction.Register ->
                if (register(action.target)) persist(action.target)
            is PushRegistrationAction.Unregister ->
                if (unregister(action.previous)) clearLast()
            is PushRegistrationAction.Reregister -> {
                // DELETE the stale registration, then POST the new one, persisting only once the POST
                // lands. On failure keep `previous` on record UNLESS the DELETE already removed it:
                // clearing it while it's still registered would make the next sync a plain Register,
                // orphaning the old region/token (it keeps receiving alerts until the 180-day age-out).
                val cleaned = unregister(action.previous)
                if (register(action.target)) persist(action.target)
                else if (cleaned) clearLast()
            }
        }
    }

    /**
     * The registration the device wants right now, or null when it can't/shouldn't be registered
     * (notifications disabled, no resolved region + sidecar host, or no FCM token yet). The locale is
     * the device's BCP-47 tag, sent as-is with no normalization.
     */
    private fun currentTarget(): PushRegistration? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return null
        val region = regionRepository.region.value ?: return null
        val base = region.sidecarBaseUrl?.takeIf { it.isNotBlank() } ?: return null
        val token = firebaseMessagingManager.userPushId().takeIf { it.isNotEmpty() } ?: return null
        return PushRegistration(
            regionId = region.id,
            sidecarBaseUrl = base,
            token = token,
            locale = Locale.getDefault().toLanguageTag(),
            testDevice = prefs.getBoolean(R.string.preference_key_push_test_device, false),
        )
    }

    /** POSTs [target]; true only on a 2xx (204) success. */
    private suspend fun register(target: PushRegistration): Boolean = runCatchingCancellable {
        service.register(
            url = registrationUrl(target),
            token = target.token,
            locale = target.locale,
            testDevice = target.testDevice,
        ).isSuccessful
    }.getOrDefault(false)

    /** DELETEs [previous]; true on 204 or 404 (already gone), so a stale record is cleared either way. */
    private suspend fun unregister(previous: PushRegistration): Boolean = runCatchingCancellable {
        val response = service.unregister(url = registrationUrl(previous), token = previous.token)
        response.isSuccessful || response.code() == HttpURLConnection.HTTP_NOT_FOUND
    }.getOrDefault(false)

    /** `{sidecarBaseUrl}/api/v2/regions/{regionId}/push_registrations` — mirrors the alarms URL build. */
    private fun registrationUrl(registration: PushRegistration): String =
        registration.sidecarBaseUrl +
            context.getString(R.string.arrivals_reminders_api_endpoint) +
            registration.regionId + "/push_registrations"

    /**
     * The registration last successfully sent to the server, or null if none is on record. The token
     * slot doubles as the commit sentinel: [persist] writes it last and [clearLast] nulls it, so a
     * torn/absent write reads back here as "no record" and re-registration (safe, idempotent) follows.
     */
    private fun loadLast(): PushRegistration? {
        val base = prefs.getString(KEY_BASE, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return PushRegistration(
            regionId = prefs.getLong(KEY_REGION_ID, -1L),
            sidecarBaseUrl = base,
            token = token,
            locale = prefs.getString(KEY_LOCALE, null) ?: "",
            testDevice = prefs.getBoolean(KEY_TEST_DEVICE, false),
        )
    }

    private fun persist(registration: PushRegistration) {
        prefs.setLong(KEY_REGION_ID, registration.regionId)
        prefs.setString(KEY_BASE, registration.sidecarBaseUrl)
        prefs.setString(KEY_LOCALE, registration.locale)
        prefs.setBoolean(KEY_TEST_DEVICE, registration.testDevice)
        // Written last: its presence is what [loadLast] treats as "a full record is committed".
        prefs.setString(KEY_TOKEN, registration.token)
    }

    /** Nulls the commit-sentinel token slot so [loadLast] reports no record. */
    private fun clearLast() {
        prefs.setString(KEY_TOKEN, null)
    }

    private companion object {
        // Internal bookkeeping slots (not user-facing) for the last successful registration.
        const val KEY_REGION_ID = "push_reg_region_id"
        const val KEY_BASE = "push_reg_base"
        const val KEY_TOKEN = "push_reg_token"
        const val KEY_LOCALE = "push_reg_locale"
        const val KEY_TEST_DEVICE = "push_reg_test_device"
    }
}
