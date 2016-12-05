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

package org.onebusaway.android.report.constants;

/**
 * Constants used in report implementation
 * @author Cagri Cetin
 */
public class ReportConstants {

    //Intent numbers
    public static int CAPTURE_PICTURE_INTENT = 1;
    public static int GALLERY_INTENT = 2;

    //Map default values
    public static final String DEFAULT_SERVICE = "default";
    public static final String DYNAMIC_SERVICE = "dynamic";

    // Number of transit-related services at which we assume all services are transit-related
    public static final int NUM_TRANSIT_SERVICES_THRESHOLD = 4;

    // Static Transit service indicates that the service is default OBA stop or trip problem
    public static final String STATIC_TRANSIT_SERVICE_STOP = "stop";
    public static final String STATIC_TRANSIT_SERVICE_TRIP = "trip";

    // Dynamic Transit service indicates that the service is coming from open311 endpoint
    public static final String DYNAMIC_TRANSIT_SERVICE_STOP = "dynamic_stop";
    public static final String DYNAMIC_TRANSIT_SERVICE_TRIP = "dynamic_trip";

    public static final String ISSUE_GROUP_TRANSIT = "Transit";

    //Preferences keys
    public static final String PREF_NAME = "reporterName";
    public static final String PREF_LAST_NAME = "reporterLastname";
    public static final String PREF_PHONE = "reporterPhone";
    public static final String PREF_EMAIL = "reporterEmail";
    public static final String PREF_VALIDATED_REGION_ID = "validatedRegionId";

    public static final String TAG_REGION_VALIDATE_DIALOG = "1";
    public static final String TAG_CUSTOMER_SERVICE_FRAGMENT = "3";
}
