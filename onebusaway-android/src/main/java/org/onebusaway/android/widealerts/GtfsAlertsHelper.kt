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
        translations.filter { it.hasLanguage() }.forEach { translation ->
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

    fun isAgencyWideAlert(alert: GtfsRealtime.Alert): Boolean = alert.informedEntityList.any { it.hasAgencyId() }

    fun isHighSeverity(alert: GtfsRealtime.Alert): Boolean = alert.hasSeverityLevel() &&
        alert.severityLevel in setOf(
            GtfsRealtime.Alert.SeverityLevel.SEVERE,
            GtfsRealtime.Alert.SeverityLevel.WARNING
        )

    fun isStartDateWithin24Hours(alert: GtfsRealtime.Alert, nowMs: Long): Boolean {
        if (alert.activePeriodCount == 0) return false
        val elapsed = nowMs - alert.getActivePeriod(0).start * 1_000L
        return elapsed in 0..DAY_MS
    }

    fun isAlertRead(context: Context, entity: GtfsRealtime.FeedEntity): Boolean = DatabaseEntryPoint.get(context).alertsRepository().isAlertExists(entity.id)

    fun markAlertAsRead(context: Context, entity: GtfsRealtime.FeedEntity) {
        DatabaseEntryPoint.get(context).alertsRepository().insertAlert(AlertEntity(entity.id))
    }

    fun getCurrentAppLanguageCode(): String = Locale.getDefault().language

    private const val DEFAULT_LANGUAGE_CODE = "en"
    private const val DAY_MS = 24L * 60L * 60L * 1_000L
}
