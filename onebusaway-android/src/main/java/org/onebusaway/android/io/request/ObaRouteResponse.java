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
import org.onebusaway.android.io.elements.ObaReferences;
import org.onebusaway.android.io.elements.ObaReferencesElement;
import org.onebusaway.android.io.elements.ObaRoute;
import org.onebusaway.android.io.elements.ObaRouteElement;

/**
 * Response object for ObaRouteRequest objects.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaRouteResponse extends ObaResponseWithRefs implements ObaRoute {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;

        private final ObaRouteElement entry = ObaRouteElement.EMPTY_OBJECT;
    }

    private final Data data;

    private ObaRouteResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getId() {
        return data.entry.getId();
    }

    @Override
    public String getShortName() {
        return data.entry.getShortName();
    }

    @Override
    public String getLongName() {
        return data.entry.getLongName();
    }

    @Override
    public String getDescription() {
        return data.entry.getDescription();
    }

    @Override
    public int getType() {
        return data.entry.getType();
    }

    @Override
    public String getUrl() {
        return data.entry.getUrl();
    }

    @Override
    public Integer getColor() {
        return data.entry.getColor();
    }

    @Override
    public Integer getTextColor() {
        return data.entry.getTextColor();
    }

    @Override
    public String getAgencyId() {
        return data.entry.getAgencyId();
    }

    /**
     * @return The agency object.
     */
    public ObaAgency getAgency() {
        return data.references.getAgency(data.entry.getAgencyId());
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
