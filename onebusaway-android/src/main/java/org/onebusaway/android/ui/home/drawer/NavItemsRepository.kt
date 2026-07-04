/*
 * Copyright (C) 2026 Open Transit Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui.home.drawer

import android.text.TextUtils
import javax.inject.Inject
import org.onebusaway.android.R
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.util.ReminderUtils

/** The (app-global) inputs that gate which home nav-drawer rows are shown — surfaced as a `StateFlow`
 *  by [NavDrawerViewModel] for [HomeNavDrawerSheet] to render. */
data class NavItemAvailability(
    val showReminders: Boolean,
    val planTripAvailable: Boolean,
    val payFareAvailable: Boolean,
)

/**
 * Supplies the region/preference-derived gating inputs for the home nav drawer, so [NavDrawerViewModel]
 * can surface the drawer's row gating without reaching into Android statics — and stays unit-testable
 * behind a fake.
 */
interface NavItemsRepository {

    fun availability(): NavItemAvailability
}

/**
 * Default implementation reading the app-global region + reminder preference (the static reads lifted
 * out of HomeActivity.refreshDrawerItems). Trip-planning needs a region OTP url (or a custom one); fare
 * payment needs the region's payment app id; reminders follow the preference.
 */
class DefaultNavItemsRepository @Inject constructor(
    private val regionRepository: RegionRepository,
    private val prefs: PreferencesRepository,
) : NavItemsRepository {

    override fun availability(): NavItemAvailability {
        val region = regionRepository.region.value
        return NavItemAvailability(
            showReminders = ReminderUtils.shouldShowReminders(),
            planTripAvailable = region != null &&
                (!TextUtils.isEmpty(region.otpBaseUrl) ||
                    !TextUtils.isEmpty(
                        prefs.getString(R.string.preference_key_otp_api_url, null))),
            payFareAvailable = region != null && !TextUtils.isEmpty(region.paymentAndroidAppId),
        )
    }
}
