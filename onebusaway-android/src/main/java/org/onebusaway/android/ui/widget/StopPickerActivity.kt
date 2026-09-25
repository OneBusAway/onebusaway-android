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
package org.onebusaway.android.ui.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import org.onebusaway.android.R
import org.onebusaway.android.database.oba.StopDao
import org.onebusaway.android.database.oba.StopListRow
import org.onebusaway.android.region.RegionRepository
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.theme.ObaTheme
import org.onebusaway.android.ui.mylists.recentCutoff
import org.onebusaway.android.util.DisplayFormat

/**
 * Lets the user pick a stop for the Stop Times widget: starred stops, then recently viewed stops
 * (the same "recent" window the My Lists tab uses, [recentCutoff]). Returns `RESULT_OK` with
 * [EXTRA_STOP_ID]/[EXTRA_STOP_NAME] set once a stop is tapped.
 */
@AndroidEntryPoint
class StopPickerActivity : ComponentActivity() {

    @Inject
    lateinit var stopDao: StopDao

    @Inject
    lateinit var regionRepository: RegionRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val starred = regionRepository.region.flatMapLatest { stopDao.starredByName(it?.id) }
        val recent = regionRepository.region.flatMapLatest { stopDao.recents(recentCutoff(), it?.id) }

        setContent {
            ObaTheme {
                val starredStops by starred.collectAsStateWithLifecycle(initialValue = emptyList())
                val recentStops by recent.collectAsStateWithLifecycle(initialValue = emptyList())
                StopPickerScreen(
                    starred = starredStops,
                    recent = recentStops,
                    onStopPicked = { stop ->
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_STOP_ID, stop.id)
                                .putExtra(EXTRA_STOP_NAME, stop.uiName.orEmpty())
                        )
                        finish()
                    },
                    onBack = ::finish
                )
            }
        }
    }

    companion object {
        const val EXTRA_STOP_ID = "stop_id"
        const val EXTRA_STOP_NAME = "stop_name"
    }
}

@Composable
private fun StopPickerScreen(
    starred: List<StopListRow>,
    recent: List<StopListRow>,
    onStopPicked: (StopListRow) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { ObaTopAppBar(title = stringResource(R.string.widget_config_select_a_stop), onBack = onBack) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "starred_header") { SectionHeader(stringResource(R.string.my_starred_title)) }
            if (starred.isEmpty()) {
                item(key = "starred_empty") { EmptyRow(stringResource(R.string.my_no_starred_stops)) }
            } else {
                items(starred, key = { "starred_${it.id}" }) { StopRow(it, onStopPicked) }
            }
            item(key = "recent_header") { SectionHeader(stringResource(R.string.my_recent_title)) }
            if (recent.isEmpty()) {
                item(key = "recent_empty") { EmptyRow(stringResource(R.string.my_no_recent_stops)) }
            } else {
                items(recent, key = { "recent_${it.id}" }) { StopRow(it, onStopPicked) }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(20.dp)
    )
}

@Composable
private fun StopRow(stop: StopListRow, onClick: (StopListRow) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(stop) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stop.uiName.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            stop.direction?.takeIf { it.isNotEmpty() }?.let { direction ->
                Text(
                    stringResource(DisplayFormat.getStopDirectionText(direction)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (stop.favorite == 1) {
            Icon(
                painter = painterResource(R.drawable.star),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
