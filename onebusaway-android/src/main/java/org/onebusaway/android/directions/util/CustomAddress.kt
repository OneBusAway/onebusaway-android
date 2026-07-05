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
package org.onebusaway.android.directions.util

import android.location.Address
import android.os.Parcel
import android.os.Parcelable
import org.geojson.Feature
import org.geojson.Point
import java.util.Locale

/**
 * Created by foucelhas on 18/08/14.
 */
// Seems that subThoroughFare is street number and thoroughfare is street name.
class CustomAddress(locale: Locale) : Address(locale) {

    /**
     * Return true if this location has been labeled under the category of "public transportation",
     * false if it has not
     */
    var isTransitCategory: Boolean = false
        private set

    constructor() : this(Locale.getDefault())

    /**
     * Creates a CustomAddress out of a Android Address from the Android platform Geocoder API
     */
    constructor(address: Address) : this(address.locale) {
        for (i in 0..address.maxAddressLineIndex) {
            setAddressLine(i, address.getAddressLine(i))
        }
        featureName = address.featureName
        adminArea = address.adminArea
        subAdminArea = address.subAdminArea
        locality = address.locality
        subLocality = address.subLocality
        thoroughfare = address.thoroughfare
        subThoroughfare = address.subThoroughfare
        postalCode = address.postalCode
        countryCode = address.countryCode
        countryName = address.countryName
        latitude = address.latitude
        longitude = address.longitude
        phone = address.phone
        url = address.url
        extras = address.extras
    }

    /**
     * Creates a CustomAddress out of a GeoJSON Feature (e.g., from Pelias Autocomplete API)
     */
    constructor(address: Feature) : this(Locale.getDefault()) {
        // TODO - see if we need to fill out more fields
        // For example response with fields see https://github.com/CUTR-at-USF/pelias-client-library/blob/master/src/test/resources/autocomplete-with-focus.json
        // and https://github.com/CUTR-at-USF/pelias-client-library/blob/master/src/test/java/edu/usf/cutr/pelias/AutocompleteTest.java#L42
        setAddressLine(0, address.properties["name"] as String?)
        featureName = address.properties["label"] as String?
        postalCode = address.properties["postalcode"] as String?
        countryName = address.properties["country"] as String?

        val p = address.geometry as Point
        latitude = p.coordinates.latitude
        longitude = p.coordinates.longitude

        // Check if the geocoder marked this location as having a public transportation category
        val categories: ArrayList<String>? = address.getProperty("category")
        isTransitCategory =
            categories?.any { it.equals("transport:public", ignoreCase = true) } == true
    }

    override fun toString(): String {
        val sb = StringBuilder()
        if (featureName != null) {
            sb.append(featureName)
        }
        if (subThoroughfare != null && subThoroughfare != featureName) {
            sb.append(", $subThoroughfare")
        }
        if (thoroughfare != null && thoroughfare != featureName) {
            sb.append(" $thoroughfare")
        }
        if (subAdminArea != null && subAdminArea != featureName) {
            sb.append(", $subAdminArea")
        }
        if (locality != null && locality != featureName) {
            sb.append(", $locality")
        }
        if (sb.isEmpty()) {
            val maxLines = if (ADDRESS_MAX_LINES_TO_SHOW > maxAddressLineIndex) {
                maxAddressLineIndex + 1
            } else {
                ADDRESS_MAX_LINES_TO_SHOW
            }
            sb.append(getAddressLine(0))
            for (i in 1 until maxLines) {
                if (getAddressLine(i) != null) {
                    sb.append(", ${getAddressLine(i)}")
                }
            }
        }
        return sb.toString()
    }

    /**
     * Is this custom address set.
     *
     * @return true if this address has a valid latitude and longitude
     */
    val isSet: Boolean
        get() = latitude != Double.MAX_VALUE && longitude != Double.MAX_VALUE

    companion object {

        private const val ADDRESS_MAX_LINES_TO_SHOW = 5

        @JvmField
        val CREATOR: Parcelable.Creator<CustomAddress> =
            object : Parcelable.Creator<CustomAddress> {
                override fun createFromParcel(parcel: Parcel): CustomAddress {
                    val language = parcel.readString()
                    val country = parcel.readString()
                    val locale = if (!country.isNullOrEmpty()) {
                        Locale(language, country)
                    } else {
                        Locale(language)
                    }
                    val a = CustomAddress(locale)

                    val n = parcel.readInt()
                    if (n > 0) {
                        for (i in 0 until n) {
                            val index = parcel.readInt()
                            val line = parcel.readString()
                            a.setAddressLine(index, line)
                        }
                    }

                    a.featureName = parcel.readString()
                    a.adminArea = parcel.readString()
                    a.subAdminArea = parcel.readString()
                    a.locality = parcel.readString()
                    a.subLocality = parcel.readString()
                    a.thoroughfare = parcel.readString()
                    a.subThoroughfare = parcel.readString()
                    a.premises = parcel.readString()
                    a.postalCode = parcel.readString()
                    a.countryCode = parcel.readString()
                    a.countryName = parcel.readString()
                    if (parcel.readInt() != 0) {
                        a.latitude = parcel.readDouble()
                    }
                    if (parcel.readInt() != 0) {
                        a.longitude = parcel.readDouble()
                    }
                    a.phone = parcel.readString()
                    a.url = parcel.readString()
                    a.extras = parcel.readBundle()
                    return a
                }

                override fun newArray(size: Int): Array<CustomAddress?> = arrayOfNulls(size)
            }

        /**
         * Create a blank CustomAddress.
         *
         * @return CustomAddress with default locale and unset latitude and longitude.
         */
        @JvmStatic
        fun getEmptyAddress(): CustomAddress {
            val addr = CustomAddress(Locale.getDefault())
            addr.latitude = Double.MAX_VALUE
            addr.longitude = Double.MAX_VALUE
            return addr
        }
    }
}
