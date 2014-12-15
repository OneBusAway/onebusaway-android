/*
* Copyright (C) 2014 University of South Florida (sjbarbeau@gmail.com)
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

package org.onebusaway.android.report.open311;


import org.onebusaway.android.report.open311.exceptions.Open311NotFoundException;
import org.onebusaway.android.report.open311.models.Open311Option;

import java.util.HashMap;
import java.util.Map;

/**
 * Open311 manager design to manage multiple endpoints in one application
 * @author Cagri Cetin
 */
public class Open311Manager {

    private static Map<String, Open311> open311Map = new HashMap<String, Open311>();

    public static Open311 getOpen311ByJurisdiction(String jurisdictionId) {

        if (open311Map.get(jurisdictionId) == null) {
            throw new Open311NotFoundException(jurisdictionId);
        } else {
            return open311Map.get(jurisdictionId);
        }
    }

    public static void initOpen311WithOption(Open311Option option) {
        Open311Factory open311Factory = new Open311Factory();
        open311Map.put(option.getJurisdiction(), open311Factory.getOpen311(option));
    }

    public static Boolean isOpen311Active(String jurisdictionId){
        return open311Map.get(jurisdictionId) != null;
    }
}
