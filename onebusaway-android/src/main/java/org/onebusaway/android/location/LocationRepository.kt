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
package org.onebusaway.android.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.util.LocationHelper
import org.onebusaway.android.util.LocationUtils

/**
 * The observable last-known device location and the single owner of location *production* for the app.
 * Features read the current value ([lastKnownLocation]) or observe changes by collecting [location] /
 * [locationUpdates].
 *
 * This is both the single source of truth and the single producer: it owns one device feed shared by
 * the whole app (the map's keep-alive and `NavigationService`'s nav updates), so there is a single set
 * of OS location registrations. One process-singleton instance is shared via Hilt; tests substitute a
 * fake. The write side — ingesting raw fixes — lives on [LocationSink] so this read contract stays
 * read-only.
 */
interface LocationRepository {

    /**
     * The last-known location, or null until one is established. **Live** while any consumer holds the
     * feed open ([startUpdates] / a [locationUpdates] collector) and seeded on start, so observers see
     * real movement; otherwise it holds whatever the last feed or [lastKnownLocation] poll established.
     * One-shot callers that just need a synchronous best-effort value should use [lastKnownLocation].
     */
    val location: StateFlow<Location?>

    /**
     * The last-known location, polled synchronously. Returns [location]'s current value, lazily polling
     * the providers to seed it the first time none has been established. This is the accessor for
     * one-shot callers with no active feed (region selection, the report flow); during an active feed
     * it agrees with [location].
     */
    fun lastKnownLocation(): Location?

    /**
     * Holds the shared location feed open for the foreground map without consuming fixes — keeps
     * [location] a live stream (and [lastKnownLocation] fresh) for as long as the map is shown.
     * Idempotent and permission-gated (the feed retries registration until permission is granted), so
     * it's safe to call on every resume and after a permission grant. Pair with [stopUpdates].
     */
    fun startUpdates()

    /** Releases the map's feed hold taken by [startUpdates]. */
    fun stopUpdates()

    /**
     * A cold [Flow] of device fixes (the post-gate "best" [Location]). Collecting it holds the shared
     * feed open and requests at least [intervalSeconds] cadence; the single underlying producer runs at
     * the *minimum* interval any active consumer asks for. Cancelling the collection releases the
     * demand.
     */
    fun locationUpdates(intervalSeconds: Int): Flow<Location>
}

/**
 * The write side of the location store: ingests raw fixes from the device listener. Kept separate from
 * the read-facing [LocationRepository] so consumers see a read-only contract; only the device-listener
 * path (reaching it via [org.onebusaway.android.app.di.LocationEntryPoint]) holds a sink.
 */
interface LocationSink {

    /**
     * Offers a raw location from the device listener. Publishes it (as a fresh copy) iff it is "better"
     * than the current value per [LocationUtils.compareLocations]; returns whether it was accepted (the
     * caller uses this to keep the location-derived magnetic declination in sync).
     */
    fun update(raw: Location?): Boolean
}

/**
 * Default implementation: owns the canonical [MutableStateFlow], the provider-polling producer, and the
 * one shared live feed. A Hilt [Singleton] constructed lazily on first injection; it starts null and
 * fills from the feed or the lazy poll ([lastKnownLocation]).
 */
@Singleton
class DefaultLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository, LocationSink {

    private val _location = MutableStateFlow<Location?>(null)

    override val location: StateFlow<Location?> = _location.asStateFlow()

    // The reactive shared feed: every consumer adds its requested interval to `demand`; the producer
    // launched below runs ONE LocationHelper at the minimum requested interval, rebuilding (via
    // collectLatest) when that minimum changes and tearing down when demand drops to zero — so the
    // whole app has a single set of OS registrations. Each fix reaches `_location` through
    // LocationHelper.onLocationChanged -> Application.setLastKnownLocation -> update() (which also
    // refreshes the magnetic declination); the feed's own listener is a no-op that just starts the
    // providers. Consumers that want the fixes collect [locationUpdates]; the map only holds the feed
    // open via start/stopUpdates.
    private val demand = MutableStateFlow<List<Int>>(emptyList())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The map's hold is idempotent (start/stopUpdates fire on every resume/pause), so track it apart
    // from the multiset `demand` to add/remove its interval exactly once.
    private var mapHoldActive = false

    init {
        scope.launch {
            demand.map { it.minOrNull() }
                .distinctUntilChanged()
                .collectLatest { interval -> if (interval != null) runFeed(interval) }
        }
    }

    /**
     * Runs one [LocationHelper] at [intervalSeconds] until cancelled — which `collectLatest` does when
     * the minimum requested interval changes or demand drops to zero. A no-op keep-alive listener
     * starts the providers; fixes are ingested via the helper's `Application.setLastKnownLocation` path.
     * Retries registration until permission is granted (or this run is cancelled).
     */
    private suspend fun runFeed(intervalSeconds: Int) {
        val helper = LocationHelper(context, intervalSeconds)
        val keepAlive = LocationHelper.Listener { /* no-op: fixes reach _location via the ingestion path */ }
        try {
            while (!helper.registerListener(keepAlive)) {
                delay(PERMISSION_RETRY_MS) // permission not granted yet — retry until it is
            }
            awaitCancellation()
        } finally {
            helper.unregisterListener(keepAlive)
        }
    }

    @Synchronized
    override fun startUpdates() {
        if (mapHoldActive) return
        mapHoldActive = true
        ensureSeeded()
        demand.update { it + MAP_UPDATE_INTERVAL_SECONDS }
    }

    @Synchronized
    override fun stopUpdates() {
        if (!mapHoldActive) return
        mapHoldActive = false
        demand.update { it - MAP_UPDATE_INTERVAL_SECONDS } // removes one occurrence
    }

    override fun locationUpdates(intervalSeconds: Int): Flow<Location> =
        location.filterNotNull()
            .onStart {
                ensureSeeded()
                demand.update { it + intervalSeconds }
            }
            .onCompletion {
                demand.update { it - intervalSeconds } // removes one occurrence (also on cancellation)
            }

    /**
     * Seeds [_location] from the cached fix (the lazy [lastKnownLocation] poll) the first time a
     * consumer arrives, so observers + the `.value` read see a value before the first live callback.
     */
    private fun ensureSeeded() {
        if (_location.value == null) {
            lastKnownLocation()
        }
    }

    @Synchronized
    override fun lastKnownLocation(): Location? {
        if (_location.value == null) {
            try {
                _location.value = poll()
            } catch (e: SecurityException) {
                Log.e(TAG, "User may have denied location permission - $e")
            }
        }
        return _location.value
    }

    @Synchronized
    override fun update(raw: Location?): Boolean {
        // compareLocations already rejects a null candidate; the explicit check also lets `raw`
        // smart-cast to non-null for the copy below.
        if (raw == null || !LocationUtils.compareLocations(raw, _location.value)) {
            return false
        }
        // A fresh copy: the StateFlow dedupes by reference (Location has no value equality).
        _location.value = Location(raw)
        return true
    }

    /**
     * Considers both Google Play Services (if available) and the Android Location API, returning the
     * more recent.
     */
    @Throws(SecurityException::class)
    private fun poll(): Location? {
        var playServices: Location? = null
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            == ConnectionResult.SUCCESS
        ) {
            val task = LocationServices.getFusedLocationProviderClient(context).lastLocation
            // isSuccessful (not isComplete) — a task that completed with a failure would throw
            // RuntimeExecutionException from getResult().
            if (task.isSuccessful) {
                playServices = task.result
                Log.d(TAG, "Got location from Google Play Services, testing against API v1...")
            }
        }
        val apiV1 = pollApiV1()
        return if (LocationUtils.compareLocationsByTime(playServices, apiV1)) {
            Log.d(TAG, "Using location from Google Play Services")
            playServices
        } else {
            Log.d(TAG, "Using location from Location API v1")
            apiV1
        }
    }

    private fun pollApiV1(): Location? {
        val mgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var last: Location? = null
        for (provider in mgr.getProviders(true)) {
            val loc = try {
                mgr.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                Log.w(TAG, "User may have denied location permission - $e")
                null
            }
            // Keep this provider's location if we have none yet, or it is newer than what we have.
            if (LocationUtils.compareLocationsByTime(loc, last)) {
                last = loc
            }
        }
        return last
    }

    private companion object {
        const val TAG = "LocationRepository"

        /** The map's keep-alive cadence (seconds); nav requests finer via [locationUpdates]. */
        const val MAP_UPDATE_INTERVAL_SECONDS = 5

        /** How often to retry feed registration while the location permission is still ungranted. */
        const val PERMISSION_RETRY_MS = 2000L
    }
}
