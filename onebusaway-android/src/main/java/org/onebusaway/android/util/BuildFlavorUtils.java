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
package org.onebusaway.android.util;

import org.onebusaway.android.BuildConfig;

/**
 * Constants and utilities used in the build.gradle build flavors to define certain features per
 * build flavor.
 *
 * @author barbeau
 */
public class BuildFlavorUtils {

  public static final String OBA_FLAVOR_BRAND = "oba";

  public static final String AGENCYY_FLAVOR_BRAND = "agencyY";

  /**
   * Returns true if the Pelias API key is non-empty, false if it is not
   *
   * @return true if the Pelias API key is non-empty, false if it is not
   */
  public static boolean isPeliasApiKeyDefined() {
    String peliasKey = BuildConfig.PELIAS_API_KEY;
    return peliasKey != null && peliasKey.length() != 0;
  }

  /**
   * Helper function to determine whether this is the official app or a white-label version.
   *
   * @return true if this is the OBA Build Flavor (i.e. the 'official' app), and false if it is not
   *     (i.e. a white-label app).
   */
  public static boolean isOBABuildFlavor() {
    return BuildConfig.FLAVOR_brand.equalsIgnoreCase(OBA_FLAVOR_BRAND);
  }
}
