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
package org.onebusaway.android.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.onebusaway.android.api.adapters.asArrivalData
import org.onebusaway.android.api.contract.ArrivalDeparture
import org.onebusaway.android.api.contract.ArrivalsForStop
import org.onebusaway.android.api.contract.EntryWithReferences
import org.onebusaway.android.api.contract.ObaEnvelope
import org.onebusaway.android.app.di.NetworkModule
import org.onebusaway.android.models.FrequencyWindow
import org.onebusaway.android.time.ServerTime
import org.onebusaway.android.ui.arrivals.ArrivalInfo

/**
 * Frequency-based (`exact_times=0`) trips through the arrivals path, wire to ETA (issue #430).
 *
 * The payload is the USF Bull Runner response from that issue — stop `203`, two headway-based routes
 * running every 10 minutes — decoded with the production [NetworkModule.provideJson] config, so the
 * nulls a frequency row carries (`tripStatus`, the `*Interval` fields) are decoded as the app sees
 * them. Neither row has a prediction: the server sends `predicted:false` and `0` for both predicted
 * times, and fills the scheduled times with the next headway instance.
 *
 * The rendered "Every N mins from/until" label needs a real `Context`, so it is covered by the
 * instrumented `UIUtilTest.frequencyStatusLabels` against the same payload.
 */
class FrequencyArrivalsTest {

    private val json = NetworkModule.provideJson()

    private val snapshot = json
        .decodeFromString<ObaEnvelope<EntryWithReferences<ArrivalsForStop>>>(BULL_RUNNER_203)
        .let { StopArrivals(checkNotNull(it.data), it.currentTime, minutesAfter = 65) }

    /**
     * Neither row is the collapsed no-prediction sentinel that ArrivalAdapters discards: only the
     * predicted times are 0, and the scheduled times are a real instance.
     */
    @Test
    fun `both frequency rows survive adaptation`() {
        assertEquals(
            listOf("USF Bull Runner_11", "USF Bull Runner_8"),
            snapshot.arrivals.map { it.tripId }
        )
    }

    /** The wire `frequency` object becomes a [FrequencyWindow] in the server clock domain. */
    @Test
    fun `the frequency window decodes onto the server clock`() {
        val window = FrequencyWindow(ServerTime(WINDOW_START), ServerTime(WINDOW_END), headway = 600L)

        snapshot.arrivals.forEach { assertEquals(window, it.frequency) }
    }

    /** With no prediction, the ETA uses the scheduled instance, not the frequency window. */
    @Test
    fun `an unpredicted frequency row counts down to its scheduled instance`() {
        val info = ArrivalInfo(null, snapshot.arrivals.first(), ServerTime(snapshot.currentTime), true)

        assertFalse(info.predicted)
        assertEquals(ServerTime(NEXT_INSTANCE), info.displayTime)
        assertEquals(5L, info.eta)
    }

    /** The negative case: a `"frequency": null` row adapts with no window. */
    @Test
    fun `a schedule-based row carries no frequency window`() {
        val row = json.decodeFromString<ArrivalDeparture>(
            """{ "routeId": "r", "tripId": "t", "stopId": "s", "stopSequence": 3,
                 "scheduledArrivalTime": $NEXT_INSTANCE, "scheduledDepartureTime": $NEXT_INSTANCE,
                 "frequency": null }"""
        )

        assertNull(checkNotNull(row.asArrivalData(directionId = null)).frequency)
    }

    private companion object {
        /** 2016-02-29 07:00 EST — Bull Runner service starts. */
        const val WINDOW_START = 1_456_747_200_000L

        /** 2016-03-01 00:00 EST — service ends. */
        const val WINDOW_END = 1_456_808_400_000L

        /** The scheduled instance both rows report: five minutes after the response's currentTime. */
        const val NEXT_INSTANCE = 1_456_780_382_378L

        /** USF Bull Runner stop 203, as captured in issue #430 (verbatim). */
        val BULL_RUNNER_203 = """
            {"currentTime":1456780082393,"text":"OK","data":{"references":{"stops":[{"id":"USF Bull Runner_203","lon":-82.4161291122,"direction":"","locationType":0,"name":"Center for Transportation Research","wheelchairBoarding":"UNKNOWN","routeIds":["USF Bull Runner_D","USF Bull Runner_E"],"code":"203","lat":28.0580728714},{"id":"USF Bull Runner_204","lon":-82.4157978594,"direction":"","locationType":0,"name":"Research & Development","wheelchairBoarding":"UNKNOWN","routeIds":["USF Bull Runner_A","USF Bull Runner_D"],"code":"204","lat":28.0577829169},{"id":"Hillsborough Area Regional Transit_3109","lon":-82.415793,"direction":"E","locationType":0,"name":"Alumni Dr @ Leroy Collins @ Eng Bldg","wheelchairBoarding":"UNKNOWN","routeIds":["Hillsborough Area Regional Transit_5"],"code":"3109","lat":28.0578},{"id":"Hillsborough Area Regional Transit_3105","lon":-82.416532,"direction":"W","locationType":0,"name":"Alumni Dr @ Beard","wheelchairBoarding":"UNKNOWN","routeIds":["Hillsborough Area Regional Transit_5"],"code":"3105","lat":28.058051}],"situations":[],"trips":[{"id":"USF Bull Runner_11","shapeId":"USF Bull Runner_4","tripShortName":"","directionId":"","serviceId":"USF Bull Runner_Mo","blockId":"USF Bull Runner_11","routeShortName":"","tripHeadsign":"Gold Campus Loop","routeId":"USF Bull Runner_E","timeZone":""},{"id":"USF Bull Runner_8","shapeId":"USF Bull Runner_3","tripShortName":"","directionId":"","serviceId":"USF Bull Runner_Mo","blockId":"USF Bull Runner_8","routeShortName":"","tripHeadsign":"Red Off-Campus West","routeId":"USF Bull Runner_D","timeZone":""}],"routes":[{"id":"USF Bull Runner_D","textColor":"FFFFFF","color":"F70505","description":"","longName":"Red Off-Campus West","shortName":"D","type":3,"agencyId":"USF Bull Runner","url":"http://usfweb2.usf.edu/parking_services/BullRunnerRoutesStops.htm"},{"id":"USF Bull Runner_E","textColor":"FFFFFF","color":"D4BA13","description":"","longName":"Gold Campus Loop","shortName":"E","type":3,"agencyId":"USF Bull Runner","url":"http://usfweb2.usf.edu/parking_services/BullRunnerRoutesStops.htm"},{"id":"USF Bull Runner_A","textColor":"FFFFFF","color":"00573C","description":"","longName":"Green Campus Loop","shortName":"A","type":3,"agencyId":"USF Bull Runner","url":"http://usfweb2.usf.edu/parking_services/BullRunnerRoutesStops.htm"},{"id":"Hillsborough Area Regional Transit_5","textColor":"FFFFFF","color":"09346D","description":"","longName":"40th Street","shortName":"5","type":3,"agencyId":"Hillsborough Area Regional Transit","url":"http://www.gohart.org/Pages/maps-schedules.aspx"}],"agencies":[{"id":"USF Bull Runner","privateService":false,"phone":"","timezone":"America/New_York","disclaimer":"","name":"USF Bull Runner","lang":"en","url":"http://www.usf.edu/administrative-services/parking"},{"id":"Hillsborough Area Regional Transit","privateService":false,"phone":"813-254-4278","timezone":"America/New_York","disclaimer":"","name":"Hillsborough Area Regional Transit","lang":"en","url":"http://www.gohart.org"}]},"entry":{"situationIds":[],"stopId":"USF Bull Runner_203","arrivalsAndDepartures":[{"serviceDate":1456722000000,"numberOfStopsAway":0,"vehicleId":"","lastUpdateTime":0,"routeId":"USF Bull Runner_E","frequency":{"startTime":1456747200000,"headway":600,"endTime":1456808400000},"stopSequence":13,"scheduledDepartureInterval":null,"routeShortName":"E","arrivalEnabled":true,"distanceFromStop":0,"scheduledArrivalTime":1456780382378,"status":"default","tripId":"USF Bull Runner_11","routeLongName":"Gold Campus Loop","tripHeadsign":"Gold Campus Loop","predicted":false,"predictedArrivalTime":0,"departureEnabled":true,"scheduledArrivalInterval":null,"predictedDepartureTime":0,"situationIds":[],"stopId":"USF Bull Runner_203","tripStatus":null,"scheduledDepartureTime":1456780382378,"blockTripSequence":0,"predictedArrivalInterval":null,"predictedDepartureInterval":null},{"serviceDate":1456722000000,"numberOfStopsAway":0,"vehicleId":"","lastUpdateTime":0,"routeId":"USF Bull Runner_D","frequency":{"startTime":1456747200000,"headway":600,"endTime":1456808400000},"stopSequence":18,"scheduledDepartureInterval":null,"routeShortName":"D","arrivalEnabled":true,"distanceFromStop":0,"scheduledArrivalTime":1456780382378,"status":"default","tripId":"USF Bull Runner_8","routeLongName":"Red Off-Campus West","tripHeadsign":"Red Off-Campus West","predicted":false,"predictedArrivalTime":0,"departureEnabled":true,"scheduledArrivalInterval":null,"predictedDepartureTime":0,"situationIds":[],"stopId":"USF Bull Runner_203","tripStatus":null,"scheduledDepartureTime":1456780382378,"blockTripSequence":0,"predictedArrivalInterval":null,"predictedDepartureInterval":null}],"nearbyStopIds":["USF Bull Runner_204","Hillsborough Area Regional Transit_3109","Hillsborough Area Regional Transit_3105"]}},"code":200,"version":2}
        """.trimIndent()
    }
}
