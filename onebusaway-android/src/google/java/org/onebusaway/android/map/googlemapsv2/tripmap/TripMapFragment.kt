/*
 * Copyright (C) 2024 Open Transit Software Foundation
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
package org.onebusaway.android.map.googlemapsv2.tripmap

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Marker
import org.onebusaway.android.extrapolation.data.TripDataManager
import org.onebusaway.android.extrapolation.data.TripDetailsPoller
import org.onebusaway.android.map.googlemapsv2.MapHelpV2
import org.onebusaway.android.ui.TripMapCallback

/**
 * Standalone map fragment for displaying a single trip's route, stops, vehicle position, and speed
 * estimate overlays within TripDetailsActivity.
 *
 * Holds two overlay layers: [TripRouteOverlay] (static skeleton) and [TripVehicleOverlay] (live
 * data). Per-frame extrapolation is driven by [TripExtrapolationController], and API polling by
 * [TripDetailsPoller].
 *
 * If the fragment cannot activate (missing trip data), it notifies the host activity via
 * [TripMapCallback] so it can fall back to the list view.
 */
class TripMapFragment : SupportMapFragment(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    companion object {
        const val TAG = "TripMapFragment"
        private const val ARG_TRIP_ID = "tripId"
        private const val ARG_STOP_ID = "stopId"
        private const val DEFAULT_INITIAL_ZOOM = 12f

        @JvmStatic
        fun newInstance(tripId: String, stopId: String? = null): TripMapFragment {
            val options = GoogleMapOptions()
            MapHelpV2.getBounds(TripDataManager.getShape(tripId))?.let { bounds ->
                options.camera(CameraPosition(bounds.center, DEFAULT_INITIAL_ZOOM, 0f, 0f))
            }
            return TripMapFragment().apply {
                arguments =
                        Bundle().apply {
                            putParcelable("MapOptions", options)
                            putString(ARG_TRIP_ID, tripId)
                            stopId?.let { putString(ARG_STOP_ID, it) }
                        }
            }
        }
    }

    private val tripId: String
        get() = requireArguments().getString(ARG_TRIP_ID)!!
    private val selectedStopId: String?
        get() = arguments?.getString(ARG_STOP_ID)

    private var map: GoogleMap? = null
    private var routeOverlay: TripRouteOverlay? = null
    private var vehicleOverlay: TripVehicleOverlay? = null
    private var extrapolationController: TripExtrapolationController? = null
    private var poller: TripDetailsPoller? = null
    private var activated = false
    private var mapCallback: TripMapCallback? = null

    // --- Lifecycle ---

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (context is TripMapCallback) {
            mapCallback = context
        }
    }

    override fun onDetach() {
        mapCallback = null
        super.onDetach()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.setOnMarkerClickListener(this)
        googleMap.uiSettings.isZoomControlsEnabled = true
        MapHelpV2.applyMapStyle(googleMap, requireContext())
        if (hasLocationPermission()) {
            googleMap.isMyLocationEnabled = true
        }

        activate()
    }

    override fun onResume() {
        super.onResume()
        if (activated && poller == null) {
            extrapolationController?.start()
            poller = TripDetailsPoller(tripId).also { it.start() }
        }
    }

    override fun onPause() {
        extrapolationController?.stop()
        poller?.stop()
        poller = null
        super.onPause()
    }

    override fun onDestroyView() {
        extrapolationController?.stop()
        extrapolationController = null
        routeOverlay?.deactivate()
        routeOverlay = null
        vehicleOverlay?.deactivate()
        vehicleOverlay = null
        map = null
        activated = false
        super.onDestroyView()
    }

    // --- Activation ---

    private fun activate() {
        val m = map ?: return
        val response =
                TripDataManager.getTripDetails(tripId)
                        ?: run {
                            Log.w(TAG, "No cached trip details for $tripId")
                            mapCallback?.onTripMapActivationFailed()
                            return
                        }

        routeOverlay?.deactivate()
        vehicleOverlay?.deactivate()
        extrapolationController?.stop()

        TripMapOverlayFactory.create(
                m,
                requireContext(),
                tripId,
                selectedStopId,
                response,
                ::onOverlaysReady
        ) { reason ->
            Log.w(TAG, "Overlay creation failed for $tripId: $reason")
            mapCallback?.onTripMapActivationFailed()
        }
    }

    private fun onOverlaysReady(overlays: TripMapOverlays) {
        routeOverlay = overlays.route
        vehicleOverlay = overlays.vehicle
        val trip = TripDataManager.getOrCreateTrip(tripId)
        extrapolationController =
                TripExtrapolationController(overlays.vehicle, trip).also { it.start() }
        poller?.stop()
        poller = TripDetailsPoller(tripId).also { it.start() }
        activated = true
        overlays.route.fitCameraToShape()
    }

    // --- Marker click handling ---

    override fun onMarkerClick(marker: Marker): Boolean {
        val vehicle = vehicleOverlay
        val route = routeOverlay
        if (vehicle == null || route == null) return false
        return vehicle.handleDataReceivedClick(marker) ||
                vehicle.handleEstimateLabelClick(marker) ||
                route.handleStopMarkerClick(marker)
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                        ctx,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}
