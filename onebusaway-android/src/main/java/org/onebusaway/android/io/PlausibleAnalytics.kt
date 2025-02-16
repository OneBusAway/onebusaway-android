package org.onebusaway.android.io

import com.onebusaway.plausible.android.Plausible

/**
 * This class is used to report events to Plausible Analytics.
 */
object PlausibleAnalytics {

    private const val REPORT_VIEW_STOP_EVENT_URL = "app://localhost/stop"
    const val REPORT_SEARCH_EVENT_URL = "app://localhost/search"
    const val REPORT_REGION_EVENT_URL = "app://localhost/regions"
    const val REPORT_DONATE_EVENT_URL = "app://localhost/donations"
    const val REPORT_MAP_EVENT_URL = "app://localhost/map"
    const val REPORT_ARRIVALS_EVENT_URL = "app://localhost/arrivals"
    const val REPORT_FARE_PAYMENT_EVENT_URL = "app://localhost/fare-payment"
    const val REPORT_BOOKMARK_EVENT_URL = "app://localhost/bookmarks"
    const val REPORT_STOP_PROBLEM_EVENT_URL = "app://localhost/stop-problem"
    const val REPORT_VEHICLE_PROBLEM_EVENT_URL = "app://localhost/vehicle-problem"
    const val REPORT_MORE_EVENT_URL = "app://localhost/more"
    const val REPORT_MENU_EVENT_URL = "app://localhost/menu"
    const val REPORT_PREFERENCES_EVENT_URL = "app://localhost/preferences"
    const val REPORT_STARRED_STOPS_EVENT_URL = "app://localhost/starred-stops"
    const val REPORT_STARRED_ROUTES_EVENT_URL = "app://localhost/starred-routes"
    const val REPORT_BIKE_EVENT_URL = "app://localhost/bike"
    const val REPORT_BACKUP_EVENT_URL = "app://localhost/backup"
    const val REPORT_OPEN311_SERVER_EVENT_URL = "app://localhost/open311-server"
    const val REPORT_TRIP_PLANNER_EVENT_URL = "app://localhost/trip-planner"
    const val REPORT_DESTINATION_REMINDER_EVENT_URL = "app://localhost/destination-reminder"
    const val REPORT_REMINDERS_EVENT_URL = "app://localhost/reminders"

    /**
     * Report a UI event to Plausible.
     * @param plausible The Plausible instance to report to.
     * @param pageURL The URL of the page where the event occurred.
     * @param id The ID of the item that was selected.
     * @param state The state of the item that was selected.
     */
    @JvmStatic
    fun reportUiEvent(plausible: Plausible?, pageURL:String, id: String, state: String?) {
        if(plausible == null) return
        plausible.event("Item Selected", pageURL, props = mapOf("item_id" to id, "item_variant" to state))
    }

    /**
     * Report a search event to Plausible.
     * @param plausible The Plausible instance to report to.
     * @param query The search query.
     */
    @JvmStatic
    fun reportSearchEvent(plausible: Plausible?, query: String) {
        if(plausible == null) return
        plausible.event("Search", REPORT_SEARCH_EVENT_URL, props = mapOf("query" to query))
    }

    /**
     * Report a view stop event to Plausible.
     * @param plausible The Plausible instance to report to.
     * @param id The ID of the stop.
     * @param stopDistance a label indicating the proximity of the user to the stop
     */
    @JvmStatic
    fun reportViewStopEvent(plausible: Plausible?, id: String, stopDistance: String) {
        if(plausible == null) return
        plausible.pageView(REPORT_VIEW_STOP_EVENT_URL, props = mapOf("id" to id, "distance" to stopDistance))
    }
}