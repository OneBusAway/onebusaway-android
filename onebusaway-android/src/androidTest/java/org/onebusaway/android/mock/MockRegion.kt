package org.onebusaway.android.mock

import android.content.Context
import org.onebusaway.android.region.Region
import org.onebusaway.android.util.RegionUtils

object MockRegion {
    fun getTampa(context: Context): Region = region(context, RegionUtils.TAMPA_REGION_ID)

    fun getPugetSound(context: Context): Region = region(context, RegionUtils.PUGET_SOUND_REGION_ID)

    fun getAtlanta(context: Context): Region = region(context, RegionUtils.ATLANTA_REGION_ID)

    private fun region(context: Context, id: Int): Region = requireNotNull(RegionUtils.getRegionsFromResources(context).firstOrNull { it.id == id.toLong() })

    fun getRegionWithPathNoSeparator(context: Context) = testRegion("Test-RegionWithPathNoSeparator", "https://api.tampa.onebusaway.org/api")

    fun getRegionNoSeparator(context: Context) = testRegion("Test-RegionNoSeparator", "https://api.pugetsound.onebusaway.org")

    fun getRegionWithPort(context: Context) = testRegion("Test-RegionWithPort", "https://api.tampa.onebusaway.org:8088/api/")

    fun getRegionNoScheme(context: Context) = testRegion("Test-RegionNoScheme", "api.tampa.onebusaway.org/api/")

    fun getRegionWithHttps() = testRegion("Test-RegionWithHttps", "https://api.tampa.onebusaway.org/api/")

    fun getRegionWithHttpsAndPort() = testRegion("Test-RegionWithHttpsAndPort", "https://api.tampa.onebusaway.org:8443/api/")

    fun getRegionWithoutObaApis(context: Context) = testRegion("Test-RegionWithoutOBAApis", "https://api.tampa.onebusaway.org/api/", supportsApis = false)

    fun getInactiveRegion(context: Context) = testRegion("Test-InactiveRegion", "https://api.tampa.onebusaway.org/api/", active = false)

    private fun testRegion(name: String, baseUrl: String, active: Boolean = true, supportsApis: Boolean = true) = Region(
        id = 0,
        name = name,
        active = active,
        obaBaseUrl = baseUrl,
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
