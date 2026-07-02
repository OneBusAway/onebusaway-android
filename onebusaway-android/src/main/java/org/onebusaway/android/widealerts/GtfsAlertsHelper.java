package org.onebusaway.android.widealerts;

import com.google.transit.realtime.GtfsRealtime;

import org.onebusaway.android.database.widealerts.AlertsRepository;
import org.onebusaway.android.database.widealerts.entity.AlertEntity;

import android.content.Context;

import java.util.Locale;

/**
 * Helper class for GTFS alerts.
 */
public class GtfsAlertsHelper {
    private static final String DEFAULT_LANGUAGE_CODE = "en";

    /**
     * Retrieves the title of the alert in the current app language or default language.
     *
     * @param alert The GTFS alert.
     * @return The alert title.
     */
    public static String getAlertTitle(GtfsRealtime.Alert alert) {
        String currentLanguageCode = getCurrentAppLanguageCode();
        String title = "";

        for (GtfsRealtime.TranslatedString.Translation translation : alert.getHeaderText().getTranslationList()) {
            if (translation.hasLanguage()) {
                if (translation.getLanguage().equals(currentLanguageCode)) {
                    return translation.getText();
                } else if (translation.getLanguage().equals(DEFAULT_LANGUAGE_CODE)) {
                    title = translation.getText();
                }
            }
        }
        return title;
    }

    /**
     * Retrieves the description of the alert in the current app language or default language.
     *
     * @param alert The GTFS alert.
     * @return The alert description.
     */
    public static String getAlertDescription(GtfsRealtime.Alert alert) {
        String currentLanguageCode = getCurrentAppLanguageCode();
        String description = "";

        for (GtfsRealtime.TranslatedString.Translation translation : alert.getDescriptionText().getTranslationList()) {
            if (translation.hasLanguage()) {
                if (translation.getLanguage().equals(currentLanguageCode)) {
                    return translation.getText();
                } else if (translation.getLanguage().equals(DEFAULT_LANGUAGE_CODE)) {
                    description = translation.getText();
                }
            }
        }
        return description;
    }

    /**
     * Retrieves the URL of the alert in the current app language or default language.
     *
     * @param alert The GTFS alert.
     * @return The alert URL.
     */
    public static String getAlertUrl(GtfsRealtime.Alert alert) {
        String currentLanguageCode = getCurrentAppLanguageCode();
        String url = "";

        for (GtfsRealtime.TranslatedString.Translation translation : alert.getUrl().getTranslationList()) {
            if (translation.hasLanguage()) {
                if (translation.getLanguage().equals(currentLanguageCode)) {
                    return translation.getText();
                } else if (translation.getLanguage().equals(DEFAULT_LANGUAGE_CODE)) {
                    url = translation.getText();
                }
            }
        }
        return url;
    }


    /**
     * Checks if the entity is valid based on agency-wide, severity, and start date criteria.
     *
     * @param entity The GTFS entity.
     * @param feedTimeMs The feed header's generation time (server clock, epoch millis), or 0 when the
     *                   feed carried no timestamp — used as "now" for the start-date window (#1612).
     * @return True if the alert is valid, false otherwise.
     */
    public static boolean isValidEntity(Context context, GtfsRealtime.FeedEntity entity, long feedTimeMs) {
        return isAgencyWideAlert(entity.getAlert()) && isHighSeverity(entity.getAlert()) && isStartDateWithin24Hours(entity.getAlert(), feedTimeMs) && !isAlertRead(context, entity);
    }

    /**
     * Checks if the alert is agency-wide.
     *
     * @param alert The GTFS alert.
     * @return True if the alert is agency-wide, false otherwise.
     */
    public static boolean isAgencyWideAlert(GtfsRealtime.Alert alert) {
        for (GtfsRealtime.EntitySelector es : alert.getInformedEntityList()) {
            if (es.hasAgencyId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the alert has high severity.
     *
     * @param alert The GTFS alert.
     * @return True if the alert has high severity, false otherwise.
     */
    public static boolean isHighSeverity(GtfsRealtime.Alert alert) {
        return alert.hasSeverityLevel() && (alert.getSeverityLevel() == GtfsRealtime.Alert.SeverityLevel.SEVERE || alert.getSeverityLevel() == GtfsRealtime.Alert.SeverityLevel.WARNING);
    }

    /**
     * Checks if the alert start date is within the last 24 hours.
     *
     * <p>Uses the feed's server-clock generation time as "now" so the window cancels device clock
     * skew — the alert's server {@code start} and the "now" it's measured against share one clock
     * (#1612). Falls back to the device clock only when the feed carried no header timestamp.
     *
     * @param alert The GTFS alert.
     * @param feedTimeMs The feed header's generation time (server clock, epoch millis), or 0 to fall
     *                   back to the device clock.
     * @return True if the start date is within the last 24 hours, false otherwise.
     */
    public static boolean isStartDateWithin24Hours(GtfsRealtime.Alert alert, long feedTimeMs) {
        long currentTime = feedTimeMs > 0 ? feedTimeMs : System.currentTimeMillis();
        long startTime = alert.getActivePeriod(0).getStart() * 1000L;
        return (currentTime - startTime) <= 24 * 60 * 60 * 1000L;
    }

    /**
     * Checks if the alert has already been read.
     *
     * @param context The context to access the database.
     * @param entity  The GTFS alert entity to check.
     * @return True if the alert exists in the database, false otherwise.
     */

    public static boolean isAlertRead(Context context, GtfsRealtime.FeedEntity entity) {
        return AlertsRepository.isAlertExists(context, entity.getId());
    }

    /**
     * Marks the alert as read by inserting it into the database.
     *
     * @param context The context to access the database.
     * @param entity The `GtfsRealtime.FeedEntity` object representing the alert.
     */
    public static void markAlertAsRead(Context context, GtfsRealtime.FeedEntity entity) {
        AlertsRepository.insertAlert(context, new AlertEntity(entity.getId()));
    }

    public static String getCurrentAppLanguageCode() {
        return Locale.getDefault().getLanguage();
    }
}
