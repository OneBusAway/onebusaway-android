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
package org.onebusaway.android.ui.widget

import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.onebusaway.android.api.net.ObaEndpointResolver
import org.onebusaway.android.demo.DemoModeController
import org.onebusaway.android.region.Region
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.region.RegionState

/**
 * The OBA server a widget's stop and route ids belong to. Ids are only unique per server and fetches go
 * to the app's current one, so a widget pauses while the app is on another.
 *
 * Exactly one of [customApiUrl] / [regionId] is set; a custom URL overrides the region, as in
 * [ObaEndpointResolver.baseUrl]. Regions are keyed by id, not base URL, so a server migration doesn't
 * strand widgets. [displayName] is only for the paused message.
 */
@Serializable
data class WidgetDeployment(
    val customApiUrl: String?,
    val regionId: Long?,
    val displayName: String
) {
    init {
        require((customApiUrl == null) != (regionId == null)) { "exactly one of customApiUrl / regionId must be set" }
    }

    /** Whether [other] is the same server, whatever either is called. */
    fun isSameServerAs(other: WidgetDeployment): Boolean = customApiUrl == other.customApiUrl && regionId == other.regionId

    companion object {
        fun forCustomUrl(url: String) = WidgetDeployment(customApiUrl = url, regionId = null, displayName = url)

        fun forRegion(region: Region) = WidgetDeployment(customApiUrl = null, regionId = region.id, displayName = region.name)
    }
}

/** Which server the app's OBA requests reach right now, as far as a widget is concerned. */
sealed interface CurrentServer {

    /** Not known yet: the saved region loads asynchronously at cold start. Wait, or keep showing the cache. */
    data object Loading : CurrentServer

    /** No usable server: none set, or the tutorial's demo mode (whose fixture reuses real ids). */
    data object Unavailable : CurrentServer

    data class On(val deployment: WidgetDeployment) : CurrentServer
}

/** Resolves [CurrentServer], mirroring [ObaEndpointResolver.baseUrl]'s precedence. */
class WidgetDeployments @Inject constructor(
    private val resolver: ObaEndpointResolver,
    private val regionRepository: RegionRepository,
    private val demoMode: DemoModeController
) {
    fun current(): CurrentServer {
        if (demoMode.isActive) return CurrentServer.Unavailable
        resolver.customApiUrl()?.let { return CurrentServer.On(WidgetDeployment.forCustomUrl(it)) }
        // State before region: RegionStateHolder.activated sets the region first, so a settled state is
        // never read with a stale null region. (A later refresh keeps the last region; only cold start is Loading.)
        val resolving = regionRepository.state.value == RegionState.Resolving
        val region = regionRepository.region.value ?: return if (resolving) CurrentServer.Loading else CurrentServer.Unavailable
        if (resolver.baseUrl() == null) return CurrentServer.Unavailable // a region publishing no OBA server
        return CurrentServer.On(WidgetDeployment.forRegion(region))
    }

    /** [current], first waiting out a cold-start region load; still [CurrentServer.Loading] if it never settles. */
    suspend fun awaitCurrent(timeout: Duration): CurrentServer {
        withTimeoutOrNull(timeout) { regionRepository.state.first { it != RegionState.Resolving || regionRepository.region.value != null } }
        return current()
    }
}

/** Whether this widget's server is the one the app is on — false while that's still [CurrentServer.Loading]. */
fun WidgetConfig.isServedBy(current: CurrentServer): Boolean = current is CurrentServer.On && deployment.isSameServerAs(current.deployment)
