/*
 * Copyright (C) 2017 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.util.test

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.util.MathUtils

class MathUtilTest {
    @Test
    fun orientationToDirection() {
        assertEquals(90.0, MathUtils.toDirection(0.0), 0.0)
        assertEquals(0.0, MathUtils.toDirection(90.0), 0.0)
        assertEquals(270.0, MathUtils.toDirection(180.0), 0.0)
        assertEquals(180.0, MathUtils.toDirection(270.0), 0.0)
    }
}
