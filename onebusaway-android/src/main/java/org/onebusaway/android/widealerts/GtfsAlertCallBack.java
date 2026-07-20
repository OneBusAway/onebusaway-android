package org.onebusaway.android.widealerts;

import androidx.annotation.NonNull;

/** Callback interface for GTFS alerts. */
public interface GtfsAlertCallBack {
  void onAlert(@NonNull String title, @NonNull String message, @NonNull String url);
}
