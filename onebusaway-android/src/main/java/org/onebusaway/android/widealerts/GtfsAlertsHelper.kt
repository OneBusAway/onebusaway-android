package org.onebusaway.android.widealerts

import android.content.Context
import com.google.transit.realtime.GtfsRealtime
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import org.onebusaway.android.app.di.DatabaseEntryPoint
import org.onebusaway.android.database.widealerts.entity.AlertEntity
import org.onebusaway.android.time.ServerTime

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
        now: ServerTime
    ): Boolean = isAgencyWideAlert(entity.alert) &&
        isHighSeverity(entity.alert) &&
        isStartDateWithin24Hours(entity.alert, now) &&
        !isAlertRead(context, entity)

    private fun isAgencyWideAlert(alert: GtfsRealtime.Alert): Boolean = alert.informedEntityList.any { it.hasAgencyId() }

    private fun isHighSeverity(alert: GtfsRealtime.Alert): Boolean = alert.hasSeverityLevel() &&
        (
            alert.severityLevel == GtfsRealtime.Alert.SeverityLevel.SEVERE ||
                alert.severityLevel == GtfsRealtime.Alert.SeverityLevel.WARNING
            )

    /**
     * True when *any* range of the alert's selected period list started within the last 24 hours of
     * [now] — the feed's own server clock (#1612), so device clock skew can't leak into the window.
     *
     * `communication_period` and `active_period` are both `repeated TimeRange`, and the spec treats
     * an alert as applicable during **any** of its configured ranges, so every range is checked
     * rather than index 0: an alert whose first range is stale but whose second opened an hour ago
     * is exactly the case index-0-only silently dropped
     * (https://github.com/OneBusAway/onebusaway-android/issues/2175).
     */
    fun isStartDateWithin24Hours(alert: GtfsRealtime.Alert, now: ServerTime): Boolean = selectedPeriods(alert).any { range ->
        // `start` is optional: a range without one begins at "the beginning of time" per the spec,
        // which is never within the last 24h — so an absent start is a non-match, not an epoch 0.
        range.hasStart() && (now - serverTimeFromGtfsSeconds(range.start)) in RECENT_START_WINDOW
    }

    /**
     * The period list the "did it start recently" question is answered from.
     *
     * "Should we show this to the user in the next 24h" maps to `communication_period`, so it's
     * preferred when a feed populates it. `active_period` is `[deprecated = true]` as of the proto
     * shipped in gtfs-realtime-bindings 0.2.0, but feeds in the wild still only populate it, so
     * [deprecatedActivePeriods] stays as a fallback — dropping it would silence every alert on those
     * feeds. Tracked by https://github.com/OneBusAway/onebusaway-android/issues/2160.
     */
    private fun selectedPeriods(alert: GtfsRealtime.Alert): List<GtfsRealtime.TimeRange> = if (alert.communicationPeriodCount > 0) {
        alert.communicationPeriodList
    } else {
        deprecatedActivePeriods(alert)
    }

    // Fallback for feeds that only populate the deprecated active_period; see the rationale on
    // selectedPeriods. Drop this once communication_period is the only field in the wild.
    @Suppress("DEPRECATION")
    private fun deprecatedActivePeriods(alert: GtfsRealtime.Alert): List<GtfsRealtime.TimeRange> = alert.activePeriodList

    /**
     * Mints a raw GTFS-realtime timestamp into the server clock domain. These alerts come straight
     * off a raw GTFS-realtime feed, not the OBA API, so the wire unit is POSIX **seconds** per the
     * GTFS-rt spec — a fixed unit, not a magnitude guess. (The OBA `situation` path is the
     * polymorphic one; its seconds-vs-millis rule lives in `situationEpochToMillis` and does not
     * apply here.) This is the one place that conversion happens for this feed.
     */
    fun serverTimeFromGtfsSeconds(epochSec: Long): ServerTime = ServerTime(epochSec * MILLIS_PER_SECOND)

    private fun isAlertRead(context: Context, entity: GtfsRealtime.FeedEntity): Boolean = DatabaseEntryPoint.get(context).alertsRepository().isAlertExists(entity.id)

    fun markAlertAsRead(context: Context, entity: GtfsRealtime.FeedEntity) {
        DatabaseEntryPoint.get(context).alertsRepository().insertAlert(AlertEntity(entity.id))
    }

    private fun getCurrentAppLanguageCode(): String = Locale.getDefault().language

    private const val DEFAULT_LANGUAGE_CODE = "en"
    private const val MILLIS_PER_SECOND = 1_000L

    /** How long ago a period may have started and still count as "just started". */
    private val RECENT_START_WINDOW = Duration.ZERO..24.hours
}
