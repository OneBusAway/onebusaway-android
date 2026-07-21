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
 * 1. **Desired state** ([buildTarget]) — derived from whether notifications are enabled at the OS level
 *    (the opt-in signal; there is no separate in-app toggle), the current region (its id + sidecar
 *    host), the FCM token, the device locale, and the test-device settings.
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
        // Settle any DELETE still owed from an earlier Reregister whose POST landed but whose DELETE
        // didn't, before reconciling, so the stale registration can't linger past this pass.
        drainPendingDelete()

        // Notifications being off is the ONLY definitive "this device should not be registered" signal.
        // A missing region or FCM token just means we can't tell yet — both resolve asynchronously, and
        // the region flow is explicitly "briefly null at cold start" (see RegionRepository) — so treating
        // that as an opt-out would fire a spurious DELETE on launch and re-POST seconds later, burning
        // two requests against a rate-limited endpoint and leaving a window with no registration at all.
        val target = if (notificationsEnabled()) {
            // Inputs still settling — bail rather than decide; a later trigger re-runs us.
            buildTarget() ?: return@withLock
        } else {
            null
        }

        when (val action = decidePushRegistration(target, store.last(), store.sinceLastSent())) {
            PushRegistrationAction.NoOp -> Unit
            is PushRegistrationAction.Register ->
                if (client.register(action.target)) store.record(action.target)
            is PushRegistrationAction.Unregister ->
                if (client.unregister(action.previous)) store.clear()
            is PushRegistrationAction.Reregister -> applyReregister(action)
        }
    }

    /**
     * DELETEs the stale registration, then POSTs the new one, recording only once the POST lands. Both
     * calls can fail independently, and all four quadrants have to leave the device recoverable:
     *
     * - DELETE ok   + POST ok   → record(target): old gone, new on record.
     * - DELETE ok   + POST fail → clear(): the server holds nothing, so the next sync re-Registers.
     * - DELETE fail + POST fail → keep `previous`: the next sync retries the whole Reregister.
     * - DELETE fail + POST ok   → record(target) AND queue `previous` for a retried DELETE, rather than
     *   dropping it — otherwise the old region keeps pushing to a still-valid token until the server's
     *   180-day prune.
     */
    private suspend fun applyReregister(action: PushRegistrationAction.Reregister) {
        val cleaned = client.unregister(action.previous)
        if (client.register(action.target)) {
            store.record(action.target)
            if (!cleaned) store.queuePendingDelete(action.previous)
        } else if (cleaned) {
            store.clear()
        }
    }

    /**
     * Retries the DELETE queued by an earlier Reregister. Runs at the top of every sync; a no-op (one
     * prefs read, no network) when nothing is queued.
     */
    private suspend fun drainPendingDelete() {
        val pending = store.pendingDelete() ?: return
        if (client.unregister(pending)) store.clearPendingDelete()
    }

    /**
     * The registration this device wants, or null when the inputs aren't all resolved yet (no region,
     * no sidecar host, or no FCM token) — never an opt-out; see [sync]. The locale is the device's
     * BCP-47 tag, sent as-is with no normalization.
     *
     * The test-device flag is honoured only once the rider has named the device: the server rejects a
     * `test_device=true` registration with a blank `description` (422), so an unnamed device registers
     * as an ordinary one rather than POSTing a request guaranteed to fail. The iOS client gates its
     * "Test Device Name" the same way. Deriving `testDevice` from the name rather than tracking it
     * separately makes that rule hold by construction.
     *
     * The name is truncated to [PUSH_DESCRIPTION_MAX_LENGTH] here as well as being bounded at the input
     * field, so a value that predates that bound (or arrives from anywhere else) still can't produce a
     * request the server is guaranteed to reject.
     */
    private fun buildTarget(): PushRegistration? {
        val region = regionRepository.region.value ?: return null
        val base = region.sidecarBaseUrl?.takeIf { it.isNotBlank() } ?: return null
        val token = firebaseMessagingManager.userPushId().takeIf { it.isNotEmpty() } ?: return null
        val description = prefs.getString(R.string.preference_key_push_test_device_name, null)
            ?.trim()
            ?.take(PUSH_DESCRIPTION_MAX_LENGTH)
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { prefs.getBoolean(R.string.preference_key_push_test_device, false) }
        return PushRegistration(
            regionId = region.id,
            sidecarBaseUrl = base,
            token = token,
            locale = Locale.getDefault().toLanguageTag(),
            testDevice = description != null,
            description = description
        )
    }
}
