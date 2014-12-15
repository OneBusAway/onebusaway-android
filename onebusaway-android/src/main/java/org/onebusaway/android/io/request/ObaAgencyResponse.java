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

import org.onebusaway.android.io.elements.ObaAgency;
import org.onebusaway.android.io.elements.ObaAgencyElement;

/**
 * Response for an ObaAgencyRequest request.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaAgencyResponse extends ObaResponse implements ObaAgency {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaAgencyElement entry = ObaAgencyElement.EMPTY_OBJECT;
    }

    private final Data data;

    private ObaAgencyResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getId() {
        return data.entry.getId();
    }

    @Override
    public String getName() {
        return data.entry.getName();
    }

    @Override
    public String getUrl() {
        return data.entry.getUrl();
    }

    @Override
    public String getTimezone() {
        return data.entry.getTimezone();
    }

    @Override
    public String getLang() {
        return data.entry.getLang();
    }

    @Override
    public String getPhone() {
        return data.entry.getPhone();
    }

    @Override
    public String getDisclaimer() {
        return data.entry.getDisclaimer();
    }

    @Override
    public String getEmail() {
        return data.entry.getEmail();
    }
}
