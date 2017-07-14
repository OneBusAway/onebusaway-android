package org.onebusaway.android.io.test;

import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.bike.OtpBikeStationRequest;
import org.onebusaway.android.io.request.bike.OtpBikeStationResponse;
import org.onebusaway.android.mock.MockRegion;
import org.opentripplanner.routing.bike_rental.BikeRentalStation;

/**
 * Created by carvalhorr on 7/14/17.
 */
public class BikeStationRequestTest extends ObaTestCase {

    public void testGetBikeStationsTampa() {
        Application.get().setCurrentRegion(MockRegion.getTampa(getContext()), false);
        OtpBikeStationRequest request = OtpBikeStationRequest.newRequest(getContext(), null, null);
        OtpBikeStationResponse response = request.call();
        assertNotNull(response);
        assertNotNull(response.stations);
        assertEquals(133, response.stations.size());
        for (BikeRentalStation station: response.stations) {
            assertNotNull(station.name);
        }

        assertFirstStationIsCorrect(response.stations.get(0));

    }

    private void assertFirstStationIsCorrect(BikeRentalStation station) {
        double precision = 0.000001d;
        assertEquals("\"bike_3566\"", station.id);
        assertEquals("B-1165", station.name);
        assertEquals(-82.40730666666667d, station.x, precision);
        assertEquals(28.066505d, station.y, precision);
        assertEquals(1, station.bikesAvailable);
        assertEquals(0, station.spacesAvailable);
        assertEquals(false, station.allowDropoff);
        assertEquals(true, station.isFloatingBike);
        assertEquals(true, station.realTimeData);
    }

}
