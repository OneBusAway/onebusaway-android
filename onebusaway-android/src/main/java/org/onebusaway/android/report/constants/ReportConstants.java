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
    public static final long MAP_MIN_TIME = 400;
    public static final float MAP_MIN_DISTANCE = 100;
    public static final String DEFAULT_SERVICE = "default";

    //Preferences keys
    public static final String PREF_NAME = "reporterName";
    public static final String PREF_LASTNAME = "reporterLastname";
    public static final String PREF_PHONE = "reporterPhone";
    public static final String PREF_EMAIL = "reporterEmail";

    public static final String TAG_REGION_VALIDATE_DIALOG = "1";
    public static final String TAG_CONTACT_INFO_FRAGMENT = "2";
    public static final String TAG_ISSUE_REPORT_FRAGMENT = "3";
}
