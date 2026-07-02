package org.onebusaway.android.widealerts;

import com.google.transit.realtime.GtfsRealtime;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.PreferenceUtils;

import android.content.Context;
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
        String pathUrl = getGtfsAlertsUrl(regionId);
        if (pathUrl == null) {
            return;
        }
        Log.d(TAG, "fetchAlerts for region: " + regionId);
        new Thread(() -> {
            try {
                URL url = new URL(pathUrl);
                GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(url.openStream());
                // The feed header timestamp is the server clock for this feed (seconds since epoch),
                // used as "now" for the alert start-date window so it cancels device clock skew (#1612).
                long feedTimeMs = feed.hasHeader() && feed.getHeader().hasTimestamp()
                        ? feed.getHeader().getTimestamp() * 1000L
                        : 0L;
                processAlerts(feed.getEntityList(), feedTimeMs, callback);
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
     * @param alerts     The list of GTFS alert entities.
     * @param feedTimeMs The feed header's server-clock generation time (epoch millis), or 0 when absent.
     * @param callback   The callback to handle each alert.
     */
    public void processAlerts(List<GtfsRealtime.FeedEntity> alerts, long feedTimeMs, GtfsAlertCallBack callback) {
        for (GtfsRealtime.FeedEntity entity : alerts) {
            if (!GtfsAlertsHelper.isValidEntity(mContext, entity, feedTimeMs)) {
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
        String baseUrl = app.getCurrentRegion().getSidecarBaseUrl();
        if (baseUrl == null) return null;
        boolean isTestAlert = PreferenceUtils.getBoolean(app.getString(R.string.preferences_display_test_alerts), false);
        String alertAPIURL = baseUrl + app.getString(R.string.alerts_api_endpoint);
        alertAPIURL = alertAPIURL.replace("regionID", regionId);
        if (isTestAlert) alertAPIURL += "?test=1";
        return alertAPIURL;
    }

}
