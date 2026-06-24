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
package org.onebusaway.android.ui.report.open311

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.testing.MainDispatcherRule

private class FakeOpen311Repository(
    var loadResult: Result<Open311FormState> = Result.success(Open311FormState()),
    var submitResult: Open311SubmitState = Open311SubmitState.Sent
) : Open311Repository {

    var submittedForm: Open311FormState? = null
    var submitGate: CompletableDeferred<Unit>? = null

    override suspend fun loadForm(): Result<Open311FormState> = loadResult

    override suspend fun submit(form: Open311FormState): Open311SubmitState {
        submittedForm = form
        submitGate?.await()
        return submitResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Open311ProblemViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val loadedForm = Open311FormState(
        fields = listOf(
            Open311Field(1, "Name", Open311FieldType.TEXT, false, "string"),
            Open311Field(
                2, "Pick", Open311FieldType.MULTI_CHOICE, false, "multivaluelist",
                options = listOf(Open311Option("a", "A"), Open311Option("b", "B"))
            )
        ),
        values = mapOf(
            1 to FieldValue.Text(""),
            2 to FieldValue.MultiChoice(emptySet())
        ),
        contact = ContactInfo(firstName = "Ada")
    )

    @Test
    fun `load surfaces the form and flips to Loaded`() = runTest {
        val viewModel = Open311ProblemViewModel(FakeOpen311Repository(Result.success(loadedForm)))

        advanceUntilIdle()

        assertEquals(Open311LoadState.LOADED, viewModel.loadState.value)
        assertEquals(loadedForm, viewModel.form.value)
    }

    @Test
    fun `load failure flips to Error`() = runTest {
        val viewModel = Open311ProblemViewModel(
            FakeOpen311Repository(Result.failure(RuntimeException()))
        )

        advanceUntilIdle()

        assertEquals(Open311LoadState.ERROR, viewModel.loadState.value)
    }

    @Test
    fun `field edits update the immutable form state`() = runTest {
        val viewModel = Open311ProblemViewModel(FakeOpen311Repository(Result.success(loadedForm)))
        advanceUntilIdle()

        viewModel.setFieldText(1, "Lovelace")
        viewModel.setMultiChoice(2, "a", checked = true)
        viewModel.setMultiChoice(2, "b", checked = true)
        viewModel.setMultiChoice(2, "a", checked = false)
        viewModel.setAnonymous(true)

        val form = viewModel.form.value
        assertEquals("Lovelace", form.textValue(1))
        assertEquals(setOf("b"), form.multiChoice(2))
        assertEquals(true, form.anonymous)
    }

    @Test
    fun `submit sends the current form and reports Sent`() = runTest {
        val repo = FakeOpen311Repository(Result.success(loadedForm), Open311SubmitState.Sent)
        val viewModel = Open311ProblemViewModel(repo)
        advanceUntilIdle()
        viewModel.setMainDescription("Broken sign")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Broken sign", repo.submittedForm?.mainDescription)
        assertEquals(Open311SubmitState.Sent, viewModel.submitState.value)
    }

    @Test
    fun `validation error is surfaced as the submit state`() = runTest {
        val repo = FakeOpen311Repository(
            Result.success(loadedForm),
            Open311SubmitState.ValidationError("Name is required")
        )
        val viewModel = Open311ProblemViewModel(repo)
        advanceUntilIdle()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(Open311SubmitState.ValidationError("Name is required"), viewModel.submitState.value)
    }

    @Test
    fun `onSubmitStateHandled resets a terminal result to Idle`() = runTest {
        val repo = FakeOpen311Repository(Result.success(loadedForm), Open311SubmitState.Sent)
        val viewModel = Open311ProblemViewModel(repo)
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(Open311SubmitState.Sent, viewModel.submitState.value)

        viewModel.onSubmitStateHandled()

        assertEquals(Open311SubmitState.Idle, viewModel.submitState.value)
    }

    @Test
    fun `cancelSubmit aborts an in-flight submit and returns to Idle`() = runTest {
        val repo = FakeOpen311Repository(Result.success(loadedForm)).apply {
            submitGate = CompletableDeferred()
        }
        val viewModel = Open311ProblemViewModel(repo)
        advanceUntilIdle()

        viewModel.submit()
        assertEquals(Open311SubmitState.Submitting, viewModel.submitState.value)

        viewModel.cancelSubmit()

        assertEquals(Open311SubmitState.Idle, viewModel.submitState.value)
    }
}
