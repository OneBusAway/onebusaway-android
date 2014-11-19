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
package org.onebusaway.android.io.request;

import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.io.elements.ObaRegionElement;

public class ObaRegionsResponse extends ObaResponse {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaRegionElement[] list = ObaRegionElement.EMPTY_ARRAY;

    }

    private final Data data;

    private ObaRegionsResponse() {
        data = Data.EMPTY_OBJECT;
    }

    public ObaRegion[] getRegions() {
        return data.list;
    }
}
