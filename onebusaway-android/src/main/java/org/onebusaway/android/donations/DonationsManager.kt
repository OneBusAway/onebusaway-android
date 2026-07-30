package org.onebusaway.android.donations

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.R
import org.onebusaway.android.analytics.PlausibleAnalytics
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.util.BuildFlavorUtils
import org.onebusaway.android.util.PreferenceUtils

@Singleton
class DonationsManager @Inject constructor(
    @param:ApplicationContext private val context: Context?,
    private val preferences: PreferencesRepository?
) {
    private fun reportUiEvent(resourceId: Int, state: String?) {
        val appContext = checkNotNull(context)
        AnalyticsEntryPoint.get(appContext).reportUiEvent(
            PlausibleAnalytics.REPORT_DONATE_EVENT_URL,
            appContext.getString(resourceId),
            state
        )
    }

    fun getDonationRequestDismissedDate(): Date? = PreferenceUtils.getLong(DISMISSED_DATE_KEY, -1)
        .takeIf { it >= 1 }
        ?.let(::Date)

    fun setDonationRequestDismissedDate(value: Date?) {
        PreferenceUtils.saveLong(DISMISSED_DATE_KEY, value?.time ?: -1)
    }

    fun dismissDonationRequests() {
        setDonationRequestDismissedDate(Date())
    }

    fun getDonationRequestReminderDate(): Date? = PreferenceUtils.getLong(REMINDER_DATE_KEY, -1)
        .takeIf { it >= 1 }
        ?.let(::Date)

    fun setDonationRequestReminderDate(value: Date?) {
        PreferenceUtils.saveLong(REMINDER_DATE_KEY, value?.time ?: -1)
    }

    fun remindUserLater() {
        setDonationRequestReminderDate(Date(Date().time + TWO_WEEKS_MS))
    }

    fun shouldShowDonationUI(): Boolean {
        if (!BuildFlavorUtils.isOBABuildFlavor()) return false
        if (checkNotNull(preferences).getAppLaunchCount() < 3) return false
        if (getDonationRequestReminderDate()?.after(Date()) == true) return false
        return getDonationRequestDismissedDate() == null
    }

    fun reportDonateButtonPress() {
        reportUiEvent(R.string.analytics_label_button_press_donate, null)
    }

    fun donateUrl(): String = checkNotNull(context).getString(R.string.donate_url)

    fun buildOpenDonationsPageIntent(): Intent {
        reportDonateButtonPress()
        return Intent(Intent.ACTION_VIEW, donateUrl().toUri())
    }

    private companion object {
        const val DISMISSED_DATE_KEY = "donationRequestDismissedDateKey"
        const val REMINDER_DATE_KEY = "donationRequestReminderDateKey"
        const val TWO_WEEKS_MS = 14L * 24L * 60L * 60L * 1_000L
    }
}
