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
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
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
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.runCatchingCancellable
import retrofit2.Response

/**
 * Carries a push-registration server rejection to the remote error channel. A dedicated type so these
 * group as their own Crashlytics issue rather than mixing into generic [IllegalStateException]s.
 */
class PushRegistrationException(message: String) : Exception(message)

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
 * limit (see [PushRegistrationWebService]). A separate `push_pending_del_*` slot holds a registration
 * whose DELETE we still owe when a re-registration's POST landed but its DELETE didn't, so the stale one
 * is retried on a later [sync] rather than orphaned (still pushing until the 180-day server prune).
 */
@Singleton
class PushRegistrationManager internal constructor(
    private val service: PushRegistrationWebService,
    private val regionRepository: RegionRepository,
    private val firebaseMessagingManager: FirebaseMessagingManager,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
    // Test seams for the Android-only bits (see the @Inject constructor). Kept off the DI path so
    // sync()'s reconcile/persist semantics — including the Reregister-failure record handling — are
    // exercisable from a plain JVM test, which is where the "orphan on double failure" class of bug lives.
    // logWarning is a seam too: the failure paths call it, and android.util.Log isn't mocked under a
    // plain JVM test, so the production default routes to Log.w while tests capture the message instead.
    private val notificationsEnabled: () -> Boolean,
    private val registrationsEndpointPath: String,
    // Remote error channel for server rejections (see [logHttpFailure]); Crashlytics in production.
    private val reportError: (Throwable) -> Unit,
    private val logWarning: (String, Throwable?) -> Unit,
    private val now: () -> WallTime = WallTime::now,
) {

    /** Production constructor Hilt builds from: resolves the seams from [context] and delegates. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        service: PushRegistrationWebService,
        regionRepository: RegionRepository,
        firebaseMessagingManager: FirebaseMessagingManager,
        prefs: PreferencesRepository,
        @AppScope scope: CoroutineScope,
    ) : this(
        service = service,
        regionRepository = regionRepository,
        firebaseMessagingManager = firebaseMessagingManager,
        prefs = prefs,
        scope = scope,
        notificationsEnabled = { NotificationManagerCompat.from(context).areNotificationsEnabled() },
        registrationsEndpointPath = context.getString(R.string.arrivals_reminders_api_endpoint),
        reportError = { FirebaseCrashlytics.getInstance().recordException(it) },
        logWarning = { message, cause -> Log.w(TAG, message, cause) },
    )

    private val started = AtomicBoolean(false)

    // Serializes reconciliation so overlapping triggers (region change + a token write arriving together)
    // can't interleave two syncs and race on the persisted last-registration record.
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
                prefs.observeString(R.string.preference_key_push_test_device_name, null),
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

    /** Reconciles the on-record registration with the currently desired one. */
    private suspend fun sync() = mutex.withLock {
        // Settle any DELETE we still owe from an earlier Reregister whose POST landed but whose DELETE
        // didn't, before reconciling, so the stale registration can't linger past this pass.
        drainPendingDelete()

        // Notifications being off is the ONLY definitive "this device should not be registered" signal.
        // A missing region or FCM token just means we can't tell yet — both resolve asynchronously, and
        // the region flow is explicitly "briefly null at cold start" (see RegionRepository) — so treating
        // that as an opt-out would fire a spurious DELETE on launch and re-POST seconds later, burning
        // two requests against a 30 req/min/IP endpoint and leaving a window with no registration at all.
        // Bail instead and let the collector re-run us when the inputs land; iOS no-ops the same way.
        val target = if (notificationsEnabled()) {
            // Inputs still settling (the region seeds asynchronously, the FCM token arrives late) —
            // bail rather than decide; the collector re-runs us once they land.
            buildTarget() ?: return@withLock
        } else {
            null
        }

        when (val action = decidePushRegistration(target, loadLast(), sinceLastSent())) {
            PushRegistrationAction.NoOp -> Unit
            is PushRegistrationAction.Register ->
                if (register(action.target)) persist(action.target)
            is PushRegistrationAction.Unregister ->
                if (unregister(action.previous)) clearLast()
            is PushRegistrationAction.Reregister -> {
                // DELETE the stale registration, then POST the new one, persisting only once the POST
                // lands. All four failure quadrants are handled so nothing is orphaned:
                //  - DELETE ok   + POST ok   → persist(target): old gone, new on record.
                //  - DELETE ok   + POST fail → clearLast(): server holds nothing, next sync re-Registers.
                //  - DELETE fail + POST fail → keep `previous`: next sync retries the whole Reregister.
                //  - DELETE fail + POST ok   → persist(target) AND queue `previous` for a retried DELETE
                //    (drained at the top of a later sync) rather than dropping it — otherwise the old
                //    region keeps pushing to a still-valid token until the 180-day server age-out.
                val cleaned = unregister(action.previous)
                if (register(action.target)) {
                    persist(action.target)
                    if (!cleaned) persistPendingDelete(action.previous)
                } else if (cleaned) {
                    clearLast()
                }
            }
        }
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
     */
    private fun buildTarget(): PushRegistration? {
        val region = regionRepository.region.value ?: return null
        val base = region.sidecarBaseUrl?.takeIf { it.isNotBlank() } ?: return null
        val token = firebaseMessagingManager.userPushId().takeIf { it.isNotEmpty() } ?: return null
        val description = prefs.getString(R.string.preference_key_push_test_device_name, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { prefs.getBoolean(R.string.preference_key_push_test_device, false) }
        return PushRegistration(
            regionId = region.id,
            sidecarBaseUrl = base,
            token = token,
            locale = Locale.getDefault().toLanguageTag(),
            testDevice = description != null,
            description = description,
        )
    }

    /** POSTs [target]; true only on a 2xx (204) success. Retried on the next sync, so a failure isn't fatal. */
    private suspend fun register(target: PushRegistration): Boolean = runCatchingCancellable {
        val response = service.register(
            url = registrationUrl(target),
            token = target.token,
            locale = target.locale,
            testDevice = target.testDevice,
            // Required by the server for a test device, omitted otherwise (see the service KDoc).
            description = target.description,
        )
        val registered = response.isSuccessful
        if (!registered) logHttpFailure("register", target, response)
        registered
    }.onFailure {
        // Surface the cause (without the token) so a "why isn't this device registered" report isn't
        // silent; the retry happens on the next trigger regardless.
        logWarning(failurePrefix("register", target), it)
    }.getOrDefault(false)

    /** DELETEs [previous]; true on 204 or 404 (already gone), so a stale record is cleared either way. */
    private suspend fun unregister(previous: PushRegistration): Boolean = runCatchingCancellable {
        val response = service.unregister(url = registrationUrl(previous), token = previous.token)
        val removed = response.isSuccessful || response.code() == HttpURLConnection.HTTP_NOT_FOUND
        if (!removed) logHttpFailure("unregister", previous, response)
        removed
    }.onFailure {
        // Same rationale as register(): log the cause without the token; the DELETE is retried later.
        logWarning(failurePrefix("unregister", previous), it)
    }.getOrDefault(false)

    /**
     * The shared opening of every failure message. Identifies the endpoint by region + host — never the
     * token, and never the URL, since the unregister URL carries the token as a query param.
     */
    private fun failurePrefix(operation: String, registration: PushRegistration): String =
        "push $operation failed for region ${registration.regionId} at ${registration.sidecarBaseUrl}"

    /**
     * Logs a non-2xx response. Without this an HTTP error is *invisible*: a failed status is a
     * perfectly successful call as far as [runCatchingCancellable] is concerned, so the `onFailure`
     * handlers above only ever fire for transport exceptions. Since a failed call also persists
     * nothing, the same doomed request then repeats on every foreground — silently, and against a
     * 30 req/min/IP endpoint — which is exactly how the missing-`description` 422 went unnoticed
     * until a packet capture.
     */
    private fun logHttpFailure(
        operation: String,
        registration: PushRegistration,
        response: Response<Unit>,
    ) {
        // The server's error body carries the actionable message (e.g. "Description can't be blank").
        val body = response.errorBody()?.string()?.trim().orEmpty()
        val detail = if (body.isEmpty()) "" else " $body"
        val message = "${failurePrefix(operation, registration)}: HTTP ${response.code()}$detail"
        logWarning(message, null)
        // Registrations are the server's ONLY audience source for service alerts, so a systematic
        // rejection — a required field added server-side, a contract change, a throttle — must not be
        // visible only in one developer's logcat. Report server rejections remotely; transport failures
        // (handled by the onFailure paths) are ordinary offline noise and stay local.
        reportError(PushRegistrationException(message))
    }

    /**
     * Retries the DELETE queued by an earlier Reregister (the DELETE-fail/POST-ok quadrant in [sync]).
     * Runs at the top of every sync; a no-op (one prefs read, no network) when the slot is empty. Cleared
     * once the DELETE lands (204 or 404), else left to retry on the next sync/foreground.
     */
    private suspend fun drainPendingDelete() {
        val pending = loadPendingDelete() ?: return
        if (unregister(pending)) clearPendingDelete()
    }

    /** `{sidecarBaseUrl}/api/v2/regions/{regionId}/push_registrations` — mirrors the alarms URL build. */
    private fun registrationUrl(registration: PushRegistration): String =
        registration.sidecarBaseUrl +
            registrationsEndpointPath +
            registration.regionId + "/push_registrations"

    /**
     * The registration last successfully sent to the server, or null if none is on record. The token
     * slot doubles as the commit sentinel: [persist] writes it last and [clearLast] nulls it. That
     * ordering is exact for in-process reads (the prefs cache updates synchronously, in call order) but
     * each slot persists to disk as its own async DataStore edit, so it is NOT guaranteed across a
     * process death mid-persist. That's acceptable by design: any torn disk state either reads back as
     * "no record" (missing base/token) → re-registration, which is a safe idempotent upsert, or as a
     * stale record whose DELETE the server answers with a tolerated 404. No recovery path depends on
     * the sentinel being durably last.
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
            description = prefs.getString(KEY_DESCRIPTION, null),
        )
    }

    /**
     * How long ago the on-record registration was sent, or null if that is unknown — no record, or one
     * written before this slot existed. Both the write and this read are device-clock ([WallTime]): it
     * is a purely local elapsed-time question about our own write, never compared against a server
     * timestamp, so no server-clock crossing is involved.
     */
    private fun sinceLastSent(): Duration? {
        val sentAtMs = prefs.getLong(KEY_SENT_AT, 0L).takeIf { it > 0L } ?: return null
        return now() - WallTime(sentAtMs)
    }

    private fun persist(registration: PushRegistration) {
        prefs.setLong(KEY_REGION_ID, registration.regionId)
        prefs.setString(KEY_BASE, registration.sidecarBaseUrl)
        prefs.setString(KEY_LOCALE, registration.locale)
        prefs.setBoolean(KEY_TEST_DEVICE, registration.testDevice)
        prefs.setString(KEY_DESCRIPTION, registration.description)
        // Stamps the refresh clock: [sinceLastSent] measures the 24h re-POST window from here.
        prefs.setLong(KEY_SENT_AT, now().epochMs)
        // Written last: its presence is what [loadLast] treats as "a full record is committed". Exact
        // in-process; only best-effort on disk (see [loadLast] for why a torn persist is still safe).
        prefs.setString(KEY_TOKEN, registration.token)
        // This endpoint is now the live registration, so drop any owed DELETE for it — otherwise a
        // return-to-an-old-endpoint (region/token switched away, then back) would DELETE the row we just
        // POSTed. This is why the single pending-delete slot is safe.
        if (loadPendingDelete()?.sameEndpoint(registration) == true) clearPendingDelete()
    }

    /** Nulls the commit-sentinel token slot so [loadLast] reports no record. */
    private fun clearLast() {
        prefs.setString(KEY_TOKEN, null)
    }

    /**
     * Persists the registration whose DELETE is still outstanding (the DELETE-fail/POST-ok Reregister
     * quadrant) so [drainPendingDelete] can retry it later. Only the DELETE-addressing fields
     * (region + host + token) are stored; locale/test-device don't affect a DELETE. A single slot: a
     * second orphan before the first drains overwrites it — rare (two region changes with both old hosts
     * unreachable back-to-back), and the loser still falls back to the 180-day server prune.
     */
    private fun persistPendingDelete(registration: PushRegistration) {
        prefs.setLong(KEY_PENDING_DEL_REGION_ID, registration.regionId)
        prefs.setString(KEY_PENDING_DEL_BASE, registration.sidecarBaseUrl)
        // Written last: the commit sentinel, mirroring [persist].
        prefs.setString(KEY_PENDING_DEL_TOKEN, registration.token)
    }

    /** The registration awaiting a retried DELETE, or null if none is queued. Metadata is placeholder. */
    private fun loadPendingDelete(): PushRegistration? {
        val base = prefs.getString(KEY_PENDING_DEL_BASE, null) ?: return null
        val token = prefs.getString(KEY_PENDING_DEL_TOKEN, null) ?: return null
        return PushRegistration(
            regionId = prefs.getLong(KEY_PENDING_DEL_REGION_ID, -1L),
            sidecarBaseUrl = base,
            token = token,
            // Unused by [unregister] — a DELETE is addressed by region + host + token only.
            locale = "",
            testDevice = false,
            description = null,
        )
    }

    /** Nulls the pending-delete sentinel token slot so [loadPendingDelete] reports nothing queued. */
    private fun clearPendingDelete() {
        prefs.setString(KEY_PENDING_DEL_TOKEN, null)
    }

    private companion object {
        const val TAG = "PushRegistration"

        // Internal bookkeeping slots (not user-facing) for the last successful registration.
        const val KEY_REGION_ID = "push_reg_region_id"
        const val KEY_BASE = "push_reg_base"
        const val KEY_TOKEN = "push_reg_token"
        const val KEY_LOCALE = "push_reg_locale"
        const val KEY_TEST_DEVICE = "push_reg_test_device"
        const val KEY_DESCRIPTION = "push_reg_description"
        const val KEY_SENT_AT = "push_reg_sent_at"

        // Slots for a registration whose DELETE is owed but not yet confirmed (see [persistPendingDelete]).
        const val KEY_PENDING_DEL_REGION_ID = "push_pending_del_region_id"
        const val KEY_PENDING_DEL_BASE = "push_pending_del_base"
        const val KEY_PENDING_DEL_TOKEN = "push_pending_del_token"
    }
}
