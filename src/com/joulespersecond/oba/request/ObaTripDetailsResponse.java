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
package com.joulespersecond.oba.request;

import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaReferencesElement;
import com.joulespersecond.oba.elements.ObaTripDetails;
import com.joulespersecond.oba.elements.ObaTripDetailsElement;
import com.joulespersecond.oba.elements.ObaTripSchedule;
import com.joulespersecond.oba.elements.ObaTripStatus;

/**
 * Response object for ObaStopRequest requests.
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaTripDetailsResponse extends ObaResponseWithRefs
    implements ObaTripDetails {
    private static final class Data {
        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;
        private final ObaTripDetailsElement entry = ObaTripDetailsElement.EMPTY_OBJECT;
    }
    private final Data data;

    private ObaTripDetailsResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getId() {
        return data.entry.getId();
    }

    @Override
    public ObaTripSchedule getSchedule() {
        return data.entry.getSchedule();
    }

    @Override
    public ObaTripStatus getStatus() {
        return data.entry.getStatus();
    }

    @Override
    protected ObaReferences getRefs() {
        return data.references;
    }
}
