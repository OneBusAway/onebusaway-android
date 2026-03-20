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

import org.onebusaway.android.R;
import org.onebusaway.android.ui.ArrivalsListActivity;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AppWidgetProvider for the Stop Times widget. Each widget instance is identified by an
 * appWidgetId assigned by the system. Config and arrival data for each instance are stored
 * in {@link WidgetPrefs}.
 */
public class StopTimesWidget extends AppWidgetProvider {

    public static final String ACTION_REFRESH_WIDGET = "org.onebusaway.android.ui.ACTION_REFRESH_WIDGET";
    public static final String ACTION_UPDATE_RELATIVE_TIMES = "org.onebusaway.android.ui.ACTION_UPDATE_WIDGET_RELATIVE_TIMES";
    public static final String ACTION_APPLY_PENDING_CONFIG = "org.onebusaway.android.ui.ACTION_APPLY_PENDING_WIDGET_CONFIG";

    public static final String EXTRA_STOP_ID = "stop_id";
    public static final String EXTRA_STOP_NAME = "stop_name";
    public static final String EXTRA_WIDGET_NAME = "widget_name";
    public static final String EXTRA_ROUTE_IDS = "route_ids";
    public static final String EXTRA_ROUTE_NAMES = "route_names";

    private static final String TAG = "StopTimesWidget";

    private static final int WIDGET_CELL_SIZE_2 = 110;
    private static final int WIDGET_CELL_SIZE_3 = 250;
    private static final int WIDGET_CELL_SIZE_4 = 300;

    private static final int[] WIDGET_ROW_IDS = {
            R.id.widget_route_1_row,
            R.id.widget_route_2_row,
            R.id.widget_route_3_row
    };

    private static final int[] WIDGET_ROUTE_TITLE_IDS = {
            R.id.widget_route_1_title,
            R.id.widget_route_2_title,
            R.id.widget_route_3_title
    };

    private static final int[][] WIDGET_ETA_IDS = {
            {R.id.widget_route_1_eta_1, R.id.widget_route_1_eta_2, R.id.widget_route_1_eta_3},
            {R.id.widget_route_2_eta_1, R.id.widget_route_2_eta_2, R.id.widget_route_2_eta_3},
            {R.id.widget_route_3_eta_1, R.id.widget_route_3_eta_2, R.id.widget_route_3_eta_3}
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        super.onReceive(context, intent);
        final int widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );

        if (ACTION_REFRESH_WIDGET.equals(action)) {
            // refresh widget (triggered every five mins or by refresh button)
            scheduleNextRefresh(context, widgetId);
            final AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            refreshWidget(context, mgr, widgetId);

        } else if (ACTION_UPDATE_RELATIVE_TIMES.equals(action)) {
            // update widget labels (triggered every minute); always reschedule even if the
            // screen is off so the chain continues when the screen comes back on
            scheduleNextRelativeTimesUpdate(context, widgetId);
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm.isInteractive()) {
                updateArrivalsFromCache(context, widgetId);
            }

        } else if (ACTION_APPLY_PENDING_CONFIG.equals(action)) {
            // callback after requestPinAppWidget succeeds
            final String stopId = intent.getStringExtra(EXTRA_STOP_ID);
            final String stopName = intent.getStringExtra(EXTRA_STOP_NAME);
            final String widgetName = intent.getStringExtra(EXTRA_WIDGET_NAME);
            final ArrayList<String> routeIdList = intent.getStringArrayListExtra(EXTRA_ROUTE_IDS);
            final ArrayList<String> routeNameList = intent.getStringArrayListExtra(EXTRA_ROUTE_NAMES);
            final Map<String, String> routeShortNames = new HashMap<>();
            if (routeIdList != null && routeNameList != null) {
                for (int i = 0; i < routeIdList.size(); i++) {
                    routeShortNames.put(routeIdList.get(i),
                            i < routeNameList.size() ? routeNameList.get(i) : routeIdList.get(i));
                }
            }
            final WidgetConfig config = new WidgetConfig(stopId, stopName, widgetName, routeShortNames);
            final AppWidgetManager mgr = AppWidgetManager.getInstance(context);

            WidgetPrefs.saveConfig(context, widgetId, config);
            scheduleRepeatingRefreshBroadcasts(context, widgetId);
            refreshWidget(context, mgr, widgetId);
        }
    }

    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
        Log.d(TAG, "onUpdate widgetIds=" + java.util.Arrays.toString(appWidgetIds));
        for (final int appWidgetId : appWidgetIds) {
            scheduleRepeatingRefreshBroadcasts(context, appWidgetId);
            updateArrivalsFromCache(context, appWidgetId);

            final WidgetConfig config = WidgetPrefs.loadConfig(context, appWidgetId);
            Log.d(TAG, "onUpdate id=" + appWidgetId + " hasConfig=" + (config != null));
            if (config == null) {
                // Widget was placed with no config: On some devices, requestPinAppWidget
                // places the widget without re-launching the configure activity or firing the
                // callback, leaving the pending config unclaimed. Apply it here so the widget
                // is immediately usable without requiring the user to configure it again.
                final WidgetConfig pending = WidgetPrefs.loadAndClearPendingPinConfig(context);
                if (pending != null) {
                    WidgetPrefs.saveConfig(context, appWidgetId, pending);
                    refreshWidget(context, appWidgetManager, appWidgetId);
                } else {
                    continue;
                }
            }

            final WidgetArrivalSnapshot snapshot = WidgetPrefs.loadSnapshot(context, appWidgetId);
            final boolean isStale = snapshot == null ||
                    System.currentTimeMillis() - snapshot.getFetchedAtMs() > TimeUnit.MINUTES.toMillis(5);
            if (isStale) {
                WidgetArrivalWorker.enqueue(context, appWidgetId);
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.d(TAG, "onDeleted widgetIds=" + java.util.Arrays.toString(appWidgetIds));
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (final int appWidgetId : appWidgetIds) {
            final Intent relativeTimesIntent = new Intent(context, StopTimesWidget.class);
            relativeTimesIntent.setAction(ACTION_UPDATE_RELATIVE_TIMES);
            alarmManager.cancel(PendingIntent.getBroadcast(
                    context,
                    requestCode(appWidgetId, ACTION_UPDATE_RELATIVE_TIMES),
                    relativeTimesIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            final Intent apiRefreshIntent = new Intent(context, StopTimesWidget.class);
            apiRefreshIntent.setAction(ACTION_REFRESH_WIDGET);
            apiRefreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            alarmManager.cancel(PendingIntent.getBroadcast(
                    context,
                    requestCode(appWidgetId, ACTION_REFRESH_WIDGET),
                    apiRefreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            WidgetPrefs.delete(context, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
                                          AppWidgetManager appWidgetManager,
                                          int appWidgetId,
                                          Bundle newOptions) {
        updateArrivalsFromCache(context, appWidgetId);
    }

    /**
     * Triggers a full widget refresh for a single instance. Shows the loading state and
     * enqueues {@link WidgetArrivalWorker} to fetch arrivals from the OBA API in the
     * background. Once the worker finishes, it calls {@link #updateArrivalsFromCache}
     * to populate the rows.
     */
    static void refreshWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "refreshWidget id=" + appWidgetId);
        applyLoadingState(context, appWidgetManager, appWidgetId);

        final WidgetConfig widgetConfig = WidgetPrefs.loadConfig(context, appWidgetId);
        if (widgetConfig != null) {
            WidgetArrivalWorker.enqueue(context, appWidgetId);
        }
    }

    // Switches the widget UI to the "loading" state for a single widget
    private static void applyLoadingState(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "applyLoadingState id=" + appWidgetId + " calling updateAppWidget");
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.stop_times_widget);

        final WidgetConfig widgetConfig = WidgetPrefs.loadConfig(context, appWidgetId);
        if (widgetConfig == null) {
            views.setTextViewText(R.id.stop_times_widget_title, context.getString(R.string.widget_not_configured));
        } else {
            views.setTextViewText(R.id.stop_times_widget_title, widgetConfig.getWidgetName());
            bindStopIntent(context, views, appWidgetId, widgetConfig.getStopId());
            views.setViewVisibility(R.id.widget_loading_spinner, View.VISIBLE);
            for (final int rowId : WIDGET_ROW_IDS) {
                views.setViewVisibility(rowId, View.GONE);
            }
        }

        bindRefreshIntent(context, views, appWidgetId);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Tapping the widget body opens the arrivals list for the configured stop
    private static void bindStopIntent(Context context, RemoteViews views, int appWidgetId, String stopId) {
        final Intent intent = new ArrivalsListActivity.Builder(context, stopId).getIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
    }

    // Tapping the refresh button triggers an immediate API fetch
    private static void bindRefreshIntent(Context context, RemoteViews views, int appWidgetId) {
        final Intent refreshIntent = new Intent(context, StopTimesWidget.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        final PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(R.id.refresh_header, refreshPendingIntent);
    }

    // Shows or hides route rows and ETA columns based on the current widget dimensions
    private static void applySizeVisibility(final Context context, final RemoteViews views, final int minWidthDp, final int minHeightDp) {
        // Hide ETA columns as the widget gets narrower
        if (minWidthDp <= WIDGET_CELL_SIZE_4) {
            for (final int[] rowEtas : WIDGET_ETA_IDS) {
                views.setViewVisibility(rowEtas[2], View.GONE);
            }
        }
        if (minWidthDp <= WIDGET_CELL_SIZE_3) {
            for (final int[] rowEtas : WIDGET_ETA_IDS) {
                views.setViewVisibility(rowEtas[1], View.GONE);
            }
        }

        final int headerHorizontalPaddingPx = dpToPx(context, 18);

        // At minimum height, hide bottom rows, remove the spacer, and tighten padding
        if (minHeightDp <= WIDGET_CELL_SIZE_2) {
            final int headerVerticalPaddingPx = dpToPx(context, 6);
            views.setViewVisibility(R.id.widget_route_2_row, View.GONE);
            views.setViewVisibility(R.id.widget_route_3_row, View.GONE);
            views.setViewVisibility(R.id.header_spacer, View.GONE);
            views.setViewPadding(R.id.stop_times_widget_header, headerHorizontalPaddingPx, headerVerticalPaddingPx, headerHorizontalPaddingPx, headerVerticalPaddingPx);
            views.setViewPadding(R.id.widget_updated_at, 0, 0, 0, dpToPx(context, 5));
        } else {
            final int headerVerticalPaddingPx = dpToPx(context, 8);
            views.setViewVisibility(R.id.header_spacer, View.VISIBLE);
            views.setViewPadding(R.id.stop_times_widget_header, headerHorizontalPaddingPx, headerVerticalPaddingPx, headerHorizontalPaddingPx, headerVerticalPaddingPx);
            views.setViewPadding(R.id.widget_updated_at, 0, 0, 0, dpToPx(context, 10));
        }
    }

    private static int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // Schedules both alarms for a widget. Called once on placement/reboot via onUpdate.
    // Both alarms reschedule themselves on each fire via onReceive.
    static void scheduleRepeatingRefreshBroadcasts(Context context, int appWidgetId) {
        scheduleNextRelativeTimesUpdate(context, appWidgetId);
        scheduleNextRefresh(context, appWidgetId);
    }

    // Schedules the next per-minute label update
    static void scheduleNextRelativeTimesUpdate(Context context, int appWidgetId) {
        scheduleExactAlarm(context, appWidgetId, ACTION_UPDATE_RELATIVE_TIMES, TimeUnit.MINUTES.toMillis(1));
    }

    // Schedules the next 5-minute data refresh
    static void scheduleNextRefresh(Context context, int appWidgetId) {
        scheduleExactAlarm(context, appWidgetId, ACTION_REFRESH_WIDGET, TimeUnit.MINUTES.toMillis(5));
    }

    // Schedules an exact wakeup alarm for the given action. On API 31+ falls back to setWindow
    // if the SCHEDULE_EXACT_ALARM permission has not been granted by the user
    private static void scheduleExactAlarm(Context context, int appWidgetId, String action, long delayMs) {
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        final Intent intent = new Intent(context, StopTimesWidget.class);
        intent.setAction(action);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);

        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(appWidgetId, action),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final long triggerAt = SystemClock.elapsedRealtime() + delayMs;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt,
                    TimeUnit.SECONDS.toMillis(30), pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }

    // Returns a unique PendingIntent request code for the given widget ID and action
    private static int requestCode(int widgetId, String action) {
        final int numActions = 2;
        final int actionIndex;
        switch (action) {
            case ACTION_REFRESH_WIDGET:
                actionIndex = 0;
                break;
            case ACTION_UPDATE_RELATIVE_TIMES:
                actionIndex = 1;
                break;
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }
        return (widgetId * numActions) + actionIndex;
    }

    /**
     * Populates the widget's arrival rows from the last cached {@link WidgetArrivalSnapshot}.
     * Called by {@link WidgetArrivalWorker} after a successful API fetch, and by the per-minute
     * alarm to reformat ETA labels without hitting the network.
     */
    static void updateArrivalsFromCache(Context context, int widgetId) {
        Log.d(TAG, "updateArrivalsFromCache id=" + widgetId);
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.stop_times_widget);
        final WidgetArrivalSnapshot arrivalSnapshot = WidgetPrefs.loadSnapshot(context, widgetId);
        final WidgetConfig config = WidgetPrefs.loadConfig(context, widgetId);
        final AppWidgetManager mgr = AppWidgetManager.getInstance(context);

        if (config != null) {
            views.setTextViewText(R.id.stop_times_widget_title, config.getWidgetName());
        }

        if (arrivalSnapshot == null) {
            mgr.updateAppWidget(widgetId, views);
            return;
        }

        // If cached data is too old, trigger a full refresh instead of displaying stale times
        final long snapshotAgeMs = System.currentTimeMillis() - arrivalSnapshot.getFetchedAtMs();
        if (snapshotAgeMs > TimeUnit.MINUTES.toMillis(10)) {
            refreshWidget(context, mgr, widgetId);
            return;
        }

        final Bundle options = mgr.getAppWidgetOptions(widgetId);
        final int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        final int minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);

        bindRouteRows(context, views, arrivalSnapshot.getRoutes());
        bindUpdatedAtLabel(context, views, arrivalSnapshot.getFetchedAtMs());

        views.setViewVisibility(R.id.widget_loading_spinner, View.GONE);
        applySizeVisibility(context, views, minWidthDp, minHeightDp);

        if (config != null) {
            bindStopIntent(context, views, widgetId, config.getStopId());
        }
        bindRefreshIntent(context, views, widgetId);

        mgr.updateAppWidget(widgetId, views);
    }

    private static void bindRouteRows(Context context,
                                      RemoteViews views,
                                      List<WidgetArrivalSnapshot.Route> routes) {
        for (int rowIndex = 0; rowIndex < WIDGET_ROUTE_TITLE_IDS.length; rowIndex++) {
            if (rowIndex >= routes.size()) {
                views.setViewVisibility(WIDGET_ROW_IDS[rowIndex], View.GONE);
                continue;
            }

            views.setViewVisibility(WIDGET_ROW_IDS[rowIndex], View.VISIBLE);

            final WidgetArrivalSnapshot.Route route = routes.get(rowIndex);
            views.setTextViewText(WIDGET_ROUTE_TITLE_IDS[rowIndex], route.getShortName());
            views.setViewVisibility(WIDGET_ROUTE_TITLE_IDS[rowIndex], View.VISIBLE);

            final List<WidgetArrivalSnapshot.Arrival> validArrivals = new ArrayList<>();
            for (final WidgetArrivalSnapshot.Arrival arrival : route.getArrivals()) {
                boolean isStale = arrival.getBestArrivalTimeMs() < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2);
                if (!isStale) {
                    validArrivals.add(arrival);
                }
            }

            bindEtaPills(context, views, WIDGET_ETA_IDS[rowIndex], validArrivals);
        }
    }

    private static void bindEtaPills(Context context,
                                     RemoteViews views,
                                     int[] etaViewIds,
                                     List<WidgetArrivalSnapshot.Arrival> validArrivals) {
        if (validArrivals.isEmpty()) {
            // No upcoming arrivals — show N/A in the first pill and hide the rest
            views.setTextViewText(etaViewIds[0], context.getString(R.string.widget_no_times));
            views.setInt(etaViewIds[0], "setBackgroundResource", R.drawable.widget_eta_bg_scheduled);
            views.setViewVisibility(etaViewIds[0], View.VISIBLE);
            for (int i = 1; i < etaViewIds.length; i++) {
                views.setViewVisibility(etaViewIds[i], View.GONE);
            }
            return;
        }
        for (int i = 0; i < etaViewIds.length; i++) {
            if (i >= validArrivals.size()) {
                views.setViewVisibility(etaViewIds[i], View.GONE);
                continue;
            }
            final WidgetArrivalSnapshot.Arrival arrival = validArrivals.get(i);
            views.setTextViewText(etaViewIds[i], formatMinutesAway(context, arrival.getBestArrivalTimeMs()));
            views.setInt(etaViewIds[i], "setBackgroundResource", getEtaBackgroundResource(
                    arrival.getPredictedArrivalTimeMs(), arrival.getScheduledArrivalTimeMs()));
            views.setViewVisibility(etaViewIds[i], View.VISIBLE);
        }
    }

    private static void bindUpdatedAtLabel(Context context, RemoteViews views, long fetchedAtMs) {
        final long elapsedMs = System.currentTimeMillis() - fetchedAtMs;
        // Round to nearest minute (adding 30s before integer division) so the label
        // transitions at the halfway point rather than truncating to the lower minute
        final long minutes = (elapsedMs + TimeUnit.SECONDS.toMillis(30)) / TimeUnit.MINUTES.toMillis(1);
        final String label = minutes == 0
                ? context.getString(R.string.widget_updated_just_now)
                : context.getString(R.string.widget_updated_minutes_ago, minutes);
        views.setTextViewText(R.id.widget_updated_at, label);
    }

    private static String formatMinutesAway(Context context, long arrivalTimeMs) {
        final long msPerMin = TimeUnit.MINUTES.toMillis(1);
        final long nowMins = System.currentTimeMillis() / msPerMin;
        final long arrivalMins = arrivalTimeMs / msPerMin;
        final long minutes = arrivalMins - nowMins;

        if (minutes == 0) {
            return context.getString(R.string.stop_info_eta_now);
        } else {
            return String.format("%s %s", minutes, context.getString(R.string.minutes_abbreviation));
        }
    }

    private static int getEtaBackgroundResource(final long predictedArrivalTime, final long scheduledArrivalTime) {
        if (predictedArrivalTime <= 0) {
            return R.drawable.widget_eta_bg_scheduled;
        }
        final long diffMinutes = TimeUnit.MILLISECONDS.toMinutes(predictedArrivalTime - scheduledArrivalTime);

        if (diffMinutes >= 1) {
            return R.drawable.widget_eta_bg_late;
        }
        if (diffMinutes <= -1) {
            return R.drawable.widget_eta_bg_early;
        }
        return R.drawable.widget_eta_bg_on_time;
    }
}
