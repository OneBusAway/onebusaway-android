package org.onebusaway.android.api.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.onebusaway.android.R
import org.onebusaway.android.app.di.PreferencesEntryPoint
import org.onebusaway.android.app.di.RegionEntryPoint
import org.onebusaway.android.region.Region

@RunWith(AndroidJUnit4::class)
abstract class ObaTestCase {
    private var oldRegion: Region? = null
    private var oldCustomApiUrl: String? = null

    @Before
    fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.setTheme(R.style.Theme_OneBusAway)
        oldRegion = RegionEntryPoint.get(context).currentRegion()
        oldCustomApiUrl = if (oldRegion == null) PreferencesEntryPoint.get(context).getString(R.string.preference_key_oba_api_url, null) else null
        PreferencesEntryPoint.get(context).setString(R.string.preference_key_oba_api_url, "api.pugetsound.onebusaway.org")
    }

    @After
    fun after() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        when {
            oldRegion != null -> RegionEntryPoint.get(context).applyRegion(oldRegion, true)
            oldCustomApiUrl != null -> PreferencesEntryPoint.get(context).setString(R.string.preference_key_oba_api_url, oldCustomApiUrl)
            else -> {
                PreferencesEntryPoint.get(context).setString(R.string.preference_key_oba_api_url, null)
                RegionEntryPoint.get(context).applyRegion(null, true)
            }
        }
    }
}
