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
        // currentTarget() sends Locale.getDefault().toLanguageTag(); pin it so the assertion is stable.
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
    fun `opt-out unregisters the recorded token and clears the record`() = runTest {
        val f = Fixture(this).registered("T1")

        // Rider turns notifications off in system settings; the ON_START resync reconciles.
        f.notificationsEnabled = false
        f.sync()

        assertEquals("T1", f.service.unregisterCalls.single().token)

        // Record cleared → a further resync is a NoOp (no duplicate DELETE).
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)
    }

    @Test
    fun `a failed register persists nothing and the next sync retries`() = runTest {
        val f = Fixture(this)
        f.setToken("T1")
        f.service.onRegister = { throw IOException("offline") }

        f.sync()
        assertEquals(1, f.service.registerCalls.size)

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
    fun `unregister treats a 404 as already-gone and clears the record`() = runTest {
        val f = Fixture(this).registered("T1")

        f.notificationsEnabled = false
        f.service.onUnregister = { Response.error(404, "".toResponseBody(null)) }
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)

        // 404 counts as removed → the record is cleared, so a further resync is a NoOp.
        f.sync()
        assertEquals(1, f.service.unregisterCalls.size)
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
        }
        val regions = FakeRegionRepository(region(1).copy(sidecarBaseUrl = "https://sidecar.test"))
        var notificationsEnabled = true

        val manager = PushRegistrationManager(
            service = service,
            regionRepository = regions,
            firebaseMessagingManager = FirebaseMessagingManager(prefs),
            prefs = prefs,
            scope = scope,
            notificationsEnabled = { notificationsEnabled },
            registrationsEndpointPath = "/api/v2/regions/",
        )

        fun setToken(token: String?) = prefs.setString(R.string.firebase_messaging_token, token)

        /** Fires the reconcile trigger and drains it — the manager runs sync() off resync()'s launch. */
        fun sync() {
            manager.resync()
            scope.advanceUntilIdle()
        }

        /** Establishes a "[token] already registered and on record" baseline before the scenario. */
        fun registered(token: String): Fixture = apply { setToken(token); sync() }
    }

    /** Records every call and returns programmable outcomes (default: success). */
    private class FakePushRegistrationWebService : PushRegistrationWebService {
        data class RegisterCall(
            val url: String,
            val token: String,
            val locale: String,
            val testDevice: Boolean,
            val operatingSystem: String,
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
            operatingSystem: String,
        ): Response<Unit> {
            registerCalls += RegisterCall(url, token, locale, testDevice, operatingSystem)
            return onRegister()
        }

        override suspend fun unregister(url: String, token: String): Response<Unit> {
            unregisterCalls += UnregisterCall(url, token)
            return onUnregister()
        }
    }
}
