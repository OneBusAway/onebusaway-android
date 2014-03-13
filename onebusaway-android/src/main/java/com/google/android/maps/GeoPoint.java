/*
 * Copyright (C) 2009 James Ancona
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
package com.google.android.maps;

/**
 * @author Jim Ancona
 */
public class GeoPoint {
    private int latitudeE6;
    private int longitudeE6;

    public GeoPoint(int latitudeE6, int longitudeE6) {
        this.latitudeE6 = latitudeE6;
        this.longitudeE6 = longitudeE6;
    }
    GeoPoint(double latitude, double longitude) {
        this((int)(latitude * 1e6), (int)(longitude * 1e6));
    }

    public int getLatitudeE6() {
        return latitudeE6;
    }

    public int getLongitudeE6() {
        return longitudeE6;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return "GeoPoint: Latitude: " + latitudeE6 + ", Longitude: " + longitudeE6;
    }
}