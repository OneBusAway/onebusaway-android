package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

/**
 * Created by carvalhorr on 9/3/17.
 */

public class TripModes {

    public static final int TRANSIT_AND_BIKE_MODE = 0;
    public static final int BUS_ONLY_MODE = 1;
    public static final int RAIL_ONLY_MODE = 2;
    public static final int BIKESHARE_MODE = 3;
    public static final int TRANSIT_ONLY_MODE = 4;

    public static int getTripModeCodeFromSelection(int selection) {
        switch (selection) {
            case R.string.transit_mode_transit_and_bikeshare:
                return TRANSIT_AND_BIKE_MODE;
            case R.string.transit_mode_transit_only:
                return TRANSIT_ONLY_MODE;
            case R.string.transit_mode_bus:
                return BUS_ONLY_MODE;
            case R.string.transit_mode_rail:
                return RAIL_ONLY_MODE;
            case R.string.transit_mode_bikeshare:
                return BIKESHARE_MODE;
        }
        return -1;

    }

    public static int getSpinnerPositionFromSeledctedCode(int selectedCode) {
        if (Application.isBikeshareEnabled()) {
            switch (selectedCode) {
                case TRANSIT_AND_BIKE_MODE:
                    return 0;
                case TRANSIT_ONLY_MODE:
                    return 1;
                case BUS_ONLY_MODE:
                    return 2;
                case RAIL_ONLY_MODE:
                    return 3;
                case BIKESHARE_MODE:
                    return 4;
            }
        } else {
            switch (selectedCode) {
                case TRANSIT_ONLY_MODE:
                    return 0;
                case BUS_ONLY_MODE:
                    return 1;
                case RAIL_ONLY_MODE:
                    return 2;
            }
        }
        return 0;
    }
}
