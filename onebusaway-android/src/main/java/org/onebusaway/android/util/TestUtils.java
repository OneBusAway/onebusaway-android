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

import android.os.Build;

import org.onebusaway.android.BuildConfig;

/**
 * A class containing utility methods related to unit tests
 */
public class TestUtils {

    /**
     * Returns true if tests are running on an emulator, false if tests are running
     * on an actual device
     *
     * @return true if tests are running on an emulator, false if tests are running
     * on an actual device
     */
    public static boolean isRunningOnEmulator() {
        return Build.FINGERPRINT.contains("generic");
    }

    /**
     * Returns true if the test is running on CI, and false if it is not
     *
     * @return true if the test is running on CI, and false if it is not
     */
    public static boolean isRunningOnCI() {
        return BuildConfig.CI != null && BuildConfig.CI.equals("true");
    }
}
