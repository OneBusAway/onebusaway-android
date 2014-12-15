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

package org.onebusaway.android.report.open311.exceptions;

/**
 * Can be thrown, if base url is not passed as an argument
 * when initializing open311.
 * @author Cagri Cetin
 */
public class Open311NotInitializedException extends IllegalStateException {

    public Open311NotInitializedException() {
        super("Open311 should initialize with baseUrl.");
    }

    public Open311NotInitializedException(String message) {
        super(message);
    }
}

