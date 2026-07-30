package org.onebusaway.android.mock

import android.content.Context
import org.onebusaway.android.region.Region
import org.onebusaway.android.util.RegionUtils

object MockRegion {
    fun getTampa(context: Context): Region = region(context, RegionUtils.TAMPA_REGION_ID)

    fun getPugetSound(context: Context): Region = region(context, RegionUtils.PUGET_SOUND_REGION_ID)

    private fun region(context: Context, id: Int): Region = requireNotNull(RegionUtils.getRegionsFromResources(context).firstOrNull { it.id == id.toLong() })

    fun getRegionWithoutObaApis() = testRegion("Test-RegionWithoutOBAApis", supportsApis = false)

    fun getInactiveRegion() = testRegion("Test-InactiveRegion", active = false)

    private fun testRegion(name: String, active: Boolean = true, supportsApis: Boolean = true) = Region(
        id = 0,
        name = name,
        active = active,
        obaBaseUrl = "https://api.tampa.onebusaway.org/api/",
        bounds = arrayOf(Region.Bounds(27.976910500000002, -82.445851, 0.5424609999999994, 0.576357999999999)),
        language = "en_US",
        contactEmail = "test@test.org",
        supportsObaDiscoveryApis = supportsApis,
        supportsObaRealtimeApis = supportsApis,
        experimental = false,
        paymentAndroidAppId = "co.bytemark.hart",
        sidecarBaseUrl = "https://onebusaway.co"
    )
}
