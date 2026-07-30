package org.onebusaway.android.util.test

import android.content.Intent
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.ui.HomeActivity
import org.onebusaway.android.ui.common.Shortcuts

@RunWith(AndroidJUnit4::class)
class ShortcutIntentFlagsTest {
    @Test fun makeShortcutInfoSetsNewTaskAndClearTaskFlags() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val themed = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_OneBusAway_NoActionBar)
        val destination = Intent(themed, HomeActivity::class.java)
        val shortcut = AtomicReference<androidx.core.content.pm.ShortcutInfoCompat>()
        instrumentation.runOnMainSync { shortcut.set(Shortcuts.makeShortcutInfo(themed, "test", destination, R.drawable.star)) }
        val intent = shortcut.get().intent
        assertTrue("Shortcut intent must set FLAG_ACTIVITY_NEW_TASK", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue("Shortcut intent must set FLAG_ACTIVITY_CLEAR_TASK", intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }
}
