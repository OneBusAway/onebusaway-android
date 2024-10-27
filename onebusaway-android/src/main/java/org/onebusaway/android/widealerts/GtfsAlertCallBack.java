package org.onebusaway.android.widealerts;

/** Callback interface for GTFS alerts. */
public interface GtfsAlertCallBack {
    void onAlert(String title, String message, String url);
}
