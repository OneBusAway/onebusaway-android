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

import java.io.IOException
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.api.contract.PushRegistrationWebService
import org.onebusaway.android.region.FakeRegionRepository
import org.onebusaway.android.region.region
import org.onebusaway.android.testing.FakePreferencesRepository
import org.onebusaway.android.time.WallTime
import retrofit2.Response

/**
 * Drives [PushRegistrationManager.sync] (through the public [PushRegistrationManager.resync] trigger)
 * over hand fakes to lock down the reconcile → apply → persist semantics [decidePushRegistration] can't
 * see: which endpoint calls fire and, crucially, what the persisted "last registration" record becomes
 * after each success/failure. The manager's two Android-only reads (OS notifications-enabled, the
 * endpoint-path string) are injected as test seams, so this is a plain JVM test with no Robolectric.
 *
 * The headline case is [reregister that fully fails keeps previous so the next sync retries the delete]:
 * a Reregister whose DELETE and POST both fail must NOT forget `previous`, or the stale region/token is
 * orphaned (still pushing) until the 180-day server prune. That path is invisible to the pure decision
 * test and is exactly the kind of bug a persist-semantics test catches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationManagerTest {

    private var previousLocale: Locale? = null

    @Before
    fun pinLocale() {
        // sync() sends Locale.getDefault().toLanguageTag(); pin it so the assertion is stable.
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        previousLocale?.let { Locale.setDefault(it) }
    }

    @Test
    fun `registers and persists, then a no-op when nothing changed`() = runTest {
        val f = Fixture(this)
        f.setToken("T1")
        f.sync()

        val call = f.service.registerCalls.single()
        assertEquals("https://sidecar.test/api/v2/regions/1/push_registrations", call.url)
        assertEquals("T1", call.token)
        assertEquals("en-US", call.locale)
        assertEquals(true, call.testDevice)
        // The server 422s a test_device=true POST without a description.
        assertEquals(DEVICE_DESCRIPTION, call.description)
        assertEquals("android", call.operatingSystem)

        // Same inputs → decide() yields NoOp; no second POST despite the trigger firing again.
        f.sync()
        assertEquals(1, f.service.registerCalls.size)
    }

    @Test
    fun `does nothing when notifications are disabled and nothing is on record`() = runTest {
        val f = Fixture(this)
        f.setToken("T1")
        f.notificationsEnabled = false

        f.sync()

        assertTrue(f.service.registerCalls.isEmpty())
        assertTrue(f.service.unregisterCalls.isEmpty())
    }

    @Test
    fun `opt-out leaves the server row alone - FCM bounces and the server prune own that cleanup`() = runTest {
        val f = Fixture(this).registered("T1")

        // Rider turns notifications off in system settings; the ON_START resync reconciles. An OS-level
        // disable never DELETEs — see [DesiredRegistration.OptedOut] for the rationale.
        f.notificationsEnabled = false
        f.sync()

        assertTrue("opt-out must not DELETE", f.service.unregisterCalls.isEmpty())
        assertEquals("opt-out must not POST either", 1, f.service.registerCalls.size)

        // Re-enabling within the refresh window: the kept record still matches → pure NoOp, no
        // spurious re-POST of an unchanged registration whose server row never went away.
        f.notificationsEnabled = true
        f.sync()
        assertEquals(1, f.service.registerCalls.size)
        assertTrue(f.service.unregisterCalls.isEmpty())
    }

    @Test
    fun `a failed register persists nothing and the next sync retries`() = runTest {
        val f = Fixture(this)
        f.setToken("T1")
        f.service.onRegister = { throw IOException("offline") }

        f.sync()
        assertEquals(1, f.service.registerCalls.size)

        // The failure is surfaced (not swallowed silently) so an on-device report has the cause — but
        // only locally: transport failures are ordinary and self-healing, and reporting every offline
        // moment remotely would drown the systematic-rejection signal.
        assertTrue(f.loggedWarnings.any { it.startsWith("push register failed") })
        assertTrue(f.reportedErrors.isEmpty())

        // Nothing was persisted, so the retry is a full Register again (not a NoOp).
        f.service.onRegister = { Response.success(Unit) }
        f.sync()
        assertEquals(2, f.service.registerCalls.size)

        // Now it's on record → the third sync is a NoOp.
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `token rotation reregisters, deleting the old token and posting the new`() = runTest {
        val f = Fixture(this).registered("T1")

        f.setToken("T2")
        f.sync()

        assertEquals("T1", f.service.unregisterCalls.single().token)
        assertEquals(listOf("T1", "T2"), f.service.registerCalls.map { it.token })

        // The new token is on record now → NoOp.
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `reregister that fully fails keeps previous so the next sync retries the delete`() = runTest {
        val f = Fixture(this).registered("T1")

        // Region switch mid-offline: both the DELETE of T1 and the POST of T2 fail.
        f.setToken("T2")
        f.service.onUnregister = { throw IOException("offline") }
        f.service.onRegister = { throw IOException("offline") }
        f.sync()

        // Back online: the record must still point at T1 so this sync re-runs the full Reregister
        // (DELETE T1 + POST T2). If `previous` had been forgotten, this would be a plain Register(T2)
        // and T1 would never be deleted — the orphan bug.
        f.service.onUnregister = { Response.success(Unit) }
        f.service.onRegister = { Response.success(Unit) }
        f.sync()

        assertEquals(2, f.service.unregisterCalls.count { it.token == "T1" })

        // T2 is finally on record → NoOp, no further POSTs.
        val postsSoFar = f.service.registerCalls.size
        f.sync()
        assertEquals(postsSoFar, f.service.registerCalls.size)
    }

    @Test
    fun `reregister clears the record when the delete succeeds but the post fails`() = runTest {
        val f = Fixture(this).registered("T1")

        // DELETE of T1 lands, POST of T2 fails → the server holds nothing, so the record must clear.
        f.setToken("T2")
        f.service.onRegister = { throw IOException("offline") }
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)

        // Record cleared → the retry is a plain Register(T2), with no further DELETE.
        f.service.onRegister = { Response.success(Unit) }
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)
        assertEquals("T2", f.service.registerCalls.last().token)
    }

    @Test
    fun `reregister with a failed delete but successful post drops the delete, best-effort`() = runTest {
        val f = Fixture(this).registered("T1")

        // Reregister where DELETE(T1) fails but POST(T2) lands: T2 is recorded and the missed DELETE is
        // deliberately dropped — the old row falls back to the server's 180-day prune, which is the iOS
        // client's baseline for every region change (see applyReregister).
        f.setToken("T2")
        f.service.onUnregister = { throw IOException("offline") }
        f.sync()

        assertEquals(listOf("T1", "T2"), f.service.registerCalls.map { it.token })
        assertEquals(1, f.service.unregisterCalls.count { it.token == "T1" })

        // T2 is settled on record: further syncs neither retry the DELETE nor re-POST. (onUnregister is
        // left throwing, so this pins that no DELETE is even *attempted*.)
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `unregister treats a 404 as already-gone and clears the record`() = runTest {
        val f = Fixture(this).registered("T1")

        // Moving to a sidecar-less region is the path that unregisters (an OS opt-out doesn't).
        f.regions.emit(region(2).copy(sidecarBaseUrl = ""))
        f.service.onUnregister = { Response.error(404, "".toResponseBody(null)) }
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)

        // 404 counts as removed → the record is cleared, so a further resync is a NoOp.
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)
    }

    @Test
    fun `an ordinary registration omits the description`() = runTest {
        val f = Fixture(this)
        f.prefs.setBoolean(R.string.preference_key_push_test_device, false)
        f.setToken("T1")
        f.sync()

        // Null means Retrofit drops the field: the server doesn't require it here, and an ordinary
        // rider's device name has no business being sent.
        val call = f.service.registerCalls.single()
        assertEquals(false, call.testDevice)
        assertEquals(null, call.description)
    }

    @Test
    fun `an unnamed test device registers as an ordinary device instead of failing`() = runTest {
        val f = Fixture(this)
        // Flag on, but no name: the server would 422 a test_device=true POST with a blank description,
        // so we must downgrade rather than send a request guaranteed to fail (matching iOS).
        f.prefs.setString(R.string.preference_key_push_test_device_name, null)
        f.setToken("T1")
        f.sync()

        val call = f.service.registerCalls.single()
        assertEquals(false, call.testDevice)
        assertEquals(null, call.description)
        assertTrue("a downgraded registration must still succeed", f.loggedWarnings.isEmpty())
    }

    @Test
    fun `an over-long test device name is truncated to the server's limit`() = runTest {
        val f = Fixture(this)
        // The settings write boundary caps this, so an over-long value can only arrive by writing the
        // pref directly (as here). Guard it at the wire anyway: OBACloud 422s a description over 255
        // chars, and a rejected registration persists nothing, so the doomed POST would repeat on
        // every foreground.
        f.prefs.setString(R.string.preference_key_push_test_device_name, "N".repeat(300))
        f.setToken("T1")
        f.sync()

        val call = f.service.registerCalls.single()
        assertEquals(true, call.testDevice)
        assertEquals("N".repeat(PUSH_DESCRIPTION_MAX_LENGTH), call.description)
    }

    @Test
    fun `renaming the test device re-posts the new name without a delete`() = runTest {
        val f = Fixture(this).registered("T1")
        assertEquals(DEVICE_DESCRIPTION, f.service.registerCalls.single().description)

        f.prefs.setString(R.string.preference_key_push_test_device_name, "Renamed Device")
        f.sync()

        // Same endpoint (region + host + token), so this upserts in place — no DELETE.
        assertEquals("Renamed Device", f.service.registerCalls.last().description)
        assertTrue(f.service.unregisterCalls.isEmpty())

        // Now on record → settled.
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `an unchanged registration is re-posted once a day to defeat the 180-day prune`() = runTest {
        val f = Fixture(this).registered("T1")
        assertEquals(1, f.service.registerCalls.size)

        // Same inputs, an hour later: still a no-op.
        f.now += 1.hours
        f.sync()
        assertEquals(1, f.service.registerCalls.size)

        // Past the refresh window: re-POST so the server's last_seen_at is refreshed.
        f.now += PUSH_REFRESH_INTERVAL
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
        assertTrue("the keep-alive must not DELETE anything", f.service.unregisterCalls.isEmpty())

        // The refresh restamps the clock, so the very next sync is quiet again.
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `a backwards device-clock jump refreshes once instead of starving the keep-alive`() = runTest {
        val f = Fixture(this).registered("T1")

        // The clock is set back past the record's stamp: elapsed-since-sent would read negative, which
        // must count as stale — otherwise the keep-alive stays silent until the clock catches back up,
        // within reach of the 180-day prune for a large jump.
        f.now -= 2.hours
        f.sync()
        assertEquals(2, f.service.registerCalls.size)

        // The refresh restamped at the new clock, so the next sync is quiet again.
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `a missing region is treated as not-yet-known rather than resolved`() = runTest {
        val f = Fixture(this).registered("T1")

        // The region flow is briefly null at cold start (RegionRepository seeds it asynchronously).
        // Treating that as no-sidecar would DELETE the registration and re-POST seconds later.
        f.regions.emit(null)
        f.sync()

        assertTrue("a null region must not trigger a DELETE", f.service.unregisterCalls.isEmpty())
        assertEquals(1, f.service.registerCalls.size)

        // Once the region resolves, the unchanged registration is still on record → no re-POST.
        f.regions.emit(region(1).copy(sidecarBaseUrl = "https://sidecar.test"))
        f.sync()
        assertEquals(1, f.service.registerCalls.size)
    }

    @Test
    fun `switching to a region without a sidecar unregisters the stale registration`() = runTest {
        val f = Fixture(this).registered("T1")

        // Region 2 has no sidecar host (the field defaults to ""). Unlike the null region above, this
        // is a *resolved* fact, not a still-settling input — so the region-1 registration must be
        // reconciled away, or the device keeps receiving region 1's alerts (and no region-2 ones)
        // until the server's 180-day prune.
        f.regions.emit(region(2).copy(sidecarBaseUrl = ""))
        f.sync()

        val delete = f.service.unregisterCalls.single()
        assertEquals("T1", delete.token)
        // The DELETE targets the *old* registration's host — the current region has none.
        assertTrue(delete.url, delete.url.startsWith("https://sidecar.test"))

        // Record cleared and nothing to register → settled: no re-POST, no duplicate DELETE.
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)
        assertEquals(1, f.service.registerCalls.size)
    }

    @Test
    fun `an HTTP error response is logged and reported, not silently swallowed`() = runTest {
        val f = Fixture(this)
        f.setToken("T1")
        // The real regression: a 422 is a *successful call* returning an unsuccessful response, so it
        // never reaches runCatching's onFailure. It used to vanish entirely.
        f.service.onRegister = {
            Response.error(422, """{"error":"Unable to register device"}""".toResponseBody(null))
        }

        f.sync()

        val warning = f.loggedWarnings.single()
        assertTrue("status code must be logged: $warning", warning.contains("422"))
        assertTrue("server error body must be logged: $warning", warning.contains("Unable to register device"))
        // The token must never reach the log.
        assertTrue("token must not be logged: $warning", !warning.contains("T1"))

        // Registrations are the server's only audience source, so a systematic rejection must also be
        // visible remotely (Crashlytics), not just in one developer's logcat.
        val reported = f.reportedErrors.single()
        assertTrue("$reported", reported is PushRegistrationException)
        assertTrue("$reported", reported.message.orEmpty().contains("422"))
    }

    @Test
    fun `a throttled register is logged locally but not reported, and retries next sync`() = runTest {
        val f = Fixture(this)
        f.setToken("T1")
        // A 429 says nothing about this client (see [PushRegistrationClient.isTransient]), and since a
        // failed register persists nothing, every foreground would re-report it.
        f.service.onRegister = { Response.error(429, "".toResponseBody(null)) }

        f.sync()

        assertTrue("throttle must still be logged", f.loggedWarnings.single().contains("429"))
        assertTrue("throttle must not be reported remotely", f.reportedErrors.isEmpty())

        // Transient means the next reconcile retries; once the throttle lifts, registration succeeds.
        f.service.onRegister = { Response.success(Unit) }
        f.sync()
        assertEquals(2, f.service.registerCalls.size)
    }

    @Test
    fun `a failed unregister response is logged too, and a 5xx is not reported remotely`() = runTest {
        val f = Fixture(this).registered("T1")

        f.regions.emit(region(2).copy(sidecarBaseUrl = ""))
        f.service.onUnregister = { Response.error(500, "boom".toResponseBody(null)) }
        f.sync()

        val warning = f.loggedWarnings.single()
        assertTrue("status code must be logged: $warning", warning.contains("500"))
        // Server-side trouble is transient and shared-fate across the fleet — local log only.
        assertTrue("a 5xx must not be reported remotely", f.reportedErrors.isEmpty())
    }

    /**
     * A ready-to-drive manager over fakes: notifications on, one region (id 1 + sidecar), the
     * test-device flag on, and no token until a test sets one. [notificationsEnabled] is a mutable flag
     * so a test can flip the OS opt-in between syncs.
     */
    private class Fixture(private val scope: TestScope) {
        val service = FakePushRegistrationWebService()
        val prefs = FakePreferencesRepository().apply {
            setBoolean(R.string.preference_key_push_test_device, true)
            // Named, so the test-device flag is honoured rather than downgraded (see deriveDesiredRegistration).
            setString(R.string.preference_key_push_test_device_name, DEVICE_DESCRIPTION)
        }
        val regions = FakeRegionRepository(region(1).copy(sidecarBaseUrl = "https://sidecar.test"))
        var notificationsEnabled = true

        /** Mutable device clock, so a test can jump past [PUSH_REFRESH_INTERVAL]. */
        var now = WallTime(1_000_000_000L)

        /** Server rejections routed to the remote error channel (Crashlytics in production). */
        val reportedErrors = mutableListOf<Throwable>()

        /** Captures every warning the manager logs (android.util.Log isn't mocked under a JVM test). */
        val loggedWarnings = mutableListOf<String>()

        // The real store and client over fakes: these tests are about the reconcile → apply → record
        // loop as a whole, so exercising the actual persistence and failure-classification code (rather
        // than fakes of it) is the point.
        val manager = PushRegistrationManager(
            client = PushRegistrationClient(
                service = service,
                regionsPath = "/api/v2/regions/",
                logWarning = { message, _ -> loggedWarnings += message },
                reportError = { reportedErrors += it }
            ),
            store = PushRegistrationStore(prefs = prefs, now = { now }),
            regionRepository = regions,
            firebaseMessagingManager = FirebaseMessagingManager(prefs),
            prefs = prefs,
            scope = scope,
            notificationsEnabled = { notificationsEnabled }
        )

        fun setToken(token: String) = prefs.setString(R.string.firebase_messaging_token, token)

        /** Fires the reconcile trigger and drains it — the manager runs sync() off resync()'s launch. */
        fun sync() {
            manager.resync()
            scope.advanceUntilIdle()
        }

        /** Establishes a "[token] already registered and on record" baseline before the scenario. */
        fun registered(token: String): Fixture = apply {
            setToken(token)
            sync()
        }
    }

    /** Records every call and returns programmable outcomes (default: success). */
    private class FakePushRegistrationWebService : PushRegistrationWebService {
        data class RegisterCall(
            val url: String,
            val token: String,
            val locale: String,
            val testDevice: Boolean,
            val description: String?,
            val operatingSystem: String
        )

        data class UnregisterCall(val url: String, val token: String)

        val registerCalls = mutableListOf<RegisterCall>()
        val unregisterCalls = mutableListOf<UnregisterCall>()

        var onRegister: () -> Response<Unit> = { Response.success(Unit) }
        var onUnregister: () -> Response<Unit> = { Response.success(Unit) }

        override suspend fun register(
            url: String,
            token: String,
            locale: String,
            testDevice: Boolean,
            description: String?,
            operatingSystem: String
        ): Response<Unit> {
            registerCalls += RegisterCall(url, token, locale, testDevice, description, operatingSystem)
            return onRegister()
        }

        override suspend fun unregister(url: String, token: String): Response<Unit> {
            unregisterCalls += UnregisterCall(url, token)
            return onUnregister()
        }
    }

    private companion object {
        /** Stands in for the name the rider types into the "Test device name" setting. */
        const val DEVICE_DESCRIPTION = "Sam's Test Pixel"
    }
}
