/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), and individual contributors.
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
package org.onebusaway.android.map;

import android.content.Context;
import android.widget.Toast;

import org.onebusaway.android.util.ObaRequestErrors;

/**
 * Utility methods related to the map display.
 */
public class MapUtils {

    /** Shows the map error toast for an OBA status [code] (used by the modernized io/client callers). */
    public static void showMapError(Context context, int code) {
        Toast.makeText(context,
                ObaRequestErrors.getMapErrorString(context, code),
                Toast.LENGTH_LONG).show();
    }
}
