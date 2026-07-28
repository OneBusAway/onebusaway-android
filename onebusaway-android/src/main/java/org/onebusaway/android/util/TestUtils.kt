/* Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com) */
package org.onebusaway.android.util

import android.os.Build
import org.onebusaway.android.BuildConfig

/** Utility methods used by instrumentation tests. */
object TestUtils {
    @JvmStatic
    fun isRunningOnEmulator(): Boolean = isEmulatorBuild(
        fingerprint = Build.FINGERPRINT,
        model = Build.MODEL,
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        device = Build.DEVICE,
        product = Build.PRODUCT,
        hardware = Build.HARDWARE
    )

    @JvmStatic fun isRunningOnCI(): Boolean = BuildConfig.CI == "true"
}

internal fun isEmulatorBuild(
    fingerprint: String,
    model: String,
    manufacturer: String,
    brand: String,
    device: String,
    product: String,
    hardware: String
): Boolean = fingerprint.startsWith("generic") ||
    fingerprint.startsWith("unknown") ||
    model.contains("google_sdk") ||
    model.contains("Emulator") ||
    model.contains("Android SDK built for") ||
    manufacturer.contains("Genymotion") ||
    hardware in setOf("goldfish", "ranchu", "vbox86") ||
    product in setOf("sdk", "google_sdk", "sdk_x86", "sdk_gphone64_arm64") ||
    (brand.startsWith("generic") && device.startsWith("generic"))
