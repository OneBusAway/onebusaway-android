/*
 * Copyright (C) 2013 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.request;

import android.content.Context;
import android.net.Uri;

import java.util.concurrent.Callable;

public final class ObaRegionsRequest extends RequestBase implements
        Callable<ObaRegionsResponse> {
    protected ObaRegionsRequest(Uri uri) {
        super(uri);
    }

    //
    // This currently has a very simple builder because you can't do much with this "API"
    //
    public static class Builder {
        private static final Uri URI = Uri.parse("http://regions.onebusaway.org/regions.json");

        public Builder(Context context) {
            //super(context);
        }

        public ObaRegionsRequest build() {
            return new ObaRegionsRequest(URI);
        }
    }

    /**
     * Helper method for constructing new instances.
     * @param context The package context.
     * @return The new request instance.
     */
    public static ObaRegionsRequest newRequest(Context context) {
        return new Builder(context).build();
    }

    @Override
    public ObaRegionsResponse call() {
        return call(ObaRegionsResponse.class);
    }

    @Override
    public String toString() {
        return "ObaRegionsRequest [mUri=" + mUri + "]";
    }
}
