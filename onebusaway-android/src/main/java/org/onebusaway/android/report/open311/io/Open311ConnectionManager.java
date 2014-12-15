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

package org.onebusaway.android.report.open311.io;

import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.report.open311.constants.Open311Constants;
import org.onebusaway.android.report.open311.utils.Open311UrlUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Manager that manages all http connections
 * while communicating any open311 server.
 *
 * @author Cagri Cetin
 */
public class Open311ConnectionManager {

    private static CookieStore cookieStore = new BasicCookieStore();
    private DefaultHttpClient httpClient = null;

    public Open311ConnectionManager() {
        BasicHttpParams params = new BasicHttpParams();
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        SSLSessionCache sslSession = new SSLSessionCache(Application.get().getApplicationContext());
        schemeRegistry.register(new Scheme("https", SSLCertificateSocketFactory.getHttpSocketFactory(10 * 60 * 1000, sslSession), 443));
        ClientConnectionManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
        HttpConnectionParams.setConnectionTimeout(params, Open311Constants.WS_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, Open311Constants.WS_TIMEOUT);

        httpClient = new DefaultHttpClient();

        httpClient.getParams().setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF-8");
        httpClient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, Open311Constants.WS_TIMEOUT);
        httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, Open311Constants.WS_TIMEOUT);
    }

    /**
     * Makes connection to any server.
     *
     * @param url           Destination url
     * @param requestMethod request method Post or Get
     * @param httpEntity    http entity contains parameters for the request
     * @return Returns the result of the request as string
     */
    public String getStringResult(String url, Open311UrlUtil.RequestMethod requestMethod, HttpEntity httpEntity) {

        HttpResponse response;
        String result = null;
        try {

            if (requestMethod == Open311UrlUtil.RequestMethod.POST) {
                response = postMethod(url, httpEntity);
            } else {
                response = getMethod(url, httpEntity);
            }

            HttpEntity resEntity = response.getEntity();

            if (resEntity != null) {
                result = EntityUtils.toString(resEntity);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Creates requests without any parameters.
     *
     * @param url
     * @param requestMethod
     * @return
     */
    public String getStringResult(String url, Open311UrlUtil.RequestMethod requestMethod) {
        return getStringResult(url, requestMethod, null);
    }

    /**
     * Internal method for post method
     *
     * @param url
     * @param httpEntity
     * @return
     * @throws org.apache.http.client.ClientProtocolException
     * @throws java.io.IOException
     */
    private HttpResponse postMethod(String url, HttpEntity httpEntity) throws ClientProtocolException, IOException {

        HttpPost httppost = new HttpPost(url);
        httppost.setHeader("Content-Type", "multipart/form-data;charset=UTF-8");
        if (httpEntity != null) {
            httppost.setEntity(httpEntity);
        }
//        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
//        httpEntity.writeTo(bytes);
//        String content = bytes.toString();
        return httpClient.execute(httppost);
    }

    /**
     * Internal method for get methods.
     *
     * @param url
     * @param httpEntity
     * @return
     * @throws org.apache.http.client.ClientProtocolException
     * @throws java.io.IOException
     */
    private HttpResponse getMethod(String url, HttpEntity httpEntity) throws ClientProtocolException, IOException {

        if (httpEntity != null) {
            url += Open311UrlUtil.nameValuePairsToParams(httpEntity);
        }

        HttpGet httpGet = new HttpGet(url);
        HttpContext ctx = new BasicHttpContext();
        return httpClient.execute(httpGet, ctx);
    }
}
