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
package org.onebusaway.android.ui.report.types

import org.junit.Assert.assertEquals
import org.junit.Test
import org.onebusaway.android.ui.compose.ListUiState

class ReportTypeListViewModelTest {

    private fun type(action: ReportAction) = ReportType(action.name, "", 0, action)

    private val allTypes = ReportAction.entries.map { type(it) }

    @Test
    fun `the gate keeps every row when the region has a contact email`() {
        assertEquals(allTypes, ReportTypeGate.apply(allTypes, emailDefined = true))
    }

    @Test
    fun `the gate drops app feedback when the region has no contact email`() {
        val gated = ReportTypeGate.apply(allTypes, emailDefined = false)

        assertEquals(
            listOf(ReportAction.CUSTOMER_SERVICE, ReportAction.STOP_PROBLEM, ReportAction.ARRIVAL_PROBLEM),
            gated.map { it.action }
        )
    }

    @Test
    fun `the view model exposes the repository's types as a ready Success`() {
        val types = listOf(type(ReportAction.CUSTOMER_SERVICE), type(ReportAction.STOP_PROBLEM))
        val viewModel = ReportTypeListViewModel(object : ReportTypeRepository {
            override fun reportTypes() = types
        })

        assertEquals(ListUiState.Success(types), viewModel.state.value)
    }
}
