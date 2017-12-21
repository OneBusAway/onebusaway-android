/*
 * Copyright (C) 2012-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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

import java.util.Arrays;

public class ObaRegionElement implements ObaRegion {

    public static final ObaRegionElement[] EMPTY_ARRAY = new ObaRegionElement[]{};

    public static class Bounds implements ObaRegion.Bounds {

        public static final Bounds[] EMPTY_ARRAY = new Bounds[]{};

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

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append("lat=").append(lat).append(",");
            sb.append("lon=").append(lon).append(",");
            sb.append("latSpan=").append(latSpan).append(",");
            sb.append("lonSpan=").append(lonSpan);
            sb.append("]");
            return sb.toString();
        }
    }

    public static class Open311Server implements ObaRegion.Open311Server {

        public static final Open311Server[] EMPTY_ARRAY = new Open311Server[]{};

        private final String jurisdictionId;

        private final String apiKey;

        private final String baseUrl;

        public Open311Server() {
            jurisdictionId = "";
            apiKey = "";
            baseUrl = "";
        }

        public Open311Server(String jurisdictionId, String apiKey, String baseUrl) {

            this.jurisdictionId = jurisdictionId;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
        }

        @Override
        public String getJuridisctionId() {
            return jurisdictionId;
        }

        @Override
        public String getApiKey() {
            return apiKey;
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            sb.append("jurisdictionId=").append(jurisdictionId).append(",");
            sb.append("apiKey=").append(apiKey).append(",");
            sb.append("baseUrl=").append(baseUrl);
            sb.append("]");
            return sb.toString();
        }
    }

    private final long id;

    private final String regionName;

    private final boolean active;

    private final String obaBaseUrl;

    private final String siriBaseUrl;

    private final Bounds[] bounds;

    private final Open311Server[] open311Servers;

    private final String language;

    private final String contactEmail;

    private final boolean supportsObaDiscoveryApis;

    private final boolean supportsObaRealtimeApis;

    private final boolean supportsSiriRealtimeApis;

    private final String twitterUrl;

    private final boolean experimental;

    private final String stopInfoUrl;

    private final String otpBaseUrl;

    private final String otpContactEmail;

    private final boolean supportsOtpBikeshare;

    private final boolean supportsEmbeddedSocial;

    ObaRegionElement() {
        id = 0;
        regionName = "";
        obaBaseUrl = null;
        siriBaseUrl = null;
        active = false;
        bounds = Bounds.EMPTY_ARRAY;
        open311Servers = Open311Server.EMPTY_ARRAY;
        language = "";
        contactEmail = "";
        supportsObaDiscoveryApis = false;
        supportsObaRealtimeApis = false;
        supportsSiriRealtimeApis = false;
        twitterUrl = "";
        experimental = true;
        stopInfoUrl = "";
        otpBaseUrl = "";
        otpContactEmail = "";
        supportsOtpBikeshare = false;
        supportsEmbeddedSocial = false;
    }

    public ObaRegionElement(long id,
                            String name,
                            boolean active,
                            String obaBaseUrl,
                            String siriBaseUrl,
                            Bounds[] bounds,
                            Open311Server[] open311Servers,
                            String lang,
                            String contactEmail,
                            boolean supportsObaDiscoveryApis,
                            boolean supportsObaRealtimeApis,
                            boolean supportsSiriRealtimeApis,
                            String twitterUrl,
                            boolean experimental,
                            String stopInfoUrl,
                            String otpBaseUrl,
                            String otpContactEmail,
                            boolean supportsOtpBikeshare,
                            boolean supportsEmbeddedSocial) {
        this.id = id;
        this.regionName = name;
        this.active = active;
        this.obaBaseUrl = obaBaseUrl;
        this.siriBaseUrl = siriBaseUrl;
        this.bounds = bounds;
        this.open311Servers = open311Servers;
        this.language = lang;
        this.contactEmail = contactEmail;
        this.supportsObaDiscoveryApis = supportsObaDiscoveryApis;
        this.supportsObaRealtimeApis = supportsObaRealtimeApis;
        this.supportsSiriRealtimeApis = supportsSiriRealtimeApis;
        this.twitterUrl = twitterUrl;
        this.experimental = experimental;
        this.stopInfoUrl = stopInfoUrl;
        this.otpBaseUrl = otpBaseUrl;
        this.otpContactEmail = otpContactEmail;
        this.supportsOtpBikeshare = supportsOtpBikeshare;
        this.supportsEmbeddedSocial = supportsEmbeddedSocial;
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
    public Open311Server[] getOpen311Servers() {
        return open311Servers;
    }

    @Override
    public String getLanguage() {
        return language;
    }

    @Override
    public String getContactEmail() {
        return contactEmail;
    }

    @Override
    public boolean getSupportsObaDiscoveryApis() {
        return supportsObaDiscoveryApis;
    }

    @Override
    public boolean getSupportsObaRealtimeApis() {
        return supportsObaRealtimeApis;
    }

    @Override
    public boolean getSupportsSiriRealtimeApis() {
        return supportsSiriRealtimeApis;
    }

    @Override
    public String getTwitterUrl() {
        return twitterUrl;
    }

    @Override
    public boolean getExperimental() {
        return experimental;
    }

    @Override
    public String getStopInfoUrl() {
        return stopInfoUrl;
    }

    @Override
    public String getOtpBaseUrl() {
        return otpBaseUrl;
    }

    @Override
    public String getOtpContactEmail() {
        return otpContactEmail;
    }

    @Override
    public boolean getSupportsOtpBikeshare() {
        return supportsOtpBikeshare;
    }

    @Override
    public boolean getSupportsEmbeddedSocial() {
        return supportsEmbeddedSocial;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == 0) ? 0 : Long.valueOf(id).hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ObaRegionElement)) {
            return false;
        }
        ObaRegionElement other = (ObaRegionElement) obj;
        if (id == 0) {
            if (other.getId() != 0) {
                return false;
            }
        } else if (id != other.getId()) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ObaRegionElement{" +
                "id=" + id +
                ", regionName='" + regionName + '\'' +
                ", active=" + active +
                ", obaBaseUrl='" + obaBaseUrl + '\'' +
                ", siriBaseUrl='" + siriBaseUrl + '\'' +
                ", bounds=" + Arrays.toString(bounds) +
                ", open311Servers=" + Arrays.toString(open311Servers) +
                ", language='" + language + '\'' +
                ", contactEmail='" + contactEmail + '\'' +
                ", supportsObaDiscoveryApis=" + supportsObaDiscoveryApis +
                ", supportsObaRealtimeApis=" + supportsObaRealtimeApis +
                ", supportsSiriRealtimeApis=" + supportsSiriRealtimeApis +
                ", twitterUrl='" + twitterUrl + '\'' +
                ", experimental=" + experimental +
                ", stopInfoUrl='" + stopInfoUrl + '\'' +
                ", otpBaseUrl='" + otpBaseUrl + '\'' +
                ", otpContactEmail='" + otpContactEmail + '\'' +
                ", supportsOtpBikeshare='" + supportsOtpBikeshare + '\'' +
                ", supportsEmbeddedSocial=" + supportsEmbeddedSocial + '\'' +
                '}';
    }
}
