/**
 * Copyright (C) 2017 Rodrigo Carvalho (carvalhorr@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agree   d to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.android.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

/**
 * Utility class to convert between the selected trip mode in the spinner and the trip mode code.
 */
public class TripModes {

    public static final int TRANSIT_AND_BIKE = 0;
    public static final int BUS_ONLY = 1;
    public static final int RAIL_ONLY = 2;
    public static final int BIKESHARE = 3;
    public static final int TRANSIT_ONLY = 4;

    /**
     * Return the trip mode code based on the selected label string resource id from the spinner
     * that shows the trip mode options.
     *
     * @param selection string resource id of the selected trip mode in the UI
     * @return corresponding trip mode code
     */
    public static int getTripModeCodeFromSelection(int selection) {
        switch (selection) {
            case R.string.transit_mode_transit_and_bikeshare:
                return TRANSIT_AND_BIKE;
            case R.string.transit_mode_transit_only:
                return TRANSIT_ONLY;
            case R.string.transit_mode_bus:
                return BUS_ONLY;
            case R.string.transit_mode_rail:
                return RAIL_ONLY;
            case R.string.transit_mode_bikeshare:
                return BIKESHARE;
        }
        return -1;

    }

    /**
     * Return the position of the selected mode in a 0 based index.
     * @param selectedCode the selected mode code
     * @return the position of the selected mode in the list of available modes
     */
    public static int getSpinnerPositionFromSeledctedCode(int selectedCode) {
        if (Application.isBikeshareEnabled()) {
            switch (selectedCode) {
                case TRANSIT_AND_BIKE:
                    return 0;
                case TRANSIT_ONLY:
                    return 1;
                case BUS_ONLY:
                    return 2;
                case RAIL_ONLY:
                    return 3;
                case BIKESHARE:
                    return 4;
            }
        } else {
            switch (selectedCode) {
                case TRANSIT_ONLY:
                    return 0;
                case BUS_ONLY:
                    return 1;
                case RAIL_ONLY:
                    return 2;
            }
        }
        return 0;
    }
}
