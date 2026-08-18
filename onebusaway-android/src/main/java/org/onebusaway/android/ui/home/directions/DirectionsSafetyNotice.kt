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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onebusaway.android.R
import org.onebusaway.android.app.di.DemoEntryPoint
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
 * Whether to put the notice on screen right now: it is still owed ([pending], from
 * [isSafetyNoticePending]) *and* the app isn't showing the demo transit system ([demoActive], #2164).
 *
 * **Suppressed is not acknowledged, and that distinction is the whole point of this function.** The
 * scripted tutorial walks the rider into directions to demonstrate the planner, and the itinerary it
 * shows there is a bundled fixture — so a full-screen warning about the walking directions OTP builds
 * from open map data is both inapplicable and, worse, *spent*: dismissing it to get past the tutorial
 * would burn the one time the rider is made to read it, and their first real set of directions would
 * arrive with no disclosure at all. Holding the notice back leaves it owed, so it fires on the first
 * genuine trip plan instead.
 */
internal fun shouldShowSafetyNotice(pending: Boolean, demoActive: Boolean): Boolean = pending && !demoActive

/**
 * The one-time notice that stands between a rider and their first set of directions (#2218).
 *
 * OTP plans walking legs from open map data whose crosswalk, signal and sidewalk coverage is patchy —
 * a route really can send someone across a busy street where there is no safe crossing — while riders
 * arrive expecting parity with commercial map apps. Tuning OTP is the real fix; this is the honest
 * disclosure that has to hold in the meantime, and it is shown once rather than on every plan because
 * the per-itinerary
 * [DirectionsCautionBanner][org.onebusaway.android.ui.tripresults.DirectionsCautionBanner] is what
 * repeats it.
 *
 * The caller composes it only while directions has the focus, which covers *every* way in (the
 * drawer, the map long-press menu, a pinned-trip resume, a monitor notification): they all land on the
 * same [CurrentFocus.Directions][org.onebusaway.android.ui.home.CurrentFocus], so none of them can
 * slip past it.
 *
 * It is a full-screen [Dialog] rather than a sibling in the host's `Box` because it needs its own
 * window: that is what puts it unconditionally over the map, the form card *and* the results sheet,
 * and what lets it take Back ahead of the host's directions `BackHandler` without either having to
 * know about the other.
 *
 * **Back is not an acknowledgement.** It leaves directions instead ([onDecline]) and writes nothing,
 * so the notice returns on the next entry. There is no way through to the planner except the button,
 * but the rider is never trapped behind it either.
 *
 * Nor is the scripted tutorial: it is held back there rather than shown-and-dismissed, so it is still
 * owed afterwards — see [shouldShowSafetyNotice].
 */
@Composable
internal fun DirectionsSafetyNotice(onDecline: () -> Unit) {
    val context = LocalContext.current
    // Resolved once rather than per recomposition, matching the remember { …EntryPoint.get(…) }
    // pattern elsewhere.
    val prefs = remember { PreferencesEntryPoint.get(context) }
    // State rather than a bare read, because a preference isn't snapshot-observed and the button tap
    // needs something to recompose on. `remember`, not `rememberSaveable`: the preference is already
    // the durable record of this exact bit, and it reads its own writes synchronously, so re-seeding
    // from it — on a config change, or on the next entry into directions — is always right.
    var pending by remember { mutableStateOf(isSafetyNoticePending(prefs)) }
    // Collected rather than read once: the tutorial ends while the rider may still be in directions, and
    // the notice they are still owed has to appear the moment the demo plan stops standing in for a real
    // one.
    val demoMode = remember { DemoEntryPoint.get(context) }
    val demoActive by demoMode.active.collectAsStateWithLifecycle()
    if (!shouldShowSafetyNotice(pending, demoActive)) return

    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(
            // The content fills the window, so there is no outside to tap; pinned false anyway, so
            // the two answers stay the two the KDoc names even if the layout ever changes.
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
