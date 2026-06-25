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
package org.onebusaway.android.ui.report.problem

import android.location.Location
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule

private class FakeProblemReportRepository(
    var result: Result<Unit> = Result.success(Unit)
) : ProblemReportRepository {

    var lastStopCode: String? = null
    var lastTripCode: String? = null
    var lastOnVehicle: Boolean? = null
    var lastVehicleNumber: String? = null

    override suspend fun submitStop(
        stopId: String,
        code: String,
        comment: String,
        location: Location?
    ): Result<Unit> {
        lastStopCode = code
        return result
    }

    override suspend fun submitTrip(
        params: ProblemParams.Trip,
        code: String,
        comment: String,
        onVehicle: Boolean,
        vehicleNumber: String,
        location: Location?
    ): Result<Unit> {
        lastTripCode = code
        lastOnVehicle = onVehicle
        lastVehicleNumber = vehicleNumber
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProblemReportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stopCodes = ProblemCodes.stop(
        listOf("Choose a problem", "Stop name is wrong", "Something else")
    )

    private val tripCodes = ProblemCodes.trip(
        listOf("Choose a problem", "The bus never came", "Something else")
    )

    private fun stopViewModel(repo: ProblemReportRepository) = ProblemReportViewModel(
        params = ProblemParams.Stop("1_75403"),
        codes = stopCodes,
        headsign = null,
        repository = repo
    )

    private fun tripViewModel(repo: ProblemReportRepository) = ProblemReportViewModel(
        params = ProblemParams.Trip("1_trip", "1_75403", "1_4123", serviceDate = 123L),
        codes = tripCodes,
        headsign = "Route 40 to Downtown",
        repository = repo
    )

    @Test
    fun `initial state is the hint with submit disabled`() = runTest {
        val viewModel = stopViewModel(FakeProblemReportRepository())

        val state = viewModel.formState.value
        assertEquals(ProblemKind.STOP, state.kind)
        assertEquals(0, state.selectedCodeIndex)
        assertFalse(state.canSubmit)
        assertEquals(SubmitState.Idle, viewModel.submitState.value)
    }

    @Test
    fun `choosing a real category enables submit`() = runTest {
        val viewModel = stopViewModel(FakeProblemReportRepository())

        viewModel.onCodeSelected(1)

        assertTrue(viewModel.formState.value.canSubmit)
    }

    @Test
    fun `submit without a chosen category is a no-op`() = runTest {
        val repo = FakeProblemReportRepository()
        val viewModel = stopViewModel(repo)

        viewModel.submit(location = null)
        advanceUntilIdle()

        assertEquals(SubmitState.Idle, viewModel.submitState.value)
        assertEquals(null, repo.lastStopCode)
    }

    @Test
    fun `successful stop submit sends the chosen code and reports Sent`() = runTest {
        val repo = FakeProblemReportRepository(Result.success(Unit))
        val viewModel = stopViewModel(repo)
        viewModel.onCodeSelected(1)

        viewModel.submit(location = null)
        advanceUntilIdle()

        assertEquals(stopCodes[1].code, repo.lastStopCode)
        assertEquals(SubmitState.Sent, viewModel.submitState.value)
    }

    @Test
    fun `failed submit reports Error`() = runTest {
        val repo = FakeProblemReportRepository(Result.failure(IOException()))
        val viewModel = stopViewModel(repo)
        viewModel.onCodeSelected(2)

        viewModel.submit(location = null)
        advanceUntilIdle()

        assertEquals(SubmitState.Error, viewModel.submitState.value)
    }

    @Test
    fun `trip submit forwards on-vehicle and vehicle number`() = runTest {
        val repo = FakeProblemReportRepository(Result.success(Unit))
        val viewModel = tripViewModel(repo)
        viewModel.onCodeSelected(1)
        viewModel.onVehicleToggle(true)
        viewModel.onVehicleNumberChange("1234")

        viewModel.submit(location = null)
        advanceUntilIdle()

        assertEquals(tripCodes[1].code, repo.lastTripCode)
        assertEquals(true, repo.lastOnVehicle)
        assertEquals("1234", repo.lastVehicleNumber)
        assertEquals(SubmitState.Sent, viewModel.submitState.value)
    }

    @Test
    fun `onSubmitResultHandled resets to Idle after a terminal result`() = runTest {
        val repo = FakeProblemReportRepository(Result.failure(IOException()))
        val viewModel = stopViewModel(repo)
        viewModel.onCodeSelected(1)
        viewModel.submit(location = null)
        advanceUntilIdle()
        assertEquals(SubmitState.Error, viewModel.submitState.value)

        viewModel.onSubmitResultHandled()

        assertEquals(SubmitState.Idle, viewModel.submitState.value)
    }
}
