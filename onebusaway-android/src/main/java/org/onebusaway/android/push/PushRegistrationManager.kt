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
import org.onebusaway.android.app.di.AppScope
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository

/**
 * Keeps this device's OBACloud push registration (issue #1957) in sync with the app's current state, so
 * that service-alert notifications reach every rider who has notifications enabled — not only those who
 * happen to create a trip alarm.
 *
 * A reconciler, in three parts:
 *
 * 1. **Desired state** ([deriveDesiredRegistration]) — a pure classification of the raw inputs (OS
 *    notifications-enabled — the opt-in signal; there is no separate in-app toggle — the current
 *    region, the FCM token, the device locale, and the test-device settings) into wanted / opted-out /
 *    no-sidecar / not-yet-resolved. This class only gathers the inputs.
 * 2. **The decision** ([decidePushRegistration]) — a pure function comparing desired against the record
 *    of what was last sent, yielding register / re-register / unregister / no-op.
 * 3. **Applying it** — this class, which owns only the rule for *what to record given which calls
 *    succeeded* (the quadrants in [sync]).
 *
 * The mechanisms underneath are deliberately elsewhere: [PushRegistrationClient] owns talking to the
 * endpoint and making failures visible, and [PushRegistrationStore] owns the durable record and its
 * commit semantics. Neither retries — every trigger below re-runs [sync], and that is the retry policy.
 */
@Singleton
class PushRegistrationManager internal constructor(
    private val client: PushRegistrationClient,
    private val store: PushRegistrationStore,
    private val regionRepository: RegionRepository,
    private val firebaseMessagingManager: FirebaseMessagingManager,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
    // The one Android-only read left here: NotificationManagerCompat isn't available to a plain JVM
    // test, and the opt-in signal is what the whole reconcile hinges on.
    private val notificationsEnabled: () -> Boolean
) {

    /** Production constructor Hilt builds from: resolves the seam from [context] and delegates. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        client: PushRegistrationClient,
        store: PushRegistrationStore,
        regionRepository: RegionRepository,
        firebaseMessagingManager: FirebaseMessagingManager,
        prefs: PreferencesRepository,
        @AppScope scope: CoroutineScope
    ) : this(
        client = client,
        store = store,
        regionRepository = regionRepository,
        firebaseMessagingManager = firebaseMessagingManager,
        prefs = prefs,
        scope = scope,
        notificationsEnabled = { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    )

    private val started = AtomicBoolean(false)

    // Serializes reconciliation so overlapping triggers (region change + a token write arriving together)
    // can't interleave two syncs and race on the stored record.
    private val mutex = Mutex()

    /**
     * Begins reconciling in the background, idempotently (safe to call more than once). Kicked from
     * `Application.onCreate` (on the main thread). Two triggers, covering every input that can change:
     *
     * - A collector over the region flow and the preferences this feature reads (the FCM token, the
     *   test-device flag, and the test-device name), running one [sync] per change. All of them replay
     *   on launch, and each later change (token rotation, region switch, toggling or naming the test
     *   device) re-emits. Observing those keys specifically — rather than every app-wide preference
     *   write — avoids a needless `areNotificationsEnabled()` IPC on unrelated writes.
     * - A process-lifecycle `ON_START` observer that [resync]s whenever the app comes to the foreground,
     *   catching an OS notification-permission change made in system settings (which touches neither a
     *   preference nor the region, so the collector can't see it) regardless of which activity resumes.
     *
     * Between them these also drive the [PUSH_REFRESH_INTERVAL] keep-alive: every foreground re-runs
     * [sync], which re-POSTs an unchanged registration once its 24h window has elapsed. No separate
     * timer or `WorkManager` job is needed — a rider who never opens the app for 180 days has no
     * service-alert reach to preserve anyway, and the server prunes exactly that population.
     */
    fun start() {
        if (started.getAndSet(true)) return
        scope.launch {
            combine(
                regionRepository.region,
                prefs.observeString(R.string.firebase_messaging_token, null),
                prefs.observeBoolean(R.string.preference_key_push_test_device, false),
                prefs.observeString(R.string.preference_key_push_test_device_name, null)
            ) { _, _, _, _ -> }.collect { sync() }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = resync()
        })
    }

    /** Re-runs reconciliation once, off the main thread; a no-op when nothing changed. */
    fun resync() {
        scope.launch { sync() }
    }

    /** Reconciles the recorded registration with the currently desired one. */
    private suspend fun sync() = mutex.withLock {
        val desired = deriveDesiredRegistration(
            notificationsEnabled = notificationsEnabled(),
            region = regionRepository.region.value,
            token = firebaseMessagingManager.userPushId(),
            locale = Locale.getDefault().toLanguageTag(),
            testDeviceEnabled = prefs.getBoolean(R.string.preference_key_push_test_device, false),
            testDeviceName = prefs.getString(R.string.preference_key_push_test_device_name, null)
        )
        // Unresolved (an async input hasn't settled) is the only state that may skip the decision — a
        // later trigger re-runs us. See DesiredRegistration for why it must never be read as a resolved
        // state (deciding on a half-read state fires spurious DELETEs).
        if (desired !is DesiredRegistration.Resolved) return@withLock

        when (val action = decidePushRegistration(desired, store.last(), store.sinceLastSent())) {
            PushRegistrationAction.NoOp -> Unit
            is PushRegistrationAction.Register ->
                if (client.register(action.target)) store.record(action.target)
            is PushRegistrationAction.Unregister ->
                if (client.unregister(action.previous)) store.clear()
            is PushRegistrationAction.Reregister -> applyReregister(action)
        }
    }

    /**
     * The [PushRegistrationAction.Reregister] DELETE and POST as two independent steps, each
     * persisting its own outcome exactly like the standalone Unregister/Register branches in [sync]:
     * a cleared-but-unrecorded state re-Registers next sync, both calls failing keeps `previous` so
     * the whole Reregister retries, and a missed DELETE alone is dropped — best-effort, see
     * [PushRegistrationAction.Reregister].
     */
    private suspend fun applyReregister(action: PushRegistrationAction.Reregister) {
        if (client.unregister(action.previous)) store.clear()
        if (client.register(action.target)) store.record(action.target)
    }
}
