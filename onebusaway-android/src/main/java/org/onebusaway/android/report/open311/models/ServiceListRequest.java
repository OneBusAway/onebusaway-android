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

package org.onebusaway.android.report.open311.models;

/**
 * Simple model for service list requests
 * @author Cagri Cetin
 */

public class ServiceListRequest {

    private Double lat;
    private Double lang;
    private String address;
    private String jurisdictionId;
    private String serviceCode;

    public ServiceListRequest(Double lat, Double lang) {
        this.lat = lat;
        this.lang = lang;
    }

    public ServiceListRequest(String address) {
        this.address = address;
    }

    public ServiceListRequest(Double lat, Double lang, String address, String jurisdictionId) {
        this.lat = lat;
        this.lang = lang;
        this.address = address;
        this.jurisdictionId = jurisdictionId;
    }

    public ServiceListRequest() {

    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLang() {
        return lang;
    }

    public void setLang(Double lang) {
        this.lang = lang;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getJurisdictionId() {
        return jurisdictionId;
    }

    public void setJurisdictionId(String jurisdictionId) {
        this.jurisdictionId = jurisdictionId;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }
}
