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
package org.onebusaway.android.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import org.onebusaway.android.app.di.AnalyticsEntryPoint
import org.onebusaway.android.analytics.PlausibleAnalytics

/** Reports a settings UI event (label [labelRes]) to the preferences analytics page. */
internal fun reportPreferencesEvent(context: Context, @StringRes labelRes: Int) {
    AnalyticsEntryPoint.get(context).reportUiEvent(
        PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
        context.getString(labelRes),
        null,
    )
}
