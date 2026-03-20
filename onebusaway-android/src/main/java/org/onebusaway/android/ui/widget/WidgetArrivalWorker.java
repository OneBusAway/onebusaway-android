/*
 * Copyright (C) 2025 Rob Godfrey (rob_godfrey@outlook.com)
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
package org.onebusaway.android.ui.widget;

import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.request.ObaArrivalInfoRequest;
import org.onebusaway.android.io.request.ObaArrivalInfoResponse;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.onebusaway.android.ui.widget.WidgetArrivalSnapshot.Arrival;
import org.onebusaway.android.ui.widget.WidgetArrivalSnapshot.Route;

/**
 * Background worker that fetches arrival times for a widget's stop and saves the result to
 * {@link WidgetPrefs} as a {@link WidgetArrivalSnapshot}. Once saved, it triggers
 * {@link StopTimesWidget#updateArrivalsFromCache} to redraw the widget from the new snapshot.
 */
public class WidgetArrivalWorker extends Worker {

    private static final String TAG = "WidgetArrivalWorker";
    private static final int MAX_FETCH_ATTEMPTS = 3;

    public WidgetArrivalWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueue(Context context, int widgetId) {
        final Data data = new Data.Builder()
                .putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .build();

        final OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetArrivalWorker.class)
                .setInputData(data)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "widget_refresh_" + widgetId,
                ExistingWorkPolicy.KEEP,
                request);
    }

    @NonNull
    @Override
    public Result doWork() {
        final int widgetId = getInputData().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Log.d(TAG, "doWork id=" + widgetId);

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.w(TAG, "doWork: invalid widget ID");
            return Result.failure();
        }

        final WidgetConfig config = WidgetPrefs.loadConfig(getApplicationContext(), widgetId);

        if (config == null || config.getStopId() == null) {
            // widget was deleted or not configured yet
            Log.d(TAG, "doWork id=" + widgetId + " no config, skipping");
            return Result.success();
        }

        final ObaArrivalInfoResponse response = fetchArrivals(config.getStopId());

        if (response == null || response.getArrivalInfo() == null) {
            Log.w(TAG, "doWork id=" + widgetId + " fetch failed, retrying");
            return Result.retry();
        }

        final WidgetArrivalSnapshot snapshot = buildSnapshot(response, config.getRouteShortNameMap());

        WidgetPrefs.saveSnapshot(getApplicationContext(), widgetId, snapshot);
        Log.d(TAG, "doWork id=" + widgetId + " fetch succeeded, updating widget");
        StopTimesWidget.updateArrivalsFromCache(getApplicationContext(), widgetId);

        return Result.success();
    }

    // Fetches arrivals for the given stop, expanding the look-ahead window by 1 hour at a time
    // until arrivals are found or the attempt limit is reached
    private ObaArrivalInfoResponse fetchArrivals(String stopId) {
        int minutesAfter = 65;

        for (int attempt = 0; attempt < MAX_FETCH_ATTEMPTS; attempt++) {
            final ObaArrivalInfoRequest request = ObaArrivalInfoRequest.newRequest(getApplicationContext(), stopId, minutesAfter);
            final ObaArrivalInfoResponse response = request.call();

            if (response != null && response.getArrivalInfo() != null && response.getArrivalInfo().length > 0) {
                return response;
            }

            minutesAfter += 60;
        }

        return null;
    }

    // Converts an API response into a WidgetArrivalSnapshot, including all configured routes.
    // Routes with no arrivals in the response are included with an empty arrivals list so the
    // widget always displays all configured routes (showing "N/A" for those with no times).
    private static WidgetArrivalSnapshot buildSnapshot(ObaArrivalInfoResponse response, Map<String, String> routeShortNameMap) {
        final long now = System.currentTimeMillis();

        final Map<String, List<ObaArrivalInfo>> groupedArrivals = filterArrivalsAndGroupByRoute(
                response.getArrivalInfo(), routeShortNameMap.keySet());

        final List<Route> routesWithArrivals = new ArrayList<>();
        final List<Route> routesWithoutArrivals = new ArrayList<>();

        for (final Map.Entry<String, String> entry : routeShortNameMap.entrySet()) {
            final String routeId = entry.getKey();
            final String shortName = entry.getValue();
            final List<ObaArrivalInfo> arrivals = groupedArrivals.get(routeId);
            if (arrivals != null) {
                routesWithArrivals.add(getRoute(arrivals));
            } else {
                routesWithoutArrivals.add(new Route(shortName, Collections.emptyList()));
            }
        }

        // Routes with arrivals sorted soonest-first, routes with no arrivals appended after
        Collections.sort(routesWithArrivals, (r1, r2) ->
                Long.compare(r1.getArrivals().get(0).getBestArrivalTimeMs(),
                        r2.getArrivals().get(0).getBestArrivalTimeMs()));

        final List<Route> allRoutes = new ArrayList<>(routesWithArrivals);
        allRoutes.addAll(routesWithoutArrivals);

        return new WidgetArrivalSnapshot(now, allRoutes);
    }

    // Groups arrivals by route ID, filtering to only the routes in routeFilter, sorts each
    // group by soonest arrival
    private static Map<String, List<ObaArrivalInfo>> filterArrivalsAndGroupByRoute(ObaArrivalInfo[] arrivals, Set<String> routeFilter) {
        final Map<String, List<ObaArrivalInfo>> filteredAndGroupedRouteArrivals = new LinkedHashMap<>();

        for (final ObaArrivalInfo info : arrivals) {
            final String routeId = info.getRouteId();
            if (routeFilter.contains(routeId)) {
                if (!filteredAndGroupedRouteArrivals.containsKey(routeId)) {
                    filteredAndGroupedRouteArrivals.put(routeId, new ArrayList<>());
                }
                filteredAndGroupedRouteArrivals.get(routeId).add(info);
            }
        }

        for (final List<ObaArrivalInfo> group : filteredAndGroupedRouteArrivals.values()) {
            Collections.sort(group, (a, b) -> {
                long timeA = a.getPredictedArrivalTime() > 0 ? a.getPredictedArrivalTime() : a.getScheduledArrivalTime();
                long timeB = b.getPredictedArrivalTime() > 0 ? b.getPredictedArrivalTime() : b.getScheduledArrivalTime();
                return Long.compare(timeA, timeB);
            });
        }

        return filteredAndGroupedRouteArrivals;
    }

    // Builds a snapshot of Routes from a sorted list of arrivals, capped at 4
    private static Route getRoute(List<ObaArrivalInfo> arrivals) {
        final String shortName = arrivals.get(0).getShortName();
        final List<Arrival> snapshotArrivals = new ArrayList<>();

        for (int i = 0; i < Math.min(arrivals.size(), 4); i++) {
            final ObaArrivalInfo arrival = arrivals.get(i);
            snapshotArrivals.add(new Arrival(arrival.getPredictedArrivalTime(), arrival.getScheduledArrivalTime()));
        }

        return new Route(shortName, snapshotArrivals);
    }
}
