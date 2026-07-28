/* Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com) */
package org.onebusaway.android.util

import android.os.Build
import org.onebusaway.android.BuildConfig

/** Utility methods used by instrumentation tests. */
object TestUtils {
    @JvmStatic fun isRunningOnEmulator(): Boolean = Build.FINGERPRINT.contains("generic")

    @JvmStatic fun isRunningOnCI(): Boolean = BuildConfig.CI == "true"
}
