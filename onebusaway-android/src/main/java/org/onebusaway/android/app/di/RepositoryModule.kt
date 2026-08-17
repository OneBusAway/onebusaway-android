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
package org.onebusaway.android.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.onebusaway.android.api.data.AgenciesDataSource
import org.onebusaway.android.api.data.DefaultAgenciesDataSource
import org.onebusaway.android.api.data.DefaultLocationSearchDataSource
import org.onebusaway.android.api.data.DefaultMapDataSource
import org.onebusaway.android.api.data.DefaultNearbyArrivalsDataSource
import org.onebusaway.android.api.data.DefaultProblemReportDataSource
import org.onebusaway.android.api.data.DefaultRouteDataSource
import org.onebusaway.android.api.data.DefaultStopArrivalsDataSource
import org.onebusaway.android.api.data.DefaultStopsForRouteRepository
import org.onebusaway.android.api.data.DefaultSurveyDataSource
import org.onebusaway.android.api.data.DefaultTripDetailsDataSource
import org.onebusaway.android.api.data.DefaultTripVehiclesDataSource
import org.onebusaway.android.api.data.DefaultVehicleSearchDataSource
import org.onebusaway.android.api.data.LocationSearchDataSource
import org.onebusaway.android.api.data.MapDataSource
import org.onebusaway.android.api.data.NearbyArrivalsDataSource
import org.onebusaway.android.api.data.ProblemReportDataSource
import org.onebusaway.android.api.data.RouteDataSource
import org.onebusaway.android.api.data.StopArrivalsDataSource
import org.onebusaway.android.api.data.StopsForRouteRepository
import org.onebusaway.android.api.data.SurveyDataSource
import org.onebusaway.android.api.data.TripDetailsDataSource
import org.onebusaway.android.api.data.TripVehiclesDataSource
import org.onebusaway.android.api.data.VehicleSearchDataSource
import org.onebusaway.android.database.oba.RouteFavorites
import org.onebusaway.android.database.oba.RouteFavoritesRepository
import org.onebusaway.android.demo.DemoModeController
import org.onebusaway.android.demo.DemoModeState
import org.onebusaway.android.demo.DemoRentalPlacesRepository
import org.onebusaway.android.demo.DemoTripPlanRepository
import org.onebusaway.android.extrapolation.data.DefaultTripObservationFetcher
import org.onebusaway.android.extrapolation.data.DefaultTripObservationRepository
import org.onebusaway.android.extrapolation.data.TripObservationFetcher
import org.onebusaway.android.extrapolation.data.TripObservationRepository
import org.onebusaway.android.location.DefaultLocationRepository
import org.onebusaway.android.location.LocationRepository
import org.onebusaway.android.location.LocationSink
import org.onebusaway.android.map.DefaultFocusedTripRepository
import org.onebusaway.android.map.DefaultRouteMapRepository
import org.onebusaway.android.map.FocusedTripRepository
import org.onebusaway.android.map.RouteMapRepository
import org.onebusaway.android.map.rental.DefaultRentalPlacesRepository
import org.onebusaway.android.map.rental.RentalPlacesRepository
import org.onebusaway.android.nav.AndroidNavigationFeedbackRepository
import org.onebusaway.android.nav.AndroidReminderNotificationPresenter
import org.onebusaway.android.nav.AndroidReminderSpeechController
import org.onebusaway.android.nav.NavigationFeedbackRepository
import org.onebusaway.android.nav.ObaReminderShapeSource
import org.onebusaway.android.nav.ReminderNotificationPresenter
import org.onebusaway.android.nav.ReminderSessionStore
import org.onebusaway.android.nav.ReminderShapeSource
import org.onebusaway.android.nav.ReminderSpeechController
import org.onebusaway.android.nav.RoomReminderSessionStore
import org.onebusaway.android.preferences.DefaultPreferencesRepository
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.DefaultRegionRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.arrivals.ArrivalsDisplay
import org.onebusaway.android.ui.arrivals.ArrivalsRepository
import org.onebusaway.android.ui.arrivals.DefaultArrivalsDisplay
import org.onebusaway.android.ui.arrivals.DefaultArrivalsRepository
import org.onebusaway.android.ui.home.DefaultStartupPreferencesRepository
import org.onebusaway.android.ui.home.StartupPreferencesRepository
import org.onebusaway.android.ui.home.drawer.DefaultNavItemsRepository
import org.onebusaway.android.ui.home.drawer.NavItemsRepository
import org.onebusaway.android.ui.home.weather.DefaultWeatherRepository
import org.onebusaway.android.ui.home.weather.WeatherRepository
import org.onebusaway.android.ui.home.widealert.DefaultWideAlertsRepository
import org.onebusaway.android.ui.home.widealert.WideAlertsRepository
import org.onebusaway.android.ui.regions.DefaultRegionsRepository
import org.onebusaway.android.ui.regions.RegionsRepository
import org.onebusaway.android.ui.report.infrastructure.DefaultGeocodeAddressRepository
import org.onebusaway.android.ui.report.infrastructure.DefaultServiceListRepository
import org.onebusaway.android.ui.report.infrastructure.GeocodeAddressRepository
import org.onebusaway.android.ui.report.infrastructure.ServiceListRepository
import org.onebusaway.android.ui.report.problem.DefaultProblemReportRepository
import org.onebusaway.android.ui.report.problem.ProblemReportRepository
import org.onebusaway.android.ui.report.types.DefaultReportTypeRepository
import org.onebusaway.android.ui.report.types.ReportTypeRepository
import org.onebusaway.android.ui.routeinfo.DefaultRouteInfoRepository
import org.onebusaway.android.ui.routeinfo.RouteInfoRepository
import org.onebusaway.android.ui.searchresults.DefaultSearchResultsRepository
import org.onebusaway.android.ui.searchresults.SearchResultsRepository
import org.onebusaway.android.ui.tripdetails.DefaultTripDetailsRepository
import org.onebusaway.android.ui.tripdetails.TripDetailsRepository
import org.onebusaway.android.ui.tripinfo.DefaultTripInfoRepository
import org.onebusaway.android.ui.tripinfo.TripInfoRepository
import org.onebusaway.android.ui.tripplan.AdvancedSettingsRepository
import org.onebusaway.android.ui.tripplan.DefaultAdvancedSettingsRepository
import org.onebusaway.android.ui.tripplan.DefaultGeocodeRepository
import org.onebusaway.android.ui.tripplan.DefaultTripPlanRepository
import org.onebusaway.android.ui.tripplan.GeocodeRepository
import org.onebusaway.android.ui.tripplan.TripPlanRepository
import org.onebusaway.android.ui.tripplan.pinned.PinnedTripStore
import org.onebusaway.android.ui.tripplan.pinned.RoomPinnedTripStore
import org.onebusaway.android.ui.tripresults.DefaultTripResultsRepository
import org.onebusaway.android.ui.tripresults.TripResultsRepository

/**
 * Binds the DI-only repositories (interface -> Default impl). These are stateless per-call fetchers, so
 * they're unscoped — a fresh instance per injection. The Default impls take only `@ApplicationContext`
 * (or nothing), so Hilt constructs them directly. Repos with runtime-arg constructors (e.g. Open311) are
 * not here — they keep their factories.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    internal abstract fun bindReminderSessionStore(impl: RoomReminderSessionStore): ReminderSessionStore

    @Binds
    @Singleton
    internal abstract fun bindPinnedTripStore(impl: RoomPinnedTripStore): PinnedTripStore

    @Binds
    internal abstract fun bindReminderNotificationPresenter(
        impl: AndroidReminderNotificationPresenter
    ): ReminderNotificationPresenter

    @Binds
    internal abstract fun bindReminderSpeechController(
        impl: AndroidReminderSpeechController
    ): ReminderSpeechController

    @Binds
    internal abstract fun bindNavigationFeedbackRepository(
        impl: AndroidNavigationFeedbackRepository
    ): NavigationFeedbackRepository

    @Binds
    internal abstract fun bindReminderShapeSource(impl: ObaReminderShapeSource): ReminderShapeSource

    @Binds
    abstract fun bindRegionsRepository(impl: DefaultRegionsRepository): RegionsRepository

    @Binds
    abstract fun bindAgenciesDataSource(impl: DefaultAgenciesDataSource): AgenciesDataSource

    // Modernized Retrofit/kotlinx.serialization route-details fetcher (api/). Stateless, so
    // unscoped like the other per-call fetchers.
    @Binds
    abstract fun bindRouteDataSource(impl: DefaultRouteDataSource): RouteDataSource

    @Binds
    abstract fun bindLocationSearchDataSource(
        impl: DefaultLocationSearchDataSource
    ): LocationSearchDataSource

    @Binds
    abstract fun bindStopArrivalsDataSource(
        impl: DefaultStopArrivalsDataSource
    ): StopArrivalsDataSource

    @Binds
    abstract fun bindNearbyArrivalsDataSource(
        impl: DefaultNearbyArrivalsDataSource
    ): NearbyArrivalsDataSource

    @Binds
    abstract fun bindTripDetailsDataSource(
        impl: DefaultTripDetailsDataSource
    ): TripDetailsDataSource

    @Binds
    abstract fun bindTripVehiclesDataSource(
        impl: DefaultTripVehiclesDataSource
    ): TripVehiclesDataSource

    @Binds
    abstract fun bindVehicleSearchDataSource(
        impl: DefaultVehicleSearchDataSource
    ): VehicleSearchDataSource

    @Binds
    abstract fun bindSearchResultsRepository(impl: DefaultSearchResultsRepository): SearchResultsRepository

    @Binds
    abstract fun bindProblemReportDataSource(
        impl: DefaultProblemReportDataSource
    ): ProblemReportDataSource

    @Binds
    abstract fun bindSurveyDataSource(impl: DefaultSurveyDataSource): SurveyDataSource

    @Binds
    abstract fun bindReportTypeRepository(impl: DefaultReportTypeRepository): ReportTypeRepository

    // The report flow's graph-constructible collaborators. The Open311 *category* repository stays
    // factory-built (see the class KDoc above) — it takes opaque library values off the issue VM's
    // runtime state — but these three take only a Context or an already-bound data source.
    @Binds
    abstract fun bindServiceListRepository(impl: DefaultServiceListRepository): ServiceListRepository

    @Binds
    abstract fun bindGeocodeAddressRepository(
        impl: DefaultGeocodeAddressRepository
    ): GeocodeAddressRepository

    @Binds
    abstract fun bindProblemReportRepository(
        impl: DefaultProblemReportRepository
    ): ProblemReportRepository

    @Binds
    abstract fun bindTripResultsRepository(impl: DefaultTripResultsRepository): TripResultsRepository

    @Binds
    abstract fun bindTripDetailsRepository(impl: DefaultTripDetailsRepository): TripDetailsRepository

    // Must be @Singleton: the impl owns the shared stops-for-route cache + SingleFlight coalescing
    // that both the route map and route-info/focused-stop paths project from, so the whole app shares
    // exactly one instance.
    @Binds
    @Singleton
    abstract fun bindStopsForRouteRepository(
        impl: DefaultStopsForRouteRepository
    ): StopsForRouteRepository

    @Binds
    abstract fun bindRouteInfoRepository(impl: DefaultRouteInfoRepository): RouteInfoRepository

    @Binds
    abstract fun bindTripInfoRepository(impl: DefaultTripInfoRepository): TripInfoRepository

    @Binds
    abstract fun bindGeocodeRepository(impl: DefaultGeocodeRepository): GeocodeRepository

    // The read-only view of demo mode, for collaborators that react to it but must not switch it.
    @Binds
    @Singleton
    abstract fun bindDemoModeState(impl: DemoModeController): DemoModeState

    // The demo-aware decorators (#2164). Each wraps the real impl and answers from the bundled demo
    // transit system only while the scripted tutorial has demo mode on, so the tutorial's trip-plan
    // and bike-layer steps have guaranteed content while normal use is untouched. The OBA side needs
    // no decorator: ObaApiProvider is a single seam and branches there.
    @Binds
    abstract fun bindTripPlanRepository(impl: DemoTripPlanRepository): TripPlanRepository

    @Binds
    abstract fun bindAdvancedSettingsRepository(
        impl: DefaultAdvancedSettingsRepository
    ): AdvancedSettingsRepository

    // Must be @Singleton: the impl keeps an in-memory cache of the DataStore and starts a collector +
    // a one-time blocking seed on construction, so the whole app shares exactly one instance.
    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: DefaultPreferencesRepository): PreferencesRepository

    @Binds
    abstract fun bindWeatherRepository(impl: DefaultWeatherRepository): WeatherRepository

    @Binds
    abstract fun bindMapDataSource(impl: DefaultMapDataSource): MapDataSource

    @Binds
    abstract fun bindRouteMapRepository(impl: DefaultRouteMapRepository): RouteMapRepository

    @Binds
    abstract fun bindRentalPlacesRepository(impl: DemoRentalPlacesRepository): RentalPlacesRepository

    // HomeViewModel's collaborators (so it can become a plain @HiltViewModel — D6).
    @Binds
    abstract fun bindWideAlertsRepository(impl: DefaultWideAlertsRepository): WideAlertsRepository

    @Binds
    abstract fun bindNavItemsRepository(impl: DefaultNavItemsRepository): NavItemsRepository

    @Binds
    abstract fun bindStartupPreferencesRepository(
        impl: DefaultStartupPreferencesRepository
    ): StartupPreferencesRepository

    // The region repository is a real Hilt @Singleton — the one process-wide instance
    // that owns region state + resolution + the canonical region write.
    @Binds
    @Singleton
    abstract fun bindRegionRepository(impl: DefaultRegionRepository): RegionRepository

    // The location repository is a real Hilt @Singleton — the one process-wide instance
    // that owns last-known-location state + provider polling.
    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: DefaultLocationRepository): LocationRepository

    // The same singleton, exposed write-side for the device-listener ingestion path only (resolved via
    // LocationEntryPoint.getSink). Kept off the read-facing LocationRepository so consumers can't push.
    @Binds
    @Singleton
    abstract fun bindLocationSink(impl: DefaultLocationRepository): LocationSink

    // Arrivals: unscoped on purpose — DefaultArrivalsRepository is stateful (lastGood) and 1:1 with its
    // (assisted) ArrivalsViewModel, so each VM gets its own. Do NOT make this @Singleton.
    @Binds
    abstract fun bindArrivalsRepository(impl: DefaultArrivalsRepository): ArrivalsRepository

    // The Android presentation edge of the arrivals load path — the seam that keeps Context out of
    // DefaultArrivalsRepository so its stale-fallback/CAS logic is JVM-unit-testable (#1909).
    @Binds
    abstract fun bindArrivalsDisplay(impl: DefaultArrivalsDisplay): ArrivalsDisplay

    // The narrow route-starring surface (interface segregation, #1909); the implementation is the
    // @Singleton owner of the favorite bit, so this binding shares that one instance.
    @Binds
    abstract fun bindRouteFavorites(impl: RouteFavoritesRepository): RouteFavorites

    // Speed-estimation trip data layer: both @Singleton — the repository owns the process-wide trip
    // store (the LRU cache shared across screens), and the fetcher owns the SingleFlight dedup maps.
    // Per-view polling is still per-view: the repository's Flows are cold and view-scoped, so a
    // shared instance does not mean a shared poller.
    @Binds
    @Singleton
    abstract fun bindTripObservationRepository(
        impl: DefaultTripObservationRepository
    ): TripObservationRepository

    @Binds
    @Singleton
    abstract fun bindTripObservationFetcher(
        impl: DefaultTripObservationFetcher
    ): TripObservationFetcher

    // Focused trips: shared caches and SingleFlight calls coalesce shape/schedule/route-stop fetches.
    @Binds
    @Singleton
    abstract fun bindFocusedTripRepository(
        impl: DefaultFocusedTripRepository
    ): FocusedTripRepository
}
