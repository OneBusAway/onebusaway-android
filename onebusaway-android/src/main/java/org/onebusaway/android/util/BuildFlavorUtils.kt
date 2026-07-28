/* Copyright (C) 2015 Sean J. Barbeau (sjbarbeau@gmail.com) */
package org.onebusaway.android.util

import org.onebusaway.android.BuildConfig

/** Constants and feature checks shared by the product flavors. */
object BuildFlavorUtils {
    const val OBA_FLAVOR_BRAND = "oba"
    const val AGENCYY_FLAVOR_BRAND = "agencyY"

    @JvmStatic fun isPeliasApiKeyDefined(): Boolean = BuildConfig.PELIAS_API_KEY.isNotEmpty()

    @JvmStatic fun isOBABuildFlavor(): Boolean = BuildConfig.FLAVOR_brand.equals(OBA_FLAVOR_BRAND, ignoreCase = true)
}
