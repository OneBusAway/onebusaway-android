/*
 * Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.ui.report

/** Constants used by report flows. */
object ReportConstants {
    // Map default values
    const val DEFAULT_SERVICE = "default"
    const val DYNAMIC_SERVICE = "dynamic"

    /** Number of transit-related services at which we assume all services are transit-related. */
    const val NUM_TRANSIT_SERVICES_THRESHOLD = 3

    // A static transit service is a default OBA stop or trip problem.
    const val STATIC_TRANSIT_SERVICE_STOP = "stop"
    const val STATIC_TRANSIT_SERVICE_TRIP = "trip"

    // A dynamic transit service comes from an Open311 endpoint.
    const val DYNAMIC_TRANSIT_SERVICE_STOP = "dynamic_stop"
    const val DYNAMIC_TRANSIT_SERVICE_TRIP = "dynamic_trip"
    const val ISSUE_GROUP_TRANSIT = "Transit"

    // Preferences keys
    const val PREF_NAME = "reporterName"
    const val PREF_LAST_NAME = "reporterLastname"
    const val PREF_PHONE = "reporterPhone"
    const val PREF_EMAIL = "reporterEmail"
    const val PREF_VALIDATED_REGION_ID = "validatedRegionId"
    const val TAG_REGION_VALIDATE_DIALOG = "1"
    const val TAG_CUSTOMER_SERVICE_FRAGMENT = "3"
}
