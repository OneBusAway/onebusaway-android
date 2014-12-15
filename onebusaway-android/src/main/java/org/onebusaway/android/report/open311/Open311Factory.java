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

import org.onebusaway.android.report.open311.constants.Open311Type;
import org.onebusaway.android.report.open311.exceptions.Open311NotInitializedException;
import org.onebusaway.android.report.open311.models.Open311Option;

/**
 * Creates open311 instances with different configurations
 * @author Cagri Cetin
 */
public class Open311Factory {

    public Open311 getOpen311(Open311Option open311Option){

        if(open311Option.getBaseUrl() == null){
            throw new Open311NotInitializedException();
        }else if(open311Option.getBaseUrl().contains(Open311Type.SEECLICKFIX.toString().toLowerCase())){
            open311Option.setOpen311Type(Open311Type.SEECLICKFIX);
            return new Open311(open311Option);
        }else {
            open311Option.setOpen311Type(Open311Type.DEFAULT);
            return new Open311(open311Option);
        }
    }
}
