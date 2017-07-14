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
    }

}
