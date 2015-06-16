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

/**
 * Constants used in the build.gradle build flavors to define certain features per build flavor
 *
 * @author barbeau
 */
public class BuildFlavorConstants {

    public static final String OBA_FLAVOR_BRAND = "oba";
            // Used to show/hide donate/powered by oba pref

    public static final int ARRIVAL_INFO_STYLE_A = 0; // Original OBA style

    public static final int ARRIVAL_INFO_STYLE_B = 1; // Style used by York Region Transit
}
