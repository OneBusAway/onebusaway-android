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
     * These alerts come straight off a raw GTFS-realtime feed, not the OBA API, so
     * `active_period.start` is POSIX **seconds** per the GTFS-rt spec — a fixed unit, not a
     * magnitude guess. (The OBA `situation` path is the polymorphic one; its seconds-vs-millis
     * rule lives in `situationEpochToMillis` and does not apply here.)
     */
    fun isStartDateWithin24Hours(alert: GtfsRealtime.Alert, nowMs: Long): Boolean {
        if (alert.activePeriodCount == 0) return false
        val elapsed = nowMs - alert.getActivePeriod(0).start * MILLIS_PER_SECOND
        return elapsed in 0..DAY_MS
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
