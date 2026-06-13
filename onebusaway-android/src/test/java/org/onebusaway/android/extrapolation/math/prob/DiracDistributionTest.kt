/*
 * Copyright (C) 2024-2026 Open Transit Software Foundation
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
package org.onebusaway.android.extrapolation.math.prob

import org.junit.Assert.assertEquals
import org.junit.Test

class DiracDistributionTest {

    private val dist = DiracDistribution(5.0)

    @Test fun `mean equals value`() = assertEquals(5.0, dist.mean, 0.0)

    @Test
    fun `pdf is always zero`() {
        assertEquals(0.0, dist.pdf(5.0), 0.0)
        assertEquals(0.0, dist.pdf(0.0), 0.0)
        assertEquals(0.0, dist.pdf(100.0), 0.0)
    }

    @Test
    fun `cdf is 0 below value and 1 at or above`() {
        assertEquals(0.0, dist.cdf(4.99), 0.0)
        assertEquals(1.0, dist.cdf(5.0), 0.0)
        assertEquals(1.0, dist.cdf(5.01), 0.0)
    }

    @Test
    fun `quantile returns value for p in (0,1)`() {
        assertEquals(5.0, dist.quantile(0.01), 0.0)
        assertEquals(5.0, dist.quantile(0.5), 0.0)
        assertEquals(5.0, dist.quantile(0.99), 0.0)
    }

    @Test
    fun `quantile boundaries`() {
        assertEquals(5.0, dist.quantile(0.0), 0.0)
        assertEquals(5.0, dist.quantile(1.0), 0.0)
    }

    @Test fun `median equals value`() = assertEquals(5.0, dist.median(), 0.0)
}
