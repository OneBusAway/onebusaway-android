package org.onebusaway.android.widealerts;

import com.google.transit.realtime.GtfsRealtime;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches GTFS alerts and processes them.
 */
public class GtfsAlerts {

    private static final String TAG = "GtfsAlerts";
    private static final Set<String> fetchedRegions = new HashSet<>();
    private final Context mContext;

    public GtfsAlerts(Context context) {
        mContext = context;
    }

    /**
     * Fetches GTFS alerts from a specified URL and processes them.
     *
     * @param regionId The current region ID.
     * @param callback The callback to handle the alert data.
     */
    public void fetchAlerts(String regionId, GtfsAlertCallBack callback) {
        if (fetchedRegions.contains(regionId)) {
            Log.d(TAG, "Alerts already fetched for region: " + regionId);
            return;
        }
        Log.d(TAG, "fetchAlerts for region: " + regionId);
        new Thread(() -> {
            try {
                String pathUrl = getGtfsAlertsUrl(regionId);
                URL url = new URL(pathUrl);
                GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(url.openStream());
                processAlerts(feed.getEntityList(), callback);
                fetchedRegions.add(regionId);
            } catch (Exception e) {
                Log.e(TAG, "Error fetching GTFS alert data for region: " + regionId, e);
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Processes the list of GTFS alerts and triggers the callback for one valid alert.
     *
     * @param alerts   The list of GTFS alert entities.
     * @param callback The callback to handle each alert.
     */
    public void processAlerts(List<GtfsRealtime.FeedEntity> alerts, GtfsAlertCallBack callback) {
        for (GtfsRealtime.FeedEntity entity : alerts) {
            if (!GtfsAlertsHelper.isValidEntity(mContext, entity)) {
                continue;
            }
            GtfsRealtime.Alert alert = entity.getAlert();
            String id = entity.getId();
            String title = GtfsAlertsHelper.getAlertTitle(alert);
            String description = GtfsAlertsHelper.getAlertDescription(alert);
            String url = GtfsAlertsHelper.getAlertUrl(alert);

            Log.d(TAG, "Alert: " + id + " - " + title + " - " + description + " - " + url);
            GtfsAlertsHelper.markAlertAsRead(Application.get().getApplicationContext(), entity);
            callback.onAlert(title, description, url);
            // Only trigger the callback for one alert.
            break;
        }
    }

    /**
     * Constructs the URL for fetching GTFS alerts for a given region.
     *
     * @param regionId The ID of the region for which to fetch alerts.
     * @return The URL to fetch GTFS alerts.
     */
    public String getGtfsAlertsUrl(String regionId) {
        Application app = Application.get();
        SharedPreferences sharedPreferences = Application.getPrefs();

        boolean isTestAlert = sharedPreferences.getBoolean(app.getString(R.string.preferences_display_test_alerts), false);
        String url = "https://onebusaway.co/api/v1/regions/" + regionId + "/alerts.pb";
        if (isTestAlert) url += "?test=1";
        return url;
    }

}
