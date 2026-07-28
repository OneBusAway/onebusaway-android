package org.onebusaway.android.widealerts

import android.content.Context
import android.util.Log
import com.google.transit.realtime.GtfsRealtime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.BuildConfig
import org.onebusaway.android.R
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.util.PreferenceUtils

/** Fetches and processes wide GTFS alerts. */
@Singleton
class GtfsAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val fetchedRegions = ConcurrentHashMap.newKeySet<String>()

    fun fetchAlerts(regionId: String, callback: GtfsAlertCallBack) {
        if (regionId in fetchedRegions) {
            Log.d(TAG, "Alerts already fetched for region: $regionId")
            return
        }
        val pathUrl = getGtfsAlertsUrl(regionId) ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "fetchAlerts for region: $regionId")
        Thread {
            try {
                val feed = URL(pathUrl).openStream().use(GtfsRealtime.FeedMessage::parseFrom)
                val nowMs = if (feed.hasHeader() && feed.header.hasTimestamp()) {
                    feed.header.timestamp * 1_000L
                } else {
                    System.currentTimeMillis()
                }
                processAlerts(feed.entityList, nowMs, callback)
                fetchedRegions += regionId
            } catch (error: Exception) {
                Log.e(TAG, "Error fetching GTFS alert data for region: $regionId", error)
            }
        }.start()
    }

    fun processAlerts(
        alerts: List<GtfsRealtime.FeedEntity>,
        nowMs: Long,
        callback: GtfsAlertCallBack
    ) {
        for (entity in alerts) {
            if (!GtfsAlertsHelper.isValidEntity(context, entity, nowMs)) continue
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
    }
}
