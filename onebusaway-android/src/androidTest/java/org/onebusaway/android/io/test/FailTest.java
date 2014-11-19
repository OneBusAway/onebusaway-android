/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.io.test;

import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.io.request.ObaStopRequest;
import org.onebusaway.android.io.request.ObaStopResponse;
import org.onebusaway.android.io.request.RequestBase;

import android.content.Context;
import android.net.Uri;

import java.util.concurrent.Callable;

public class FailTest extends ObaTestCase {

    public void test404_1() {
        ObaStopResponse response = ObaStopRequest.newRequest(getContext(), "404test").call();
        assertNotNull(response);
        // Right now this is what is in the test response...
        assertEquals(ObaApi.OBA_INTERNAL_ERROR, response.getCode());
    }

    // This is a real 404
    public void test404_2() {
        BadResponse response =
                new BadRequest.Builder(getContext(), "/foo/1_29261.json").build().call();
        assertNotNull(response);
        assertEquals(ObaApi.OBA_NOT_FOUND, response.getCode());
    }

    public void testBadJson() {
        BadResponse response =
                new BadRequest.Builder(getContext(), "/stop/1_29261.xml").build().call();
        assertNotNull(response);
        assertEquals(ObaApi.OBA_INTERNAL_ERROR, response.getCode());
    }

    //
    // We can create our own test request that points to an invalid path.
    //
    private static class BadResponse extends ObaResponse {

    }

    private static class BadRequest extends RequestBase
            implements Callable<BadResponse> {

        protected BadRequest(Uri uri) {
            super(uri);
        }

        @Override
        public BadResponse call() {
            return call(BadResponse.class);
        }

        static class Builder extends RequestBase.BuilderBase {

            public Builder(Context context, String path) {
                super(context, BASE_PATH + path);
            }

            public BadRequest build() {
                return new BadRequest(buildUri());
            }
        }
    }

}
