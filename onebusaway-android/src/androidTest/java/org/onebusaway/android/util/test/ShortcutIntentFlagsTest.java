/*
 * Copyright (C) 2026 OneBusAway
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
package org.onebusaway.android.util.test;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.onebusaway.android.R;
import org.onebusaway.android.ui.HomeActivity;
import org.onebusaway.android.ui.common.Shortcuts;

import java.util.concurrent.atomic.AtomicReference;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Regression test for issue #1564 — when the app is already running in the background, tapping a
 * launcher shortcut brought the app's last state to the foreground instead of the shortcut's
 * destination. The documented pattern for launcher shortcuts is {@link
 * Intent#FLAG_ACTIVITY_NEW_TASK} combined with {@link Intent#FLAG_ACTIVITY_CLEAR_TASK} so that
 * tapping a shortcut always opens a fresh task rooted at the target activity.
 */
@RunWith(AndroidJUnit4.class)
public class ShortcutIntentFlagsTest {

    @Test
    public void makeShortcutInfo_setsNewTaskAndClearTaskFlags() {
        Instrumentation instr = InstrumentationRegistry.getInstrumentation();
        final Context themed = new ContextThemeWrapper(instr.getTargetContext(),
                R.style.Theme_OneBusAway_NoActionBar);

        Intent destIntent = new Intent(themed, HomeActivity.class);
        final AtomicReference<ShortcutInfoCompat> shortcutRef = new AtomicReference<>();

        instr.runOnMainSync(() -> shortcutRef.set(
                Shortcuts.makeShortcutInfo(themed, "test", destIntent, R.drawable.ic_drawer_star)));

        Intent shortcutIntent = shortcutRef.get().getIntent();
        int flags = shortcutIntent.getFlags();
        assertTrue("Shortcut intent must set FLAG_ACTIVITY_NEW_TASK so that the launcher opens it"
                + " in a fresh task",
                (flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue("Shortcut intent must set FLAG_ACTIVITY_CLEAR_TASK so that tapping the shortcut"
                + " replaces the app's existing back stack with the shortcut destination",
                (flags & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
        assertEquals("ShortcutInfoCompat requires the intent action to be set; without ACTION_VIEW"
                + " requestPinShortcut throws IllegalArgumentException",
                Intent.ACTION_VIEW, shortcutIntent.getAction());
    }
}
