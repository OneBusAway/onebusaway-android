/*
 * Copyright (C) 2016 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.util;

/**
 * Utilities for input and output
 */

public class IOUtils {

    /**
     * Encodes the ID to ensure it's safe for local storage and submission as API request
     * parameter.
     * See #704.
     *
     * @param id the element ID
     * @return an encoded version of that element ID
     */
    public static String encodeId(String id) {
        return id.replace("/", "%2F");
    }
}
