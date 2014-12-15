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
package org.onebusaway.android.report.open311.utils;

import org.onebusaway.android.report.open311.constants.Open311Constants;
import org.onebusaway.android.report.open311.constants.Open311Type;
import org.onebusaway.android.report.open311.models.Open311Attribute;
import org.onebusaway.android.report.open311.models.Open311AttributePair;
import org.onebusaway.android.report.open311.models.ServiceDescription;
import org.onebusaway.android.report.open311.models.ServiceRequest;

import java.util.List;

/**
 * Validator class for open311 requests
 * @author Cagri Cetin
 */
public class Open311Validator {

    //Error codes for validation
    public static final int PROBLEM_CODE_SERVICE = 1;
    public static final int PROBLEM_CODE_USER_NAME = 2;
    public static final int PROBLEM_CODE_USER_LASTNAME = 3;
    public static final int PROBLEM_CODE_USER_EMAIL = 4;
    public static final int PROBLEM_CODE_LOCATION = 5;
    public static final int PROBLEM_CODE_DESC = 6;
    public static final int PROBLEM_CODE_MANDATORY_QUESTION = 7;
    public static final int PROBLEM_CODE_OK = 0;

    /**
     * Validates service request
     * @param serviceRequest
     * @param open311Type Different config for different backend
     * @param serviceDescription validates also service description
     * @return error code
     */
    public static int validateServiceRequest(ServiceRequest serviceRequest, Open311Type open311Type, ServiceDescription serviceDescription) {

        if (serviceRequest.getService_code() == null) {
            return PROBLEM_CODE_SERVICE;
        }

        if (serviceRequest.getLat() == null || serviceRequest.getLang() == null) {
            return PROBLEM_CODE_LOCATION;
        }

        if (serviceRequest.getDescription() == null || "".equals(serviceRequest.getDescription())) {
            return PROBLEM_CODE_DESC;
        }

        if (open311Type.equals(Open311Type.SEECLICKFIX)) {
            if (serviceRequest.getFirst_name() == null || "".equals(serviceRequest.getFirst_name())) {
                return PROBLEM_CODE_USER_NAME;
            } else if (serviceRequest.getLast_name() == null || "".equals(serviceRequest.getLast_name())) {
                return PROBLEM_CODE_USER_LASTNAME;
            } else if (serviceRequest.getEmail() == null || "".equals(serviceRequest.getEmail())) {
                return PROBLEM_CODE_USER_EMAIL;
            }
        }

        if (serviceRequest.getAttributes() != null && serviceDescription != null) {
            for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {
                if (open311Attribute.getRequired()) {
                    boolean valid = findAttribute(serviceRequest.getAttributes(), open311Attribute.getCode());
                    if (valid == false) {
                        return PROBLEM_CODE_MANDATORY_QUESTION;
                    }
                }
            }
        }

        return PROBLEM_CODE_OK;
    }

    private static boolean findAttribute(List<Open311AttributePair> attributes, Integer code) {
        for (Open311AttributePair attribute : attributes) {
            if (attribute.getCode() == code) {
                return true;
            }
        }
        return false;
    }

    public static int validateServiceRequest(ServiceRequest serviceRequest) {
        return validateServiceRequest(serviceRequest, Open311Type.DEFAULT, null);
    }

    public static int validateServiceRequest(ServiceRequest serviceRequest, Open311Type open311Type) {
        return validateServiceRequest(serviceRequest, open311Type, null);
    }

    /**
     *
     * @param errorCode
     * @return if the validation is valid
     */
    public static boolean isValid(int errorCode) {
        return errorCode == PROBLEM_CODE_OK;
    }

    /**
     * Gets default error message from error code
     * @param errorCode
     * @return
     */
    public static String getErrorMessageForServiceRequestByErrorCode(int errorCode) {

        switch (errorCode) {
            case PROBLEM_CODE_SERVICE:
                return Open311Constants.M_GENERIC_ISSUE_TYPE;
            case PROBLEM_CODE_USER_NAME:
                return Open311Constants.M_GENERIC_FIRST_NAME;
            case PROBLEM_CODE_USER_LASTNAME:
                return Open311Constants.M_GENERIC_LAST_NAME;
            case PROBLEM_CODE_USER_EMAIL:
                return Open311Constants.M_GENERIC_EMAIL;
            case PROBLEM_CODE_LOCATION:
                return Open311Constants.M_GENERIC_LOCATION;
            case PROBLEM_CODE_DESC:
                return Open311Constants.M_GENERIC_DESC;
            case PROBLEM_CODE_MANDATORY_QUESTION:
                return Open311Constants.M_GENERIC_MANDATORY_QUESTION;
            default:
                return Open311Constants.M_GENERIC_ERROR;

        }
    }

}
