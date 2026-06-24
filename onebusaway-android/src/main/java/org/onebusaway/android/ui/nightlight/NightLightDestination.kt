/*
 * Copyright 2013-2026 Colin McDonough, University of South Florida, Sean J. Barbeau,
 * Open Transit Software Foundation
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
package org.onebusaway.android.ui.nightlight

import org.onebusaway.android.ui.HomeActivity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.onebusaway.android.R
import org.onebusaway.android.ui.common.Shortcuts
import org.onebusaway.android.ui.compose.components.ObaTopAppBar
import org.onebusaway.android.ui.compose.findActivity
import org.onebusaway.android.ui.nav.NavRoutes
import org.onebusaway.android.util.PreferenceUtils

/** The dimmed "off" color between flashes (a dark scrim over the theme background). */
private val COLOR_DARK = Color(0xCC000000)

/** Amount of time the light is left on for a single flash, in milliseconds. */
private const val FLASH_TIME_ON = 75L

/** Amount of time between flashes, in milliseconds — two quick blinks then a beat. */
private val WAIT_TIMES = longArrayOf(100, 100, 400)

private const val PREFERENCE_SHOWED_DIALOG = "showed_night_light_dialog"

/**
 * The night-light NavHost destination: a flashing light riders show at night to flag
 * bus drivers. Re-hosts the former [NightLightLauncher]'s window-level concerns — keep-screen-on,
 * full brightness, portrait lock — on the single host activity for as long as this destination is on
 * screen (a [DisposableEffect] adds them on enter and restores them on exit), shows the one-time
 * epilepsy intro, and drives [NightLightScreen]. [onBack] pops the back stack.
 */
@Composable
fun NightLightRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val window = activity.window

    // Window/orientation concerns live as long as the destination is on screen.
    DisposableEffect(Unit) {
        val previousOrientation = activity.requestedOrientation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = previousOrientation
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    // Full brightness once the user has seen (now or previously) the epilepsy intro.
    var fullBrightness by remember { mutableStateOf(false) }
    LaunchedEffect(fullBrightness) {
        if (fullBrightness) {
            val lp = window.attributes
            lp.screenBrightness = 1.0f
            window.attributes = lp
        }
    }

    // One-time intro dialog (gated by a pref); "start" enables brightness, "cancel" leaves the screen.
    var introHandled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (introHandled) return@LaunchedEffect
        introHandled = true
        if (PreferenceUtils.getBoolean(PREFERENCE_SHOWED_DIALOG, false)) {
            fullBrightness = true
        } else {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.night_light_dialog_title)
                .setMessage(R.string.night_light_dialog_message)
                .setCancelable(false)
                .setPositiveButton(R.string.night_light_start) { _, _ ->
                    PreferenceUtils.saveBoolean(PREFERENCE_SHOWED_DIALOG, true)
                    fullBrightness = true
                }
                .setNegativeButton(R.string.night_light_cancel) { _, _ -> onBack() }
                .show()
        }
    }

    NightLightScreen(onBack = onBack, onCreateShortcut = {
        val shortcut = Shortcuts.makeShortcutInfo(
            activity,
            activity.getString(R.string.stop_info_option_night_light),
            HomeActivity.navIntent(activity, NavRoutes.NIGHT_LIGHT),
            R.drawable.ic_night_light
        )
        ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null)
    })
}

/**
 * The flashing screen: white / theme-color / white blinks with a pause between rounds, matching
 * the legacy flash thread. Tapping the screen pauses and resumes the flashing.
 */
@Composable
private fun NightLightScreen(onBack: () -> Unit, onCreateShortcut: () -> Unit) {
    // Remembered: NightLightScreen recomposes on every flash tick (~10x/sec) as displayColor changes.
    val themeColor = colorResource(R.color.theme_primary)
    val flashColors = remember(themeColor) { listOf(Color.White, themeColor, Color.White) }
    var flashing by remember { mutableStateOf(true) }
    // The single source of truth for the screen color: a flash color while on, the dark scrim while off.
    var displayColor by remember { mutableStateOf(COLOR_DARK) }

    // RESUMED-only flash loop, replacing the legacy background thread.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(flashing) {
        if (!flashing) {
            displayColor = COLOR_DARK
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var counter = 0
            while (isActive) {
                displayColor = flashColors[counter % flashColors.size]
                delay(FLASH_TIME_ON)
                displayColor = COLOR_DARK
                delay(WAIT_TIMES[counter % WAIT_TIMES.size])
                counter++
            }
        }
    }

    Scaffold(
        topBar = {
            ObaTopAppBar(stringResource(R.string.stop_info_option_night_light), onBack) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.night_light_create_shortcut)) },
                            onClick = {
                                expanded = false
                                onCreateShortcut()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(displayColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { flashing = !flashing }
        )
    }
}
