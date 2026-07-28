package org.onebusaway.android.mock

import android.location.Location
import java.util.HashMap
import org.onebusaway.android.models.ObaRoute
import org.onebusaway.android.models.ObaStop
import org.onebusaway.android.models.WheelchairBoarding
import org.onebusaway.android.util.locationOf

object MockObaStop {
    fun getMockStop(): ObaStop = Stop26

    fun getMockRoutes(): HashMap<String, ObaRoute> = hashMapOf(Route1.id to Route1, Route5.id to Route5)

    private object Stop26 : ObaStop {
        override val stopCode = "26"
        override val name = "Nebraska Av @ Columbus Dr"
        override val location: Location get() = locationOf(latitude, longitude)
        override val latitude = 27.966904
        override val longitude = -82.451178
        override val direction = "N"
        override val locationType = ObaStop.LOCATION_STOP
        override val routeIds = arrayOf("Hillsborough Area Regional Transit_1", "Hillsborough Area Regional Transit_5")
        override val id = "Hillsborough Area Regional Transit_26"
        override val wheelchairBoarding = WheelchairBoarding.UNKNOWN
    }

    private abstract class BaseRoute : ObaRoute {
        override val description = ""
        override val type = ObaRoute.TYPE_BUS
        override val color: Int = 0
        override val textColor: Int = 0
        override val agencyId = "Hillsborough Area Regional Transit"
    }

    private object Route1 : BaseRoute() {
        override val shortName = "1"
        override val longName = "Florida Avenue"
        override val url = "https://www.gohart.org/routes/hart/01.html"
        override val id = "Hillsborough Area Regional Transit_1"
    }

    private object Route5 : BaseRoute() {
        override val shortName = "5"
        override val longName = "40th Street"
        override val url = "https://www.gohart.org/routes/hart/05.html"
        override val id = "Hillsborough Area Regional Transit_5"
    }
}
