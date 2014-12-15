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

import org.onebusaway.android.report.open311.constants.Open311Type;

/**
 * Model for initializing open311.
 * In one application there could be multiple open311 servers.
 * @author Cagri Cetin
 */
public class Open311Option {
    private String baseUrl;
    private String apiKey;
    private String jurisdiction;
    private Open311Type open311Type;

    public Open311Option(String baseUrl, String apiKey, String jurisdiction, Open311Type open311Type) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.jurisdiction = jurisdiction;
        this.open311Type = open311Type;
    }

    public Open311Option(String baseUrl, String apiKey, String jurisdiction) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.jurisdiction = jurisdiction;
        this.open311Type = Open311Type.DEFAULT;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public Open311Type getOpen311Type() {
        return open311Type;
    }

    public void setOpen311Type(Open311Type open311Type) {
        this.open311Type = open311Type;
    }
}
