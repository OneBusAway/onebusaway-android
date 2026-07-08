/*
 * Copyright (C) 2026 Open Transit Software Foundation
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
package org.onebusaway.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class WireTimeEscapeDetectorTest {

    /** A stand-in wire DTO in the real contract package, with a time field and a non-time field. */
    private val stopTimeDto = kotlin(
        """
        package org.onebusaway.android.api.contract
        data class StopTime(
            val stopId: String = "",
            val arrivalTime: Long = 0,
            val departureTime: Long = 0,
        )
        """,
    ).indented()

    private fun lintKotlin(vararg sources: String) =
        lint()
            .files(stopTimeDto, *sources.map { kotlin(it).indented() }.toTypedArray())
            .issues(WireTimeEscapeDetector.ISSUE)
            .allowMissingSdk()
            .run()

    /** Reading a wire time field from app code (outside the adapter layer) is flagged. */
    @Test
    fun flagsWireTimeFieldReadOutsideAdapter() {
        lintKotlin(
            """
            package org.onebusaway.android.ui.trip
            import org.onebusaway.android.api.contract.StopTime
            fun render(st: StopTime): Long = st.arrivalTime
            """,
        ).expectWarningCount(1)
    }

    /** The adapter layer is the sanctioned mint boundary — not flagged. */
    @Test
    fun doesNotFlagWireTimeFieldReadInAdapter() {
        lintKotlin(
            """
            package org.onebusaway.android.api.adapters
            import org.onebusaway.android.api.contract.StopTime
            fun mint(st: StopTime): Long = st.arrivalTime
            """,
        ).expectClean()
    }

    /** Data sources may touch the DTO directly too. */
    @Test
    fun doesNotFlagWireTimeFieldReadInDataSource() {
        lintKotlin(
            """
            package org.onebusaway.android.api.data
            import org.onebusaway.android.api.contract.StopTime
            fun peek(st: StopTime): Long = st.arrivalTime
            """,
        ).expectClean()
    }

    /** A non-time field on the same DTO is not a wire time escape. */
    @Test
    fun doesNotFlagNonTimeField() {
        lintKotlin(
            """
            package org.onebusaway.android.ui.trip
            import org.onebusaway.android.api.contract.StopTime
            fun id(st: StopTime): String = st.stopId
            """,
        ).expectClean()
    }

    /** No pass-through exemption: even handing the raw field straight to a sink is an escape. */
    @Test
    fun flagsPassThroughOutsideAdapter() {
        lintKotlin(
            """
            package org.onebusaway.android.ui.trip
            import org.onebusaway.android.api.contract.StopTime
            fun sink(ms: Long) {}
            fun render(st: StopTime) { sink(st.arrivalTime) }
            """,
        ).expectWarningCount(1)
    }
}
