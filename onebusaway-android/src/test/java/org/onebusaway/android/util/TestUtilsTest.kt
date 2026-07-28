/*
 * Copyright (C) 2026 Open Transit Software Foundation
 * Licensed under the Apache License, Version 2.0
 */
package org.onebusaway.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestUtilsTest {
    @Test
    fun `recognizes common Android emulator identities`() {
        assertTrue(build(fingerprint = "generic/sdk/generic:15/test-keys"))
        assertTrue(build(model = "Android SDK built for x86_64"))
        assertTrue(build(model = "sdk_gphone64_arm64", hardware = "ranchu"))
        assertTrue(build(manufacturer = "Genymotion"))
        assertTrue(build(brand = "generic", device = "generic_x86_64"))
    }

    @Test
    fun `does not classify a physical device as an emulator`() {
        assertFalse(
            build(
                fingerprint = "google/husky/husky:16/BP2A/release-keys",
                model = "Pixel 8 Pro",
                manufacturer = "Google",
                brand = "google",
                device = "husky",
                product = "husky",
                hardware = "zuma"
            )
        )
    }

    private fun build(
        fingerprint: String = "google/sdk_gphone64_arm64/emu64a:16/test-keys",
        model: String = "Pixel",
        manufacturer: String = "Google",
        brand: String = "google",
        device: String = "emu64a",
        product: String = "emu64a",
        hardware: String = "cutf_cvm"
    ): Boolean = isEmulatorBuild(
        fingerprint,
        model,
        manufacturer,
        brand,
        device,
        product,
        hardware
    )
}
