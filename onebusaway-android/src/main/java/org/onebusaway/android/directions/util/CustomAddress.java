/*
 * Copyright 2012 University of South Florida
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.directions.util;

import android.location.Address;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.Locale;


/**
 * Created by foucelhas on 18/08/14.
 */
public class CustomAddress extends Address {

    // Seems that subThoroughFare is street number and thoroughfare is street name.

    private static final int ADDRESS_MAX_LINES_TO_SHOW = 5;

    public CustomAddress(Locale locale) {
        super(locale);
    }

    public CustomAddress() {
        super(Locale.getDefault());
    }

    public CustomAddress(Address address) {
        super(address.getLocale());
        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
            super.setAddressLine(i, address.getAddressLine(i));
        }
        super.setFeatureName(address.getFeatureName());
        super.setAdminArea(address.getAdminArea());
        super.setSubAdminArea(address.getSubAdminArea());
        super.setLocality(address.getLocality());
        super.setSubLocality(address.getSubLocality());
        super.setThoroughfare(address.getThoroughfare());
        super.setSubThoroughfare(address.getSubThoroughfare());
        super.setPostalCode(address.getPostalCode());
        super.setCountryCode(address.getCountryCode());
        super.setCountryName(address.getCountryName());
        super.setLatitude(address.getLatitude());
        super.setLongitude(address.getLongitude());
        super.setPhone(address.getPhone());
        super.setUrl(address.getUrl());
        super.setExtras(address.getExtras());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getFeatureName() != null) {
            sb.append(getFeatureName());
        }
        if (getSubThoroughfare() != null && !getSubThoroughfare().equals(getFeatureName())) {
            sb.append(", " + getSubThoroughfare());
        }
        if (getThoroughfare() != null && !getThoroughfare().equals(getFeatureName())) {
            sb.append(" " + getThoroughfare());
        }
        if (getSubAdminArea() != null && !getSubAdminArea().equals(getFeatureName())) {
            sb.append(", " + getSubAdminArea());
        }
        if (getLocality() != null && !getLocality().equals(getFeatureName())) {
            sb.append(", " + getLocality());
        }
        if (TextUtils.isEmpty(sb.toString())) {
            int maxLines = (ADDRESS_MAX_LINES_TO_SHOW > getMaxAddressLineIndex())
                    ? getMaxAddressLineIndex() + 1 : ADDRESS_MAX_LINES_TO_SHOW;
            sb.append(getAddressLine(0));
            for (int i = 1; i < maxLines; i++) {
                if (getAddressLine(i) != null) {
                    sb.append(", " + getAddressLine(i));
                }
            }
        }
        return sb.toString();
    }

    public static final Parcelable.Creator<CustomAddress> CREATOR =
            new Parcelable.Creator<CustomAddress>() {
                public CustomAddress createFromParcel(Parcel in) {
                    String language = in.readString();
                    String country = in.readString();
                    Locale locale = country.length() > 0 ?
                            new Locale(language, country) :
                            new Locale(language);
                    CustomAddress a = new CustomAddress(locale);

                    int N = in.readInt();
                    if (N > 0) {
                        for (int i = 0; i < N; i++) {
                            int index = in.readInt();
                            String line = in.readString();
                            a.setAddressLine(index, line);
                        }
                    }

                    a.setFeatureName(in.readString());
                    a.setAdminArea(in.readString());
                    a.setSubAdminArea(in.readString());
                    a.setLocality(in.readString());
                    a.setSubLocality(in.readString());
                    a.setThoroughfare(in.readString());
                    a.setSubThoroughfare(in.readString());
                    a.setPremises(in.readString());
                    a.setPostalCode(in.readString());
                    a.setCountryCode(in.readString());
                    a.setCountryName(in.readString());
                    boolean mHasLatitude = in.readInt() != 0;
                    if (mHasLatitude) {
                        a.setLatitude(in.readDouble());
                    }
                    boolean mHasLongitude = in.readInt() != 0;
                    if (mHasLongitude) {
                        a.setLongitude(in.readDouble());
                    }
                    a.setPhone(in.readString());
                    a.setUrl(in.readString());
                    a.setExtras(in.readBundle());
                    return a;
                }

                public CustomAddress[] newArray(int size) {
                    return new CustomAddress[size];
                }
            };

    /**
     * Is this custom address set.
     *
     * @return true if this address has a valid latitude and longitude
     */
    public boolean isSet() {
        return getLatitude() != Double.MAX_VALUE && getLongitude() != Double.MAX_VALUE;
    }

    /**
     * Create a blank CustomAddress.
     *
     * @return CustomAddress with default locale and unset latitude and longitude.
     */
    public static CustomAddress getEmptyAddress() {
        Locale locale = Locale.getDefault();
        CustomAddress addr = new CustomAddress(locale);
        addr.setLatitude(Double.MAX_VALUE);
        addr.setLongitude(Double.MAX_VALUE);
        return addr;
    }
}
