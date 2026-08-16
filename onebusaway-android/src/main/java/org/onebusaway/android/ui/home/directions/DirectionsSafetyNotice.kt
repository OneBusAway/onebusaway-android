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
package org.onebusaway.android.ui.home.directions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.preferences.PreferencesRepository
import org.onebusaway.android.ui.compose.components.AlertSeverity
import org.onebusaway.android.ui.compose.components.alertAccentColor

/**
 * Whether the rider has yet to acknowledge the directions safety notice (#2218) — true on a fresh
 * install, false forever after the acknowledgement. Pure (it only reads [prefs]) so the gate is
 * JVM-unit-testable, the same shape
 * [ArrivalTutorial.pendingSteps][org.onebusaway.android.ui.tutorial.ArrivalTutorial.pendingSteps] uses.
 */
internal fun isSafetyNoticePending(prefs: PreferencesRepository): Boolean = !prefs.getBoolean(R.string.preference_key_directions_safety_acknowledged, false)

/** Records the acknowledgement, so the notice is never shown again on this install. */
internal fun markSafetyNoticeAcknowledged(prefs: PreferencesRepository) {
    prefs.setBoolean(R.string.preference_key_directions_safety_acknowledged, true)
}

/**
 * The one-time notice that stands between a rider and their first set of directions (#2218).
 *
 * OTP plans walking legs from open map data whose crosswalk, signal and sidewalk coverage is patchy —
 * a route really can send someone across a busy street where there is no safe crossing — while riders
 * arrive expecting parity with commercial map apps. Tuning OTP is the real fix; this is the honest
 * disclosure that has to hold in the meantime, and it is shown once rather than on every plan because
 * the per-itinerary
 * [DirectionsCautionBanner][org.onebusaway.android.ui.tripresults.DirectionsCautionBanner] is the
 * standing reminder.
 *
 * Shown whenever [active] and the acknowledgement is still owed, which covers *every* way into
 * directions (the drawer, the map long-press menu, a pinned-trip resume, a monitor notification):
 * they all land on the same [CurrentFocus.Directions][org.onebusaway.android.ui.home.CurrentFocus]
 * that [active] is read from, so none of them can slip past it.
 *
 * It is a full-screen [Dialog] rather than a sibling in the host's `Box` because it needs its own
 * window: that is what puts it unconditionally over the map, the form card *and* the results sheet,
 * and what lets it take Back ahead of the host's directions `BackHandler` without either having to
 * know about the other.
 *
 * **Back is not an acknowledgement.** It leaves directions instead ([onDecline]) and writes nothing,
 * so the notice returns on the next entry. There is no way through to the planner except the button,
 * but the rider is never trapped behind it either.
 */
@Composable
internal fun DirectionsSafetyNotice(active: Boolean, onDecline: () -> Unit) {
    val context = LocalContext.current
    // Resolved once rather than per recomposition, matching the remember { …EntryPoint.get(…) }
    // pattern elsewhere. The repository reads its own writes synchronously, so the initial read
    // already reflects an acknowledgement made earlier in this process.
    val prefs = remember { PreferencesEntryPoint.get(context) }
    // Seeded from prefs and flipped locally on acknowledgement: the write is async-persisted, and
    // this is what closes the notice on the very next frame rather than on the next process.
    var pending by rememberSaveable { mutableStateOf(isSafetyNoticePending(prefs)) }
    if (!active || !pending) return

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(
            // A tap outside can't reach it (it fills the window), but saying so keeps the intent
            // explicit: the only two answers are the button and Back.
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    // Scrolls rather than clips: at the largest accessibility font scale, or in
                    // landscape, the body plus the button is taller than the window.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_warning_24),
                    contentDescription = null,
                    tint = alertAccentColor(AlertSeverity.WARNING),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.directions_safety_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(
                        R.string.directions_safety_body,
                        stringResource(R.string.app_name)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.directions_safety_body_secondary),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        markSafetyNoticeAcknowledged(prefs)
                        pending = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.directions_safety_acknowledge))
                }
            }
        }
    }
}
