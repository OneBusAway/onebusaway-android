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
package org.onebusaway.android.analytics

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.onebusaway.android.preferences.PreferencesRepository

/**
 * A random, anonymous id generated once per install and persisted thereafter — deliberately never
 * derived from ANDROID_ID, an advertising id, or any other device identifier.
 *
 * Umami derives its visitor/session id as `uuid(website, IP, User-Agent, monthly salt)` *unless* the
 * event payload carries an explicit `payload.id`, in which case it uses `uuid(website, id)` instead.
 * (True through Umami v3.2; v3.3+ hash the IP back in, so the shared server must stay pinned to
 * <= v3.2 for this to dedupe visitors.)
 * Without one, every IP change (wifi <-> cellular) mints Umami a "new" visitor, inflating reported MAU
 * roughly 2x. Sending this id as `payload.id` on every Umami event keeps a device's sessions stable
 * across IP changes while staying anonymous — it never leaves the device with any other identifying
 * value attached, and it is independent of every other id the app uses (e.g. [org.onebusaway.android.
 * ui.survey.SurveyPreferences]'s survey UUID), so Umami usage can't be cross-referenced against surveys.
 *
 * Injected as an app-singleton so every [UmamiAnalytics] instance — one per region, rebuilt on region
 * change by [AnalyticsProvider] — shares the same id for the lifetime of the install.
 */
@Singleton
class AnalyticsInstallId @Inject constructor(
    private val prefs: PreferencesRepository
) {

    /** The install's anonymous id, generated and persisted on first access. */
    val value: String by lazy {
        prefs.getString(INSTALL_ID_KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.setString(INSTALL_ID_KEY, it)
        }
    }

    companion object {
        private const val INSTALL_ID_KEY = "analyticsInstallId"
    }
}
