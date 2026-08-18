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
package org.onebusaway.android.api.net

import android.net.Uri
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.onebusaway.android.api.ObaApiException
import org.onebusaway.android.api.asObaFailure
import org.onebusaway.android.api.contract.ObaWebService
import org.onebusaway.android.demo.DemoModeController
import org.onebusaway.android.util.runCatchingCancellable
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds — and rebuilds — the region-bound [ObaWebService]. The OBA host isn't known until a region
 * is selected and changes when the user switches regions, so rather than a fixed Retrofit on a
 * throwaway base URL (with a per-request host rewrite), this holds a Retrofit built against the
 * *real* base URL from [ObaEndpointResolver] and rebuilds it the first time it's used after that URL
 * changes. The shared [OkHttpClient] (carrying [ApiParamsInterceptor]) is reused across rebuilds —
 * only the lightweight Retrofit proxy is replaced. Region switches are rare, so the rebuild-on-change
 * cost is negligible.
 *
 * This is also the app's single OBA seam — every one of the `api/data` sources reaches the API through
 * [call] / [callOrNull] — which is what lets the scripted tutorial's demo mode (#2164) substitute a
 * whole offline deployment here ([DemoModeController.obaService]) without any data source, repository,
 * view model or screen knowing about it.
 */
@Singleton
class ObaApiProvider @Inject constructor(
    private val resolver: ObaEndpointResolver,
    private val client: OkHttpClient,
    private val json: Json,
    private val demoMode: DemoModeController
) {
    private var cachedBase: Uri? = null
    private var cachedService: ObaWebService? = null

    /**
     * Runs [block] against the [ObaWebService] for the current region and maps the outcome to a
     * [Result]. Yields [Result.failure] when there's no endpoint to contact (no current region and no
     * custom API URL) or when [block] throws (a non-OK app code, transport, or parse failure) — so the
     * common "no region is an error" callers get one uniform failure path without a try/catch.
     *
     * A failure the OBA layer itself stated is normalized to [ObaApiException] here (see
     * [asObaFailure]), whichever side of the HTTP status/envelope divide it arrived on, so callers
     * classify app-level codes on one type.
     */
    suspend fun <T> call(block: suspend (ObaWebService) -> T): Result<T> {
        if (demoMode.isActive) return attempt { onDemoDeployment(block) }
        val service = service() ?: return Result.failure(noEndpoint())
        return attempt { block(service) }
    }

    /**
     * Like [call], but yields `success(null)` when there's no endpoint yet — for callers (e.g. the map)
     * that treat "no region" as nothing-to-show rather than an error.
     */
    suspend fun <T> callOrNull(block: suspend (ObaWebService) -> T): Result<T?> {
        if (demoMode.isActive) return attempt { onDemoDeployment(block) }
        val service = service() ?: return Result.success(null)
        return attempt { block(service) }
    }

    /**
     * Runs [block] against the demo deployment, off whatever thread asked for it.
     *
     * Reached ahead of [service] rather than from inside it, so the demo tour needs no endpoint at all:
     * it runs with no region selected, no API key and no network, which is precisely the fragility in
     * the live-data tutorial that #2164 set out to remove.
     *
     * **The service is resolved inside the `withContext`, not outside it.** `demoMode.obaService` is a
     * `by lazy` that reads and parses the bundled 21 KB fixture on whoever touches it first, so
     * resolving it at the call site would put that parse on the caller's thread — the very thing this
     * function exists to prevent. `DemoModeController.prepare()` normally warms it before the tour
     * enters, but that is the tour being polite, not a guarantee this seam can lean on.
     *
     * The live path is main-safe for free — Retrofit dispatches its own IO, which is why the data
     * sources above here deliberately carry no `withContext` of their own. The demo deployment is a
     * plain in-process simulation, so it runs on its caller's dispatcher instead, and several of those
     * callers poll from `viewModelScope` on Main: a whole viewport's worth of arrivals — every stop,
     * every route, a vehicle status per arrival — would be simulated on the main thread on each tick,
     * while the tour's spotlight ring is animating over it. It is CPU work over an in-memory fixture
     * rather than IO, so [Dispatchers.Default] is its home.
     *
     * NEEDS AN ON-DEVICE PASS: an earlier attempt at this reportedly stalled, and nothing in the code
     * explains why — the demo service is a pure function of the fixture and the clock, and takes no
     * locks. The one mechanism that would fit is pool contention: the map's own stop pipeline runs
     * `flowOn(Dispatchers.Default)` and route-shape decoding shares that same small pool, so several
     * long simulated responses can queue the map behind them. If it recurs, [Dispatchers.IO] is the
     * escape hatch — a worse fit for CPU work, but elastic, and the demo service is standing in for
     * calls its callers believe are network-bound anyway.
     */
    private suspend fun <T> onDemoDeployment(block: suspend (ObaWebService) -> T): T = withContext(Dispatchers.Default) { block(demoMode.obaService) }

    /**
     * Which deployment is answering right now — the demo transit system, or the resolved OBA base URL
     * (null when there is no endpoint yet).
     *
     * An OBA id is only unique *within* a deployment: agency, route, stop and trip ids all come from
     * that server's feed, and two servers can and do use the same ones. So anything that caches a
     * response for longer than one call has to key on this alongside the id, or a deployment change
     * silently serves one server's answer for another's question. Two changes make that reachable: the
     * rider switching regions, and the scripted tutorial's demo mode (#2164), whose bundled fixture is
     * a capture of a real deployment and therefore carries that deployment's real ids.
     *
     * Exposed rather than derived by each cache so there is one answer to "whose data is this?".
     */
    val deploymentKey: String
        get() = if (demoMode.isActive) DEMO_DEPLOYMENT else resolver.baseUrl()?.toString() ?: NO_DEPLOYMENT

    /** Runs [block], mapping an OBA-stated failure to [ObaApiException] and keeping cancellation out. */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = runCatchingCancellable { block() }
        .recoverCatching { throw it.asObaFailure(json) }

    /**
     * The [ObaWebService] bound to the current region's base URL, or null when there's no endpoint to
     * contact yet. Rebuilds when the base URL has changed. Private: callers go through [call] /
     * [callOrNull] so the no-endpoint policy lives in one place.
     */
    @Synchronized
    private fun service(): ObaWebService? {
        val base = resolver.baseUrl()
        if (base == null) {
            cachedBase = null
            cachedService = null
            return null
        }
        // cachedBase and cachedService are only ever set or cleared together, so comparing the base
        // alone is enough — a real base never equals a null cachedBase.
        if (base != cachedBase) {
            cachedService = build(base)
            cachedBase = base
        }
        return cachedService
    }

    private fun noEndpoint() = IOException("No OBA API endpoint: no current region and no custom API URL set")

    private fun build(base: Uri): ObaWebService {
        // Retrofit requires the base URL to end in '/' so the endpoints' relative paths resolve onto it.
        val baseUrl = base.toString().let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ObaWebService::class.java)
    }

    private companion object {
        /** [deploymentKey] for the bundled demo transit system, which has no base URL of its own. */
        const val DEMO_DEPLOYMENT = "demo"

        /** [deploymentKey] before a region resolves. Nothing is fetched then, so nothing is cached. */
        const val NO_DEPLOYMENT = ""
    }
}
