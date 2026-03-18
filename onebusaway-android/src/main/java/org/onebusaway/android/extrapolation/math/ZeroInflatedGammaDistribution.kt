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
package org.onebusaway.android.extrapolation.math

/**
 * Zero-inflated Gamma distribution with point mass [p0] at zero.
 *
 * With probability [p0], the value is exactly zero. With probability (1 - [p0]), the value follows
 * a Gamma distribution parameterized by [alpha] (shape) and [scale].
 */
class ZeroInflatedGammaDistribution(
        p0: Double,
        @JvmField val alpha: Double,
        @JvmField val scale: Double
) : ZeroInflatedDistribution(p0, GammaDistribution(alpha, scale))
