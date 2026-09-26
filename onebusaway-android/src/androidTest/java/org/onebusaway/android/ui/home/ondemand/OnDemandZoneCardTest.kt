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
package org.onebusaway.android.ui.home.ondemand

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.onebusaway.android.R
import org.onebusaway.android.models.OnDemandService
import org.onebusaway.android.ondemand.TUESDAY_TWO_PM
import org.onebusaway.android.ondemand.sampleMatch
import org.onebusaway.android.ondemand.sampleService
import org.onebusaway.android.ui.compose.createUnconfinedComposeRule
import org.onebusaway.android.ui.compose.theme.ObaTheme

class OnDemandZoneCardTest {

    @get:Rule
    val composeRule = createUnconfinedComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // A state rather than a value so one test can re-render the card with a different contact.
    private fun render(service: MutableState<OnDemandService> = mutableStateOf(sampleService()), moreCount: Int = 0, onCall: (String) -> Unit = {}, onPicker: () -> Unit = {}) {
        composeRule.setContent {
            ObaTheme {
                OnDemandZoneCard(
                    match = sampleMatch(service.value),
                    moreCount = moreCount,
                    color = 0xFF78AA36.toInt(),
                    now = TUESDAY_TWO_PM,
                    onOpenDetail = {},
                    onOpenPicker = onPicker,
                    onCall = onCall,
                    onOpenUrl = {}
                )
            }
        }
    }

    @Test
    fun aPhoneMakesTheCallToBookPrimary() {
        var called: String? = null
        render(onCall = { called = it })
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_call_to_book)).performClick()
        assertEquals("231-582-6900", called)
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_eyebrow)).assertIsDisplayed()
    }

    @Test
    fun aUrlAloneMakesBookOnlinePrimaryAndNoContactLeavesOnlyDetails() {
        val service = mutableStateOf(sampleService(phone = null, url = "https://book.example.org"))
        render(service)
        composeRule.onNodeWithText(context.getString(R.string.ondemand_book_online)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_call_to_book)).assertDoesNotExist()

        service.value = sampleService(phone = null, url = null)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_details)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_card_call_to_book)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.ondemand_book_online)).assertDoesNotExist()
    }

    @Test
    fun theFooterCountsTheOtherServicesAndOpensThePicker() {
        var opened = 0
        render(moreCount = 2, onPicker = { opened++ })
        val footer = context.resources.getQuantityString(R.plurals.ondemand_card_more_services, 2, 2)
        composeRule.onNodeWithText(footer).performClick()
        assertEquals(1, opened)
    }
}
