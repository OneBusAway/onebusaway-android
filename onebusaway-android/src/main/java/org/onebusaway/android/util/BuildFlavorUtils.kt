/*
 * Copyright (C) 2015 Sean J. Barbeau (sjbarbeau@gmail.com)
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
package org.onebusaway.android.util

import org.onebusaway.android.BuildConfig

/** Constants and feature checks shared by the product flavors. */
object BuildFlavorUtils {
    const val OBA_FLAVOR_BRAND = "oba"
    const val AGENCYY_FLAVOR_BRAND = "agencyY"

    @JvmStatic fun isPeliasApiKeyDefined(): Boolean = BuildConfig.PELIAS_API_KEY.isNotEmpty()

    // Case-insensitive, as it has always been: brand names are Gradle flavor names and are not
    // uniformly lower-case (see AGENCYY_FLAVOR_BRAND), so an exact match would silently send a
    // future brand named e.g. "OBA" down the white-label path.
    @JvmStatic fun isOBABuildFlavor(): Boolean = BuildConfig.FLAVOR_brand.equals(OBA_FLAVOR_BRAND, ignoreCase = true)
}
