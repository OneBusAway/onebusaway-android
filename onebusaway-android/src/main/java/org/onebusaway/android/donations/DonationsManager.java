package org.onebusaway.android.donations;

import org.onebusaway.android.R;
import org.onebusaway.android.app.di.AnalyticsEntryPoint;
import org.onebusaway.android.analytics.PlausibleAnalytics;
import org.onebusaway.android.preferences.PreferencesRepository;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.PreferenceUtils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class DonationsManager {

    // Application context, used at event time to (a) read Resources for event labels / the donate URL and
    // (b) lazily resolve the AnalyticsProvider (for the region-derived Plausible emitter). Resolving the
    // AnalyticsProvider lazily — rather than injecting it into this constructor — keeps the constructor off
    // the region graph: AnalyticsProvider pulls in the RegionRepository, which is deliberately constructed
    // lazily to avoid the region-init ordering hazard.
    private final Context mContext;

    private final PreferencesRepository mPreferences;

    // The constructor only captures its collaborators — no dereferencing — so a bare instance can back
    // the JVM unit tests of consumers whose exercised paths never touch the manager. Resources and the
    // launch count are read lazily at their use sites.
    @Inject
    public DonationsManager(
            @ApplicationContext Context context,
            PreferencesRepository preferences) {
        this.mContext = context;
        this.mPreferences = preferences;
    }

    // region Analytics

    /**
     * Reports UI events using Firebase
     * @param resourceID ID of the UI element to report
     * @param state the state or variant of the UI item, or null if the item doesn't have a state or variant
     */
    private void reportUIEvent(Integer resourceID, String state) {
        AnalyticsEntryPoint.get(mContext).reportUiEvent(PlausibleAnalytics.REPORT_DONATE_EVENT_URL, mContext.getResources().getString(resourceID), state);
    }

    // endregion

    // Preference Keys

    private static String donationRequestDismissedDateKey = "donationRequestDismissedDateKey";
    private static String donationRequestReminderDateKey = "donationRequestReminderDateKey";

    // region Donation Request Dismissal

    /**
     * @return The date that donation requests were hidden by the user, either because they donated
     * or because they tapped the 'dismiss' button, or null if the date has not been set.
     */
    public Date getDonationRequestDismissedDate() {
        long timestamp = PreferenceUtils.getLong(donationRequestDismissedDateKey, -1);
        if (timestamp < 1) {
            return null;
        }

        return new Date(timestamp);
    }

    /**
     * Sets the date that the donation request UI was dismissed on. Pass in null to 'reset' the UI.
     * @param date The dismissal date.
     */
    public void setDonationRequestDismissedDate(Date date) {
        PreferenceUtils.saveLong(
                donationRequestDismissedDateKey,
                date == null ? -1 : date.getTime()
        );
    }

    /**
     * Hides subsequent attempts to request donations.
     */
    public void dismissDonationRequests() {
        setDonationRequestDismissedDate(new Date());
    }

    // endregion

    // region Donation Request Reminder

    /**
     * @return Optional date at which the app should remind the user to donate.
     */
    public Date getDonationRequestReminderDate() {
        long timestamp = PreferenceUtils.getLong(donationRequestReminderDateKey, -1);
        if (timestamp < 1) {
            return null;
        }

        return new Date(timestamp);
    }

    public void setDonationRequestReminderDate(Date date) {
        PreferenceUtils.saveLong(
                donationRequestReminderDateKey,
                date == null ? -1 : date.getTime()
        );
    }

    /**
     *  Sets a date two weeks in the future on which the app will remind the user to donate.
     */
    public void remindUserLater() {
        long twoWeeksInMilliseconds = 86400 * 14 * 1000; // Seconds in a day * 14 days * 1000 milliseconds
        Date futureDate = new Date((new Date()).getTime() + twoWeeksInMilliseconds);
        setDonationRequestReminderDate(futureDate);
    }

    // endregion

    // region State

    /**
     * Determines whether the app should show the donation UI based on a number of factors.
     * @return True if the donation UI should be shown; false otherwise.
     */
    public boolean shouldShowDonationUI() {
        // white-label apps should not show the donation UI.
        if (!BuildFlavorUtils.isOBABuildFlavor()) {
            return false;
        }

        // Don't show the donations UI on the first few launches of the app.
        if (mPreferences.getAppLaunchCount() < 3) {
            return false;
        }

        // Don't show the UI if there's a reminder date that is still in the future.
        Date reminderDate = getDonationRequestReminderDate();
        if (reminderDate != null && reminderDate.after(new Date())) {
            return false;
        }

        // Show the donation UI if the user has not explicitly dismissed it.
        return getDonationRequestDismissedDate() == null;
    }

    // endregion

    // region UI/Activities

    public Intent buildOpenDonationsPageIntent() {
        reportUIEvent(R.string.analytics_label_button_press_donate, null);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mContext.getResources().getString(R.string.donate_url)));
        return intent;
    }

    // endregion
}
