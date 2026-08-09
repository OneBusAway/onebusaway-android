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
package org.onebusaway.android.map.render

import android.content.Context
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Contexts pinned to light and dark mode, for the marker tests that read colors out of qualified
 * resources (#2055).
 *
 * A test that used the bare target context would resolve whichever mode the emulator happens to be
 * running in, which is neither of the two things any of them mean to assert: the mode-comparing tests
 * would compare a mode against itself, and the mode-*independent* ones (the occupancy pips) would
 * silently start reading a white rim as pip ink on a dark device. Naming the mode makes both honest.
 */
internal fun lightContext(): Context = modeContext(Configuration.UI_MODE_NIGHT_NO)

internal fun darkContext(): Context = modeContext(Configuration.UI_MODE_NIGHT_YES)

private fun modeContext(nightMode: Int): Context {
    val base = InstrumentationRegistry.getInstrumentation().targetContext
    val configuration = Configuration(base.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
    }
    return base.createConfigurationContext(configuration)
}
