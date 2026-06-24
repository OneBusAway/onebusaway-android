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
package org.onebusaway.android.ui.home.donation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.onebusaway.android.R
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.theme.ObaTheme

/**
 * The donation "learn more" screen (why donations matter, with a button out to the donations page):
 * a NavHost destination hosted by [HomeActivity]. The donate button's behavior (dismiss pending
 * requests + open the donations page) lives in the destination.
 */
@Composable
internal fun DonationLearnMoreScreen(onBack: () -> Unit, onDonate: () -> Unit) {
    Scaffold(
        topBar = {
            ObaTopAppBar(stringResource(R.string.title_activity_donation_learn_more), onBack)
        }
    ) { padding ->
        val margin = dimensionResource(R.dimen.dialog_margin)
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painter = painterResource(R.drawable.wmata),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Text(
                text = stringResource(
                    R.string.donation_learn_more_explanation, stringResource(R.string.app_name)
                ),
                fontSize = 18.sp,
                modifier = Modifier.padding(margin)
            )
            Button(
                onClick = onDonate,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.theme_primary),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = margin, end = margin, bottom = margin)
            ) {
                Text(stringResource(R.string.donation_view_donate_now_button), fontSize = 24.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DonationLearnMorePreview() {
    ObaTheme {
        Box {
            DonationLearnMoreScreen(onBack = {}, onDonate = {})
        }
    }
}
