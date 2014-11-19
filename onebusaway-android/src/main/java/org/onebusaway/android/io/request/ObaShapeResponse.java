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

import org.onebusaway.android.io.elements.ObaShape;
import org.onebusaway.android.io.elements.ObaShapeElement;

import android.location.Location;

import java.util.List;

/**
 * Response object for ObaShapeRequest requests.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaShapeResponse extends ObaResponse implements ObaShape {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        //private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;
        private final ObaShapeElement entry = ObaShapeElement.EMPTY_OBJECT;
    }

    private final Data data;

    private ObaShapeResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public int getLength() {
        return data.entry.getLength();
    }

    @Override
    public List<Integer> getLevels() {
        return data.entry.getLevels();
    }

    @Override
    public List<Location> getPoints() {
        return data.entry.getPoints();
    }

    @Override
    public String getRawLevels() {
        return data.entry.getRawLevels();
    }

    @Override
    public String getRawPoints() {
        return data.entry.getRawPoints();
    }
}
