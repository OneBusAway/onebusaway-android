package org.onebusaway.android.widealerts

import android.content.Context
import com.google.transit.realtime.GtfsRealtime
import java.util.Locale
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.database.widealerts.entity.AlertEntity

/** Pure selection helpers and read-state persistence for wide GTFS alerts. */
object GtfsAlertsHelper {
    fun getAlertTitle(alert: GtfsRealtime.Alert): String = localizedText(alert.headerText.translationList)

    fun getAlertDescription(alert: GtfsRealtime.Alert): String = localizedText(alert.descriptionText.translationList)

    fun getAlertUrl(alert: GtfsRealtime.Alert): String = localizedText(alert.url.translationList)

    private fun localizedText(
        translations: List<GtfsRealtime.TranslatedString.Translation>
    ): String {
        val language = getCurrentAppLanguageCode()
        var fallback = ""
        for (translation in translations) {
            if (!translation.hasLanguage()) continue
            when (translation.language) {
                language -> return translation.text
                DEFAULT_LANGUAGE_CODE -> fallback = translation.text
            }
        }
        return fallback
    }

    fun isValidEntity(
        context: Context,
        entity: GtfsRealtime.FeedEntity,
        nowMs: Long
    ): Boolean = isAgencyWideAlert(entity.alert) &&
        isHighSeverity(entity.alert) &&
        isStartDateWithin24Hours(entity.alert, nowMs) &&
        !isAlertRead(context, entity)

    private fun isAgencyWideAlert(alert: GtfsRealtime.Alert): Boolean = alert.informedEntityList.any { it.hasAgencyId() }

    private fun isHighSeverity(alert: GtfsRealtime.Alert): Boolean = alert.hasSeverityLevel() &&
        (
            alert.severityLevel == GtfsRealtime.Alert.SeverityLevel.SEVERE ||
                alert.severityLevel == GtfsRealtime.Alert.SeverityLevel.WARNING
            )

    /**
     * These alerts come straight off a raw GTFS-realtime feed, not the OBA API, so a period's
     * `start` is POSIX **seconds** per the GTFS-rt spec — a fixed unit, not a magnitude guess.
     * (The OBA `situation` path is the polymorphic one; its seconds-vs-millis rule lives in
     * `situationEpochToMillis` and does not apply here.)
     *
     * "Should we show this to the user in the next 24h" maps to `communication_period`, so it's
     * preferred when a feed populates it. `active_period` is `[deprecated = true]` as of the
     * proto shipped in gtfs-realtime-bindings 0.2.0, but feeds in the wild still only populate
     * it, so [activePeriodStartSec] stays as a fallback — dropping it would silence every alert
     * on those feeds. Tracked by https://github.com/OneBusAway/onebusaway-android/issues/2160.
     */
    fun isStartDateWithin24Hours(alert: GtfsRealtime.Alert, nowMs: Long): Boolean {
        val startSec = communicationPeriodStartSec(alert) ?: activePeriodStartSec(alert) ?: return false
        val elapsed = nowMs - startSec * MILLIS_PER_SECOND
        return elapsed in 0..DAY_MS
    }

    private fun communicationPeriodStartSec(alert: GtfsRealtime.Alert): Long? {
        if (alert.communicationPeriodCount == 0) return null
        return alert.getCommunicationPeriod(0).start
    }

    // Fallback for feeds that only populate the deprecated active_period; see the rationale on
    // isStartDateWithin24Hours. Drop this once communication_period is the only field in the wild.
    @Suppress("DEPRECATION")
    private fun activePeriodStartSec(alert: GtfsRealtime.Alert): Long? {
        if (alert.activePeriodCount == 0) return null
        return alert.getActivePeriod(0).start
    }

    private fun isAlertRead(context: Context, entity: GtfsRealtime.FeedEntity): Boolean = DatabaseEntryPoint.get(context).alertsRepository().isAlertExists(entity.id)

    fun markAlertAsRead(context: Context, entity: GtfsRealtime.FeedEntity) {
        DatabaseEntryPoint.get(context).alertsRepository().insertAlert(AlertEntity(entity.id))
    }

    private fun getCurrentAppLanguageCode(): String = Locale.getDefault().language

    private const val DEFAULT_LANGUAGE_CODE = "en"
    private const val MILLIS_PER_SECOND = 1_000L
    private const val DAY_MS = 24L * 60L * 60L * 1_000L
}
