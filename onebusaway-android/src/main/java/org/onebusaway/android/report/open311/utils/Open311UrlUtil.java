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


import android.content.Context;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.onebusaway.android.report.open311.models.Open311AttributePair;
import org.onebusaway.android.report.open311.models.ServiceListRequest;
import org.onebusaway.android.report.open311.models.ServiceRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for preparing destination urls
 * Helper class for preparing request parameters
 * @author Cagri Cetin
 */

public class Open311UrlUtil {

    public enum RequestMethod {
        GET, POST;
    }

    /**
     * creates service url
     *
     * @param baseUrl
     * @param format  json/xml/html
     * @return url as string
     */
    public static String getServiceUrl(String baseUrl, String format) {
        return new StringBuilder(baseUrl).append("services").append(".").append(format).toString();
    }

    /**
     * crates service description url
     *
     * @param baseUrl
     * @param serviceCode
     * @param format      json/xml/html
     * @return url as string
     */
    public static String getServiceDescUrl(String baseUrl, String serviceCode, String format) {
        return new StringBuilder(baseUrl).append("services/").append(serviceCode).append(".").append(format).toString();
    }

    /**
     * creates service request url
     *
     * @param baseUrl
     * @param format  json/xml/html
     * @return url as string
     */
    public static String getServiceRequestUrl(String baseUrl, String format) {
        return new StringBuilder(baseUrl).append("requests").append(".").append(format).toString();
    }

    /**
     * Converts name value pairs to http get parameters
     *
     * @param httpEntity http Entity containing name value pairs
     * @return Apendable string for http get url
     * @throws java.io.IOException
     */
    public static String nameValuePairsToParams(HttpEntity httpEntity) throws IOException {

        List<NameValuePair> valuePairs = URLEncodedUtils.parse(httpEntity);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < valuePairs.size(); i++) {
            if (i == 0) {
                sb.append("?");
            } else {
                sb.append("&");
            }
            sb.append(valuePairs.get(i).getName()).append("=").append(valuePairs.get(i).getValue());
        }
        return sb.toString();
    }

    /**
     * Prepares encoded entity with name value pairs from service request
     *
     * @param serviceRequest uses service request as a source
     * @return UrlEncodedFormEntity object
     * @throws java.io.UnsupportedEncodingException
     */
    public static UrlEncodedFormEntity prepareUrlEncodedEntity(ServiceRequest serviceRequest) throws UnsupportedEncodingException {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("api_key", serviceRequest.getApi_key()));
        nameValuePairs.add(new BasicNameValuePair("service_code", serviceRequest.getService_code()));
        nameValuePairs.add(new BasicNameValuePair("service_name", serviceRequest.getService_name()));

        if (serviceRequest.getLat() != null && serviceRequest.getLang() != null) {
            nameValuePairs.add(new BasicNameValuePair("lat", serviceRequest.getLat().toString()));
            nameValuePairs.add(new BasicNameValuePair("long", serviceRequest.getLang().toString()));
        } else {
            nameValuePairs.add(new BasicNameValuePair("jurisdiction_id", serviceRequest.getJurisdiction_id()));
        }
        if (serviceRequest.getAddress_string() != null) {
            nameValuePairs.add(new BasicNameValuePair("address_string", serviceRequest.getAddress_string()));
        }
        nameValuePairs.add(new BasicNameValuePair("description", serviceRequest.getDescription()));
        if (serviceRequest.getMedia_url() != null)
            nameValuePairs.add(new BasicNameValuePair("media_url", serviceRequest.getMedia_url()));

        if (serviceRequest.getFirst_name() != null) {
            //Personal info
            nameValuePairs.add(new BasicNameValuePair("first_name", serviceRequest.getFirst_name()));
            nameValuePairs.add(new BasicNameValuePair("last_name", serviceRequest.getLast_name()));
        }

        if (serviceRequest.getPhone() != null && "".equals(serviceRequest.getPhone()) == false)
            nameValuePairs.add(new BasicNameValuePair("phone", serviceRequest.getPhone()));

        nameValuePairs.add(new BasicNameValuePair("email", serviceRequest.getEmail()));

        if (serviceRequest.getDevice_id() != null)
            nameValuePairs.add(new BasicNameValuePair("device_id", serviceRequest.getDevice_id()));

        if (serviceRequest.getAttributes() != null) {
            for (Open311AttributePair open311AttributePair : serviceRequest.getAttributes()) {
                if ("".equals(open311AttributePair.getValue()) == false) {
                    nameValuePairs.add(new BasicNameValuePair("attribute[" + open311AttributePair.getCode() + "]"
                            , open311AttributePair.getValue()));
                }
            }
        }

        return new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8);
    }

    /**
     * Creates multi part entity for service request
     * This method useful for sending images, and media files
     *
     * @param serviceRequest
     * @param context
     * @return
     * @throws java.io.UnsupportedEncodingException
     */
    public static MultipartEntity prepareMultipartEntity(ServiceRequest serviceRequest, Context context) throws UnsupportedEncodingException {
        MultipartEntity multipartEntity = new MultipartEntity();
        multipartEntity.addPart("api_key", new StringBody(serviceRequest.getApi_key()));
        multipartEntity.addPart("service_code", new StringBody(serviceRequest.getService_code()));
        multipartEntity.addPart("service_name", new StringBody(serviceRequest.getService_name()));

        if (serviceRequest.getLat() != null && serviceRequest.getLang() != null) {
            multipartEntity.addPart("lat", new StringBody(serviceRequest.getLat().toString()));
            multipartEntity.addPart("long", new StringBody(serviceRequest.getLang().toString()));
        } else {
            multipartEntity.addPart("jurisdiction_id", new StringBody(serviceRequest.getJurisdiction_id()));
        }

        if (serviceRequest.getAddress_string() != null) {
            multipartEntity.addPart("address_string", new StringBody(serviceRequest.getAddress_string()));
        }

        multipartEntity.addPart("description", new StringBody(serviceRequest.getDescription()));
//        if (serviceRequest.getMedia_url() != null)
//            multipartEntity.addPart("media_url", new StringBody(serviceRequest.getMedia_url()));

        if (serviceRequest.getFirst_name() != null) {
            //Personal info
            multipartEntity.addPart("first_name", new StringBody(serviceRequest.getFirst_name()));
            multipartEntity.addPart("last_name", new StringBody(serviceRequest.getLast_name()));
        }
        if (serviceRequest.getPhone() != null && "".equals(serviceRequest.getPhone()) == false)
            multipartEntity.addPart("phone", new StringBody(serviceRequest.getPhone()));

        multipartEntity.addPart("email", new StringBody(serviceRequest.getEmail()));

        if (serviceRequest.getDevice_id() != null)
            multipartEntity.addPart("device_id", new StringBody(serviceRequest.getDevice_id()));

        multipartEntity.addPart("media", new ByteArrayBody(serviceRequest.getMedia(), "image.png"));

        if (serviceRequest.getAttributes() != null) {
            for (Open311AttributePair open311AttributePair : serviceRequest.getAttributes()) {
                if ("".equals(open311AttributePair.getValue()) == false) {
                    multipartEntity.addPart("attribute[" + open311AttributePair.getCode() + "]", new StringBody(open311AttributePair.getValue()));
                }
            }
        }

        return multipartEntity;
    }


    /**
     * Creates http entity for ServiceListRequest
     *
     * @param serviceListRequest
     * @return
     * @throws java.io.UnsupportedEncodingException
     */
    public static UrlEncodedFormEntity prepareUrlEncodedEntity(ServiceListRequest serviceListRequest) throws UnsupportedEncodingException {
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        if (serviceListRequest.getLat() != null && serviceListRequest.getLang() != null) {
            nameValuePairs.add(new BasicNameValuePair("lat", serviceListRequest.getLat().toString()));
            nameValuePairs.add(new BasicNameValuePair("long", serviceListRequest.getLang().toString()));
        } else {
            nameValuePairs.add(new BasicNameValuePair("jurisdiction_id", serviceListRequest.getJurisdictionId()));
        }

        return new UrlEncodedFormEntity(nameValuePairs, HTTP.UTF_8);
    }

}
