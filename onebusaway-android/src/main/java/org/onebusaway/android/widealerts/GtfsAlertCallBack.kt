package org.onebusaway.android.widealerts

/** Callback interface for GTFS alerts. */
fun interface GtfsAlertCallBack {
    fun onAlert(title: String, message: String, url: String)
}
