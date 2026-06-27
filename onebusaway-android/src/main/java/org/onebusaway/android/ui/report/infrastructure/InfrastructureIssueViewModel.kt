/*
 * Copyright (C) 2014 University of South Florida,
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
package org.onebusaway.android.ui.report.infrastructure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.onebusaway.android.report.TripReportContext
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.report.constants.ReportConstants

/**
 * Orchestrates the infrastructure-issue container: tracks the [IssueLocation], loads Open311
 * categories + reverse-geocodes the address when the location changes, and routes a category
 * selection to a [ReportTarget] (stop form / trip form / Open311). Replaces the bulk of the legacy
 * InfrastructureIssueActivity plus IssueLocationHelper.
 *
 * The Open311 library stays quarantined: the chosen endpoint and each category's `Service` are held
 * as opaque values ([open311], [ServiceListItem.Category.raw]) that the host casts when launching
 * the Open311 form, so this ViewModel never imports edu.usf.cutr.
 */
class InfrastructureIssueViewModel(
    private val serviceListRepository: ServiceListRepository,
    private val geocodeRepository: GeocodeAddressRepository,
    initialLocation: GeoPoint,
    initialStop: ObaStop?,
    private val defaultIssueType: DefaultIssueType,
    private var arrivalInfo: TripReportContext?,
    private val agencyName: String?,
    private val blockId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InfrastructureIssueUiState(
            location = IssueLocation(initialLocation.latitude, initialLocation.longitude, initialStop),
            busStopName = initialStop?.name,
            servicesVisible = initialStop != null
        )
    )
    val uiState: StateFlow<InfrastructureIssueUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<InfrastructureIssueEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<InfrastructureIssueEvent> = _events.asSharedFlow()

    /** The chosen library Open311 endpoint, opaque to this ViewModel; cast by the host. */
    var open311: Any? = null
        private set

    private var allTransitHeuristicMatch = false
    private var pendingTransitTypeWithoutStop: String? = null
    private var selectedCategory: ServiceListItem.Category? = null
    private var pendingDefaultIssueType = defaultIssueType

    init {
        loadForLocation()
    }

    // --- Map + address inputs -------------------------------------------------------------------

    /** The map reported a stop focus change (stop is null when focus is lost). */
    fun onMapFocusChanged(stop: ObaStop?, latitude: Double, longitude: Double) {
        clearReportingTarget()
        if (stop != null) {
            _uiState.update {
                it.copy(
                    location = IssueLocation(latitude, longitude, stop),
                    busStopName = stop.name,
                    servicesVisible = true,
                    markerLocation = null,
                    showStopPrompt = false
                )
            }
            val deferredType = pendingTransitTypeWithoutStop
            pendingTransitTypeWithoutStop = null
            if (deferredType != null) {
                showTransitService(deferredType)
            }
            loadForLocation()
        } else {
            _uiState.update {
                it.copy(
                    location = IssueLocation(latitude, longitude, null),
                    busStopName = null
                )
            }
            loadForLocation()
        }
    }

    /** Search a typed address: forward-geocode, recenter the map, reload categories. */
    fun onAddressSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            geocodeRepository.forwardGeocode(query).fold(
                onSuccess = { point ->
                    _uiState.update {
                        it.copy(location = IssueLocation(point.latitude, point.longitude, null), busStopName = null)
                    }
                    _events.emit(InfrastructureIssueEvent.RecenterMap(point.latitude, point.longitude))
                    loadForLocation()
                },
                onFailure = { _events.emit(InfrastructureIssueEvent.AddressNotFound) }
            )
        }
    }

    private fun loadForLocation() {
        val location = _uiState.value.location
        val lat = location.latitude
        val lon = location.longitude
        _uiState.update { it.copy(loadingServices = true) }

        viewModelScope.launch {
            val addressJob = async { geocodeRepository.reverseGeocode(lat, lon) }
            val servicesResult = serviceListRepository.loadServices(lat, lon)
            addressJob.await().onSuccess { address ->
                _uiState.update { it.copy(address = address) }
            }
            servicesResult.fold(
                onSuccess = ::onServicesLoaded,
                onFailure = { _uiState.update { it.copy(loadingServices = false) } }
            )
        }
    }

    private fun onServicesLoaded(result: ServiceListResult) {
        open311 = result.open311
        allTransitHeuristicMatch = result.allTransitHeuristicMatch
        val hasStop = _uiState.value.location.stop != null

        _uiState.update { state ->
            val showMarker = !hasStop && result.areaManagedByOpen311
            state.copy(
                services = result.items,
                loadingServices = false,
                servicesVisible = hasStop || result.areaManagedByOpen311,
                showStopPrompt = !hasStop && !result.areaManagedByOpen311,
                markerLocation = if (showMarker) state.location else null
            )
        }

        if (pendingDefaultIssueType != DefaultIssueType.NONE) {
            selectDefaultCategory(result.items, pendingDefaultIssueType)
            pendingDefaultIssueType = DefaultIssueType.NONE
        }
    }

    // --- Category selection ---------------------------------------------------------------------

    fun onServiceSelected(index: Int) {
        val item = _uiState.value.services.getOrNull(index) ?: return
        _uiState.update { it.copy(selectedIndex = index) }
        when (item) {
            is ServiceListItem.Hint -> clearReportingTarget()
            is ServiceListItem.Section -> Unit
            is ServiceListItem.Category -> onCategorySelected(item)
        }
    }

    /** Back was pressed inside a form: drop the selection back to the hint row. */
    fun onResetToHint() {
        _uiState.update { it.copy(selectedIndex = 0) }
        clearReportingTarget()
    }

    private fun onCategorySelected(category: ServiceListItem.Category) {
        pendingTransitTypeWithoutStop = null
        selectedCategory = category

        val type = category.type
        val isOpen311 = category.code != null && (type == null || !type.contains(ReportConstants.DYNAMIC_SERVICE))
        if (isOpen311) {
            showTarget(ReportTarget.Open311(category, null), showMarker = true)
        } else if (!ReportConstants.DEFAULT_SERVICE.equals(type, ignoreCase = true)) {
            _uiState.update { it.copy(showStopPrompt = false) }
            showTransitService(type)
        }
    }

    private fun showTransitService(type: String?) {
        val stop = _uiState.value.location.stop
        if (stop == null) {
            pendingTransitTypeWithoutStop = type
            _uiState.update { it.copy(target = ReportTarget.None, showStopPrompt = true, markerLocation = null) }
            return
        }
        val target = when (type) {
            ReportConstants.STATIC_TRANSIT_SERVICE_STOP -> ReportTarget.StopProblem(stop)
            ReportConstants.STATIC_TRANSIT_SERVICE_TRIP -> ReportTarget.TripProblem(stop, arrivalInfo)
            ReportConstants.DYNAMIC_TRANSIT_SERVICE_STOP -> open311TargetOrNone(null)
            ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP ->
                if (arrivalInfo != null) open311TargetOrNone(arrivalInfo) else ReportTarget.TripProblem(stop, null)

            else -> ReportTarget.None
        }
        showTarget(target, showMarker = false)
    }

    /** The arrival picker returned a selection; route to the trip form or the Open311 trip form. */
    fun onArrivalSelected(arrival: TripReportContext) {
        arrivalInfo = arrival
        val stop = _uiState.value.location.stop ?: return
        val category = selectedCategory
        if (category != null && category.type == ReportConstants.DYNAMIC_TRANSIT_SERVICE_TRIP) {
            showTarget(ReportTarget.Open311(category, arrival), showMarker = true)
        } else {
            showTarget(ReportTarget.TripProblem(stop, arrival), showMarker = false)
        }
    }

    fun onReportSent() {
        viewModelScope.launch { _events.emit(InfrastructureIssueEvent.ReportSent) }
    }

    /** Trip context for the host when launching the trip / Open311-trip forms. */
    fun tripContext(): Triple<TripReportContext?, String?, String?> = Triple(arrivalInfo, agencyName, blockId)

    /**
     * The current issue location/address/stop, for the hosted Open311 form (was the host activity's
     * currentIssueContext()). Returns the raw triple of coordinate, optional address, and focused stop
     * so the host can wrap it in its Open311 context type without this VM importing it.
     */
    fun issueContext(): IssueContextSnapshot {
        val state = _uiState.value
        return IssueContextSnapshot(
            latitude = state.location.latitude,
            longitude = state.location.longitude,
            address = state.address.takeIf { it.isNotEmpty() },
            stop = state.location.stop
        )
    }

    // --- Helpers --------------------------------------------------------------------------------

    private fun open311TargetOrNone(arrival: TripReportContext?): ReportTarget =
        selectedCategory?.let { ReportTarget.Open311(it, arrival) } ?: ReportTarget.None

    private fun showTarget(target: ReportTarget, showMarker: Boolean) {
        _uiState.update {
            it.copy(
                target = target,
                markerLocation = if (showMarker) it.location else it.markerLocation,
                showStopPrompt = false
            )
        }
    }

    private fun clearReportingTarget() {
        _uiState.update { it.copy(target = ReportTarget.None) }
    }

    private fun selectDefaultCategory(items: List<ServiceListItem>, type: DefaultIssueType) {
        items.forEachIndexed { index, item ->
            if (item !is ServiceListItem.Category) return@forEachIndexed
            val matches = when (type) {
                // Stop problem only auto-selects on an explicit (non-heuristic) transit match.
                DefaultIssueType.STOP ->
                    item.type == ReportConstants.STATIC_TRANSIT_SERVICE_STOP && !allTransitHeuristicMatch

                DefaultIssueType.TRIP ->
                    item.type == ReportConstants.STATIC_TRANSIT_SERVICE_TRIP

                DefaultIssueType.NONE -> false
            }
            if (matches) {
                onServiceSelected(index)
                return
            }
        }
    }
}
