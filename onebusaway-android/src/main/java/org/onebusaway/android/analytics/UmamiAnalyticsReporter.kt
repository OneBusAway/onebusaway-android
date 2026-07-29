/*
 * Copyright The OneBusAway Authors.
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

/**
 * Maps OneBusAway analytics events to [UmamiAnalytics] emitter calls. Mirrors
 * [PlausibleAnalytics] so Umami receives the same events as Plausible. Every method
 * is a no-op when [umami] is null (region without Umami config).
 */
object UmamiAnalyticsReporter {

    private const val REPORT_VIEW_STOP_EVENT_URL = "app://localhost/stop"

    @JvmStatic
    fun reportUiEvent(umami: UmamiAnalytics?, pageURL: String, id: String, state: String?) {
        if (umami == null) return
        umami.event("Item Selected", pageURL, mapOf("item_id" to id, "item_variant" to state))
    }

    @JvmStatic
    fun reportSearchEvent(umami: UmamiAnalytics?, query: String) {
        if (umami == null) return
        umami.event("Search", PlausibleAnalytics.REPORT_SEARCH_EVENT_URL, mapOf("query" to query))
    }

    @JvmStatic
    fun reportViewStopEvent(umami: UmamiAnalytics?, id: String, stopDistance: String) {
        if (umami == null) return
        umami.pageView(REPORT_VIEW_STOP_EVENT_URL, mapOf("id" to id, "distance" to stopDistance))
    }
}
