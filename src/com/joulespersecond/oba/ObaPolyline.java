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
package com.joulespersecond.oba;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.android.maps.GeoPoint;
import com.google.gson.reflect.TypeToken;

public final class ObaPolyline {
    public static final ObaPolyline EMPTY_OBJECT = new ObaPolyline();
    public static final ObaArray<ObaPolyline> EMPTY_ARRAY = new ObaArray<ObaPolyline>();
    public static final Type ARRAY_TYPE = new TypeToken<ObaArray<ObaPolyline>>(){}.getType();

    private final String points;
    private final int length;
    private final String levels;

    /**
     * Constructor.
     */
    ObaPolyline() {
        points = "";
        length = 0;
        levels = "";
    }

    /**
     * Returns the number of points in the line.
     *
     * @return The number of points in the line.
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns the levels to display this line.
     *
     * @return The levels to display this line, or the empty string.
     */
    public String getRawLevels() {
        return levels;
    }

    /**
     * Returns the levels on which to display this line.
     *
     * @return The decoded levels to display line.
     */
    public List<Integer> getLevels() {
        return decodeLevels(levels, length);
    }

    /**
     * Returns the list of points in this line.
     *
     * @return The list of points in this line.
     */
    public List<GeoPoint> getPoints() {
        return decodeLine(points, length);
    }

    /**
     * Returns the string encoding of the points in this line.
     *
     * @return The string encoding of the points in this line.
     */
    public String getRawPoints() {
        return points;
    }

    /**
     * Decodes an encoded polyline into a list of points.
     * Adapted from http://georgelantz.com/files/polyline_decoder.rb
     * For the exact algorithm:
     * http://code.google.com/apis/maps/documentation/polylinealgorithm.html
     *
     * @param encoded The encoded string.
     * @param numPoints The number of points. This is purely used as a hint
     *      to allocate memory; the function will always return the number
     *      of points that are contained in the encoded string.
     * @return A list of points from the encoded string.
     */
    public static List<GeoPoint> decodeLine(String encoded, int numPoints) {
        assert(numPoints >= 0);
        ArrayList<GeoPoint> array = new ArrayList<GeoPoint>(numPoints);

        final int len = encoded.length();
        int i = 0;
        int lat = 0, lon = 0;

        while (i < len) {
            int shift = 0;
            int result = 0;

            int a,b;
            do {
                a = encoded.charAt(i);
                b = a - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
                ++i;
            } while (b >= 0x20);

            final int dlat = ((result & 1) == 1 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                a = encoded.charAt(i);
                b = a - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
                ++i;
            } while (b >= 0x20);

            final int dlon = ((result & 1) == 1 ? ~(result >> 1) : (result >> 1));
            lon += dlon;

            // The polyline encodes in degrees * 1E5, we need degrees * 1E6
            array.add(new GeoPoint(lat*10, lon*10));
        }

        return array;
    }

    /**
     * Decodes encoded levels according to:
     * http://code.google.com/apis/maps/documentation/polylinealgorithm.html
     *
     * @param encoded The encoded string.
     * @param numPoints The number of points. This is purely used as a hint
     *      to allocate memory; the function will always return the number
     *      of points that are contained in the encoded string.
     * @return A list of levels from the encoded string.
     */
    public static List<Integer> decodeLevels(String encoded, int numPoints) {
        assert(numPoints >= 0);
        ArrayList<Integer> array = new ArrayList<Integer>(numPoints);

        final int len = encoded.length();
        int i = 0;
        while (i < len) {
            int shift = 0;
            int result = 0;

            int a,b;
            do {
                a = encoded.charAt(i);
                b = a - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
                ++i;
            } while (b >= 0x20);

            array.add(result);
        }

        return array;
    }
}
