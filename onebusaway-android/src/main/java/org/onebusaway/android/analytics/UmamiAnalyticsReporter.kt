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
