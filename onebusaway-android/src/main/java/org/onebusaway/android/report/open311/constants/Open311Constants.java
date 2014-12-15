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

package org.onebusaway.android.report.open311.constants;

/**
 * Constants for open311
 * @author Cagri Cetin
 */
public class Open311Constants {
    public static final int WS_TIMEOUT = 6000;

    public static final String RESULT_OK = "200";
    public static final String RESULT_FAIL = "404";

    //Service Request Response JSON Attributes
    public static final String STATUS = "status";
    public static final String DESCRIPTION = "description";
    public static final String BASE = "base";
    public static final String DUPLICATE = "duplicate";
    public static final String ID = "id";
    public static final String REQUEST_ID = "service_request_id";
    public static final String TOKEN = "token";

    //Open311 generic messages
    public static final String M_REPORT_SUCCESS = "Your issue has been successfully reported";
    public static final String M_GENERIC_ERROR = "Error occurred.";
    public static final String M_GENERIC_ISSUE_TYPE = "Please select an issue type";
    public static final String M_GENERIC_FIRST_NAME = "First name cannot be empty.";
    public static final String M_GENERIC_LAST_NAME = "Last name cannot be empty.";
    public static final String M_GENERIC_EMAIL = "Email cannot be empty.";
    public static final String M_GENERIC_LOCATION = "Address cannot be empty.";
    public static final String M_GENERIC_DESC = "Description cannot be empty.";
    public static final String M_GENERIC_MANDATORY_QUESTION = "Required questions should be answered.";

}
