/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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

import android.content.Intent;
import android.os.Bundle;

/**
 * A class containing utility methods related to handling fragments
 */
public class FragmentUtils {

    public static final String URI = "uri";

    public static Bundle getIntentArgs(Intent intent) {
        Bundle args = intent.getExtras();
        if (args != null) {
            args = (Bundle) args.clone();
        } else {
            args = new Bundle();
        }
        args.putParcelable(URI, intent.getData());
        return args;
    }
}
