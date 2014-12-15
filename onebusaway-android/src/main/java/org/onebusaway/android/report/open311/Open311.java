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
package org.onebusaway.android.report.open311;

import org.apache.http.HttpEntity;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.report.open311.io.Open311ConnectionManager;
import org.onebusaway.android.report.open311.models.Open311Option;
import org.onebusaway.android.report.open311.models.ServiceDescription;
import org.onebusaway.android.report.open311.models.ServiceListRequest;
import org.onebusaway.android.report.open311.models.ServiceListResponse;
import org.onebusaway.android.report.open311.models.ServiceRequest;
import org.onebusaway.android.report.open311.models.ServiceRequestResponse;
import org.onebusaway.android.report.open311.utils.Open311Parser;
import org.onebusaway.android.report.open311.utils.Open311UrlUtil;

import java.io.UnsupportedEncodingException;

/**
 * Open311 class for managing actions to submit request with open311
 * @author Cagri Cetin
 */
public class Open311 {

    private Open311Option open311Option;
    private String format = "json";

    private Open311ConnectionManager connectionManager = new Open311ConnectionManager();

    /**
     * Constructor with open311 option
     */
    protected Open311(Open311Option open311Option) {
        this.open311Option = open311Option;
    }

    /**
     * Method for getting service list
     * @param serviceListRequest takes request params
     * @return ServiceListResponse object
     */
    public ServiceListResponse getServiceList(ServiceListRequest serviceListRequest) {
        HttpEntity httpEntity = null;
        try {
            serviceListRequest.setJurisdictionId(open311Option.getJurisdiction());
            httpEntity = Open311UrlUtil.prepareUrlEncodedEntity(serviceListRequest);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String result = connectionManager.getStringResult(Open311UrlUtil.getServiceUrl(open311Option.getBaseUrl(), format), Open311UrlUtil.RequestMethod.GET, httpEntity);
        return Open311Parser.parseServices(result);
    }

    /**
     *
     * @return service list response without
     *          passing parameter
     *
     *          May return generic service list
     */
    public ServiceListResponse getServiceList() {
        return getServiceList(null);
    }

    /**
     * method for creating service request
     * @param serviceRequest takes service request object
     * @return result from open311 endpoint
     */
    public ServiceRequestResponse postServiceRequest(ServiceRequest serviceRequest) {
        HttpEntity httpEntity = null;
        try {
            if (serviceRequest.getMedia_url() == null) {
                httpEntity = Open311UrlUtil.prepareUrlEncodedEntity(serviceRequest);
            } else {
                httpEntity = Open311UrlUtil.prepareMultipartEntity(serviceRequest, Application.get().getBaseContext());
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String result = connectionManager.getStringResult(Open311UrlUtil.getServiceRequestUrl(open311Option.getBaseUrl(), format), Open311UrlUtil.RequestMethod.POST, httpEntity);
        return Open311Parser.parseRequestResponse(result, open311Option.getOpen311Type());
    }

    /**
     * method for getting current service requests from server
     * @param serviceRequest takes service request object
     * @return result from open311 endpoint
     */
    @SuppressWarnings("UnusedDeclaration")
    public ServiceRequestResponse getServiceRequest(ServiceRequest serviceRequest) {
        HttpEntity httpEntity = null;
        try {
            httpEntity = Open311UrlUtil.prepareUrlEncodedEntity(serviceRequest);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String result = connectionManager.getStringResult(Open311UrlUtil.getServiceRequestUrl(open311Option.getBaseUrl(), format), Open311UrlUtil.RequestMethod.GET, httpEntity);
        return Open311Parser.parseRequestResponse(result, open311Option.getOpen311Type());
    }

    /**
     * method for getting description of the services
     * @param serviceListRequest takes service request object
     * @return result from open311 endpoint
     */
    public ServiceDescription getServiceDescription(ServiceListRequest serviceListRequest) {
        HttpEntity httpEntity = null;
        try {
            serviceListRequest.setJurisdictionId(open311Option.getJurisdiction());
            httpEntity = Open311UrlUtil.prepareUrlEncodedEntity(serviceListRequest);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String result = connectionManager.getStringResult(Open311UrlUtil.getServiceDescUrl(open311Option.getBaseUrl(), serviceListRequest.getServiceCode(), format), Open311UrlUtil.RequestMethod.GET, httpEntity);
        return Open311Parser.parseServiceDescription(result);
    }

    /**
     *
     * @return base url of the open311 server
     */
    @SuppressWarnings("UnusedDeclaration")
    public String getBaseUrl() {
        return open311Option.getBaseUrl();
    }

    public String getJurisdiction() {
        return getOpen311Option().getJurisdiction();
    }

    public String getApiKey() {
        return open311Option.getApiKey();
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Open311Option getOpen311Option() {
        return open311Option;
    }
}
