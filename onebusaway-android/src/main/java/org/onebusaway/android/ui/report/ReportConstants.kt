/* Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com) */
@file:Suppress("PropertyName")

package org.onebusaway.android.ui.report

/** Constants used by report flows. */
object ReportConstants {
    @JvmField var CAPTURE_PICTURE_INTENT = 1

    @JvmField var GALLERY_INTENT = 2
    const val DEFAULT_SERVICE = "default"
    const val DYNAMIC_SERVICE = "dynamic"
    const val NUM_TRANSIT_SERVICES_THRESHOLD = 3
    const val STATIC_TRANSIT_SERVICE_STOP = "stop"
    const val STATIC_TRANSIT_SERVICE_TRIP = "trip"
    const val DYNAMIC_TRANSIT_SERVICE_STOP = "dynamic_stop"
    const val DYNAMIC_TRANSIT_SERVICE_TRIP = "dynamic_trip"
    const val ISSUE_GROUP_TRANSIT = "Transit"
    const val PREF_NAME = "reporterName"
    const val PREF_LAST_NAME = "reporterLastname"
    const val PREF_PHONE = "reporterPhone"
    const val PREF_EMAIL = "reporterEmail"
    const val PREF_VALIDATED_REGION_ID = "validatedRegionId"
    const val TAG_REGION_VALIDATE_DIALOG = "1"
    const val TAG_CUSTOMER_SERVICE_FRAGMENT = "3"
}
