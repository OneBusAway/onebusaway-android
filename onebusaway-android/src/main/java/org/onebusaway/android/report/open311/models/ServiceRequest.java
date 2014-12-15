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

import java.io.File;
import java.util.List;

/**
 *
 * Model for requesting service from any open311 server.
 * @author Cagri Cetin
 */
public class ServiceRequest {

    private String jurisdiction_id;
    private String service_code;
    private String service_name;
    private String api_key;
    private Double lat;
    private Double lang;
    private String address_string;
    private String email;
    private String device_id;
    private String account_id;
    private String first_name;
    private String last_name;
    private String phone;
    private String description;
    private String summary;
    private String media_url;

    private byte[] media;
    private List<Open311AttributePair> attributes;

    public ServiceRequest(String jurisdiction_id, String service_code, String api_key) {
        this.jurisdiction_id = jurisdiction_id;
        this.service_code = service_code;
        this.api_key = api_key;
    }

    public ServiceRequest(String jurisdiction_id, String service_code, String service_name, String api_key, Double lat, Double lang, String address_string, String email, String device_id, String account_id, String first_name, String last_name, String phone, String description, String summary, String media_url, byte[] media) {
        this.jurisdiction_id = jurisdiction_id;
        this.service_code = service_code;
        this.service_name = service_name;
        this.api_key = api_key;
        this.lat = lat;
        this.lang = lang;
        this.address_string = address_string;
        this.email = email;
        this.device_id = device_id;
        this.account_id = account_id;
        this.first_name = first_name;
        this.last_name = last_name;
        this.phone = phone;
        this.description = description;
        this.summary = summary;
        this.media_url = media_url;
        this.media = media;
    }

    public String getJurisdiction_id() {
        return jurisdiction_id;
    }

    public void setJurisdiction_id(String jurisdiction_id) {
        this.jurisdiction_id = jurisdiction_id;
    }

    public String getService_code() {
        return service_code;
    }

    public void setService_code(String service_code) {
        this.service_code = service_code;
    }

    public String getApi_key() {
        return api_key;
    }

    public void setApi_key(String api_key) {
        this.api_key = api_key;
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

    public String getAddress_string() {
        return address_string;
    }

    public void setAddress_string(String address_string) {
        this.address_string = address_string;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDevice_id() {
        return device_id;
    }

    public void setDevice_id(String device_id) {
        this.device_id = device_id;
    }

    public String getAccount_id() {
        return account_id;
    }

    public void setAccount_id(String account_id) {
        this.account_id = account_id;
    }

    public String getFirst_name() {
        return first_name;
    }

    public void setFirst_name(String first_name) {
        this.first_name = first_name;
    }

    public String getLast_name() {
        return last_name;
    }

    public void setLast_name(String last_name) {
        this.last_name = last_name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMedia_url() {
        return media_url;
    }

    public void setMedia_url(String media_url) {
        this.media_url = media_url;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public byte[] getMedia() {
        return media;
    }

    public void setMedia(byte[] media) {
        this.media = media;
    }

    public List<Open311AttributePair> getAttributes() {
        return attributes;
    }

    public String getService_name() {
        return service_name;
    }

    public void setService_name(String service_name) {
        this.service_name = service_name;
    }

    public void setAttributes(List<Open311AttributePair> attributes) {
        this.attributes = attributes;
    }

    public static class Builder {
        private String jurisdiction_id;
        private String service_code;
        private String service_name;
        private String api_key;
        private Double lat;
        private Double lang;
        private String address_string;
        private String email;
        private String device_id;
        private String account_id;
        private String first_name;
        private String last_name;
        private String phone;
        private String description;
        private String summary;
        private String media_url;
        private byte[] media;

        public Builder setJurisdiction_id(String jurisdiction_id) {
            this.jurisdiction_id = jurisdiction_id;
            return this;
        }

        public Builder setService_code(String service_code) {
            this.service_code = service_code;
            return this;
        }

        public Builder setService_name(String service_name) {
            this.service_name = service_name;
            return this;
        }

        public Builder setApi_key(String api_key) {
            this.api_key = api_key;
            return this;
        }

        public Builder setLat(Double lat) {
            this.lat = lat;
            return this;
        }

        public Builder setLang(Double lang) {
            this.lang = lang;
            return this;
        }

        public Builder setAddress_string(String address_string) {
            this.address_string = address_string;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder setDevice_id(String device_id) {
            this.device_id = device_id;
            return this;
        }

        public Builder setAccount_id(String account_id) {
            this.account_id = account_id;
            return this;
        }

        public Builder setFirst_name(String first_name) {
            this.first_name = first_name;
            return this;
        }

        public Builder setLast_name(String last_name) {
            this.last_name = last_name;
            return this;
        }

        public Builder setPhone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setSummary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder setMedia_url(String media_url) {
            this.media_url = media_url;
            return this;
        }

        public Builder setMedia(byte[] media) {
            this.media = media;
            return this;
        }

        public ServiceRequest createServiceRequest() {
            return new ServiceRequest(jurisdiction_id, service_code, service_name, api_key, lat, lang, address_string, email, device_id, account_id, first_name, last_name, phone, description, summary, media_url, media);
        }

    }
}
