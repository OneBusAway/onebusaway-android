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

import java.util.List;

/**
 * model for open311 service description
 * @author Cagri Cetin
 */
public class ServiceDescription extends Open311BaseModel {

    private String service_code;
    private String service_name;
    private List<Open311Attribute> attributes;

    public String getService_code() {
        return service_code;
    }

    public void setService_code(String service_code) {
        this.service_code = service_code;
    }

    public String getService_name() {
        return service_name;
    }

    public void setService_name(String service_name) {
        this.service_name = service_name;
    }

    public List<Open311Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Open311Attribute> attributes) {
        this.attributes = attributes;
    }
}
