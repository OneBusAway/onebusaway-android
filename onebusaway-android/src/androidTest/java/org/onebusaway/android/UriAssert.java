/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android;

import junit.framework.Assert;

import org.onebusaway.android.io.request.RequestBase;

import android.net.Uri;

import java.lang.reflect.Field;
import java.util.Map;

public class UriAssert extends Assert {

    /**
     * Check request for matching Uri.
     *
     * @param expectedUri   The Uri the test should expect (query values are ignored, use
     *                      expectedQuery)
     * @param expectedQuery A Map of query key/values required to be in the Uri.
     *                      Use asterisk to require key, but ignore value. Order is irrelevant.
     *                      The list is not exhaustive, extra key/values are ignored.
     */
    public static void assertUriMatch(Uri expectedUri, Map<String, String> expectedQuery,
            Uri actualUri) {
        assertEquals(expectedUri.getHost(), actualUri.getHost());
        assertEquals(expectedUri.getScheme(), actualUri.getScheme());
        assertEquals(expectedUri.getPath(), actualUri.getPath());
        if (expectedQuery != null) {
            for (Map.Entry<String, String> entry : expectedQuery.entrySet()) {
                String expectedValue = entry.getValue();
                String actualValue = actualUri.getQueryParameter(entry.getKey());
                if ("*".equals(expectedValue)) {
                    assertNotNull("URI missing key \"" + entry.getKey() + "\"", actualValue);
                } else {
                    assertEquals(
                            "URI mismatch on query key \"" + entry.getKey() + "\"",
                            expectedValue,
                            actualValue);
                }
            }
        }
    }

    /**
     * Check request for matching Uri.
     *
     * @param expectedUri   The Uri the test should expect (query values are ignored, use
     *                      expectedQuery)
     * @param expectedQuery A Map of query key/values required to be in the Uri.
     *                      Use asterisk to require key, but ignore value. Order is irrelevant.
     *                      The list is not exhaustive, extra key/values are ignored.
     */
    public static void assertUriMatch(Uri expectedUri, Map<String, String> expectedQuery,
            RequestBase actualRequest) {
        Uri actualUri;
        try {
            actualUri = getUriFromRequest(actualRequest);
        } catch (Exception e) {
            fail("Exception thrown reflecting on RequestBase: " + e.getMessage());
            return;
        }
        assertUriMatch(expectedUri, expectedQuery, actualUri);
    }

    /**
     * Check request for matching Uri.
     *
     * @param expectedUri The Uri the test should expect (query values are ignored, use
     *                    expectedQuery)
     */
    public static void assertUriMatch(Uri expectedUri, RequestBase actualRequest) {
        assertUriMatch(expectedUri, null, actualRequest);
    }

    /**
     * Check request for matching Uri.
     *
     * @param expectedUriString The Uri the test should expect (query values are ignored, use
     *                    expectedQuery)
     */
    public static void assertUriMatch(String expectedUriString, RequestBase actualRequest) {
        assertUriMatch(Uri.parse(expectedUriString), null, actualRequest);
    }

    /**
     * Check request for matching Uri.
     *
     * @param expectedUriString   The Uri the test should expect (query values are ignored, use
     *                      expectedQuery)
     * @param expectedQuery A Map of query key/values required to be in the Uri.
     *                      Use asterisk to require key, but ignore value. Order is irrelevant.
     *                      The list is not exhaustive, extra key/values are ignored.
     */
    public static void assertUriMatch(String expectedUriString, Map<String, String> expectedQuery,
            RequestBase actualRequest) {
        assertUriMatch(Uri.parse(expectedUriString), expectedQuery, actualRequest);
    }

    // read private field of request (if name changes, we'll throw)
    protected static Uri getUriFromRequest(RequestBase actualRequest)
            throws NoSuchFieldException, IllegalAccessException {
        Field uriField = RequestBase.class.getDeclaredField("mUri");
        uriField.setAccessible(true);
        Uri actualUri = (Uri) uriField.get(actualRequest);
        return actualUri;
    }

}
