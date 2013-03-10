/*
 * Copyright (C) 2012-2013 Paul Watts (paulcwatts@gmail.com)
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
package com.joulespersecond.oba.elements;


public class ObaRegionElement implements ObaRegion {
    public static final ObaRegionElement[] EMPTY_ARRAY = new ObaRegionElement[] {};

    public static class Bounds implements ObaRegion.Bounds {
        public static final Bounds[] EMPTY_ARRAY = new Bounds[] {};

        private final double lat;
        private final double lon;
        private final double latSpan;
        private final double lonSpan;

        Bounds() {
            lat = 0;
            lon = 0;
            latSpan = 0;
            lonSpan = 0;
        }

        public Bounds(double lat,
                double lon,
                double latSpan,
                double lonSpan) {
            this.lat = lat;
            this.lon = lon;
            this.latSpan = latSpan;
            this.lonSpan = lonSpan;
        }

        @Override
        public double getLat() {
            return lat;
        }

        @Override
        public double getLon() {
            return lon;
        }

        @Override
        public double getLatSpan() {
            return latSpan;
        }

        @Override
        public double getLonSpan() {
            return lonSpan;
        }
    }

    private final long id;
    private final String regionName;
    private final boolean active;
    private final String obaBaseUrl;
    private final String siriBaseUrl;
    private final Bounds[] bounds;
    private final String language;
    private final String contact;
    private final String contactEmail;
    private final boolean supportsObaDiscovery;
    private final boolean supportsObaRealtime;
    private final boolean supportsSiriRealtime;

    ObaRegionElement() {
        id = 0;
        regionName = "";
        obaBaseUrl = null;
        siriBaseUrl = null;
        active = false;
        bounds = Bounds.EMPTY_ARRAY;
        language = "";
        contact = "";
        contactEmail = "";
        supportsObaDiscovery = false;
        supportsObaRealtime = false;
        supportsSiriRealtime = false;
    }

    public ObaRegionElement(long id,
            String name,
            boolean active,
            String obaBaseUrl,
            String siriBaseUrl,
            Bounds[] bounds,
            String lang,
            String contactName,
            String contactEmail,
            boolean supportsObaDiscovery,
            boolean supportsObaRealtime,
            boolean supportsSiriRealtime) {
        this.id = id;
        this.regionName = name;
        this.active = active;
        this.obaBaseUrl = obaBaseUrl;
        this.siriBaseUrl = siriBaseUrl;
        this.bounds = bounds;
        this.language = lang;
        this.contact = contactName;
        this.contactEmail = contactEmail;
        this.supportsObaDiscovery = supportsObaDiscovery;
        this.supportsObaRealtime = supportsObaRealtime;
        this.supportsSiriRealtime = supportsSiriRealtime;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getName() {
        return regionName;
    }

    @Override
    public boolean getActive() {
        return active;
    }

    @Override
    public String getObaBaseUrl() {
        return obaBaseUrl;
    }

    @Override
    public String getSiriBaseUrl() {
        return siriBaseUrl;
    }

    @Override
    public Bounds[] getBounds() {
        return bounds;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public String getContactName() {
        return contact;
    }

    @Override
    public String getContactEmail() {
        return contactEmail;
    }

    @Override
    public boolean getSupportsObaDiscovery() {
        return supportsObaDiscovery;
    }

    @Override
    public boolean getSupportsObaRealtime() {
        return supportsObaRealtime;
    }

    @Override
    public boolean getSupportsSiriRealtime() {
        return supportsSiriRealtime;
    }
}
