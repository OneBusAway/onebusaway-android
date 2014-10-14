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
package org.onebusaway.android.io.request;

import org.onebusaway.android.io.ObaApi;

/**
 * Base class for response objects.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public class ObaResponse {

    private final String version;

    private final int code;

    private final long currentTime;

    private final String text;

    protected ObaResponse() {
        version = ObaApi.VERSION1;
        code = 0;
        currentTime = 0;
        text = "ERROR";
    }

    /**
     * @return The version of this response.
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return The status code (one of the ObaApi.OBA_ constants)
     */
    public int getCode() {
        return code;
    }

    /**
     * @return The status text.
     */

    public String getText() {
        return text;
    }

    /**
     * @return The current system time on the API server
     * as milliseconds since the epoch.
     */
    public long getCurrentTime() {
        return currentTime;
    }
}
