package org.onebusaway.android.widealerts

import android.content.Context
import android.util.Log
import com.google.transit.realtime.GtfsRealtime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.time.WallTime
import org.onebusaway.android.util.PreferenceUtils

/** Fetches and processes wide GTFS alerts. */
@Singleton
class GtfsAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val fetchedRegions = ConcurrentHashMap.newKeySet<String>()

    fun fetchAlerts(regionId: String, callback: GtfsAlertCallBack) {
        val pathUrl = getGtfsAlertsUrl(regionId) ?: return
        if (!fetchedRegions.add(regionId)) {
            Log.d(TAG, "Alerts already fetched for region: $regionId")
            return
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchAlerts for region: $regionId")
        Thread {
            try {
                val connection = URL(pathUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = NETWORK_TIMEOUT_MS
                connection.readTimeout = NETWORK_TIMEOUT_MS
                val feed = try {
                    connection.inputStream.use(GtfsRealtime.FeedMessage::parseFrom)
                } finally {
                    connection.disconnect()
                }
                // "Now" for the alert active-window check. The feed header timestamp is this feed's
                // own server clock (GTFS-RT seconds), so preferring it cancels device clock skew
                // (#1612). That field is optional in the spec, and a feed omitting it must still
                // yield alerts, so the device clock is the documented fallback — a deliberate
                // server/device crossing, resolved here at the boundary so the downstream check
                // stays a pure function of its inputs.
                val now = if (feed.hasHeader() && feed.header.hasTimestamp()) {
                    GtfsAlertsHelper.serverTimeFromGtfsSeconds(feed.header.timestamp)
                } else {
                    Log.w(TAG, "GTFS alert feed for region $regionId omitted its timestamp; using the device clock")
                    ServerTime(WallTime.now().epochMs)
                }
                processAlerts(feed.entityList, now, callback)
            } catch (error: Exception) {
                fetchedRegions.remove(regionId)
                Log.e(TAG, "Error fetching GTFS alert data for region: $regionId", error)
            }
        }.start()
    }

    fun processAlerts(
        alerts: List<GtfsRealtime.FeedEntity>,
        now: ServerTime,
        callback: GtfsAlertCallBack
    ) {
        for (entity in alerts) {
            if (!GtfsAlertsHelper.isValidEntity(context, entity, now)) continue
            val alert = entity.alert
            val title = GtfsAlertsHelper.getAlertTitle(alert)
            val description = GtfsAlertsHelper.getAlertDescription(alert)
            val url = GtfsAlertsHelper.getAlertUrl(alert)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Alert: ${entity.id} - $title - $description - $url")
            }
            GtfsAlertsHelper.markAlertAsRead(context, entity)
            callback.onAlert(title, description, url)
            break
        }
    }

    fun getGtfsAlertsUrl(regionId: String): String? {
        val region = RegionEntryPoint.get(context).currentRegion() ?: return null
        val baseUrl = region.sidecarBaseUrl ?: return null
        val testAlert = PreferenceUtils.getBoolean(
            context.getString(R.string.preferences_display_test_alerts),
            false
        )
        return (baseUrl + context.getString(R.string.alerts_api_endpoint))
            .replace("regionID", regionId)
            .let { if (testAlert) "$it?test=1" else it }
    }

    private companion object {
        const val TAG = "GtfsAlerts"
        const val NETWORK_TIMEOUT_MS = 15_000
    }
}
