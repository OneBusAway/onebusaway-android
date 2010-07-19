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
package com.joulespersecond.oba.request.test;

import java.io.IOException;

import com.joulespersecond.oba.request.ObaAgencyRequest;
import com.joulespersecond.oba.request.ObaAgencyResponse;


public class AgencyRequestTest extends ObaTestCase {
    public void testKCMAgency() throws IOException {
        ObaAgencyRequest.Builder builder = new ObaAgencyRequest.Builder(getContext(), "1");
        ObaAgencyRequest request = builder.build();
        ObaAgencyResponse response = request.call();
        assertOK(response);

        assertEquals(response.getId(), "1");
        assertEquals(response.getName(), "Metro Transit");
    }
}
