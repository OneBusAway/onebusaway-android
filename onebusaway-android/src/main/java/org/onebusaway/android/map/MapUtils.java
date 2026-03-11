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

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaApi;
import org.onebusaway.android.io.request.ObaResponse;
import org.onebusaway.android.util.UIUtils;

import static org.onebusaway.android.util.UIUtils.canManageDialog;

/**
 * Utility methods related to the map display.
 */
public class MapUtils {

    /**
     * Shows error messages related to stops, routes, and vehicles on the map, based on the
     * response from the server.
     *
     * @param response the response from the server, or null if the response object was null
     */
    public static void showMapError(ObaResponse response) {
        Context context = Application.get().getApplicationContext();
        int code;
        if (response != null) {
            code = response.getCode();
        } else {
            code = ObaApi.OBA_INTERNAL_ERROR;
        }
        if (canManageDialog(context)) {
            Toast.makeText(context,
                    context.getString(UIUtils.getMapErrorString(context, code)),
                    Toast.LENGTH_LONG).show();
        }
    }
}
