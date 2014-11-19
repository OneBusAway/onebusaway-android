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
package org.onebusaway.android.io.elements;

import android.location.Location;

import java.util.List;

public interface ObaShape {

    /**
     * Returns the number of points in the line.
     *
     * @return The number of points in the line.
     */
    public int getLength();

    /**
     * Returns the levels to display this line.
     *
     * @return The levels to display this line, or the empty string.
     */
    public String getRawLevels();

    /**
     * Returns the levels on which to display this line.
     *
     * @return The decoded levels to display line.
     */
    public List<Integer> getLevels();

    /**
     * Returns the list of points in this line.
     *
     * @return The list of points in this line.
     */
    public List<Location> getPoints();

    /**
     * Returns the string encoding of the points in this line.
     *
     * @return The string encoding of the points in this line.
     */
    public String getRawPoints();
}
