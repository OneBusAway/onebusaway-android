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
package org.onebusaway.android.ui.arrivals

import androidx.compose.material3.SnackbarHostState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hiding several routes in a row shows one undo snackbar, the latest, rather than queueing one per
 * hide (#2366): each [UndoSnackbar.show] replaces the previous, whose undo is forfeited.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class UndoSnackbarTest {

    @Test
    fun latestHideReplacesTheSnackbarBeforeIt() = runTest {
        val host = SnackbarHostState()
        val undoSnackbar = UndoSnackbar(host, backgroundScope)
        val undone = mutableListOf<String>()

        undoSnackbar.show(UndoSnackbarVisuals("Arrivals for 44 hidden", "UNDO")) { undone += "first" }
        runCurrent()
        undoSnackbar.show(UndoSnackbarVisuals("Arrivals for 48 hidden", "UNDO")) { undone += "second" }
        runCurrent()

        assertEquals("Arrivals for 48 hidden", host.currentSnackbarData?.visuals?.message)

        host.currentSnackbarData!!.performAction()
        runCurrent()

        assertEquals("only the latest snackbar's undo runs", listOf("second"), undone)
        assertNull("the replaced snackbar must not come back after the latest", host.currentSnackbarData)
    }
}
