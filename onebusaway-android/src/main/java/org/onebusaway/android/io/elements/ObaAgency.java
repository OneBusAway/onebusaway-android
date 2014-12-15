/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.io.elements;


/**
 * Interface that defines an Agency element
 * {@link http://code.google.com/p/onebusaway/wiki/OneBusAwayRestApi_AgencyElementV2}
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public interface ObaAgency extends ObaElement {

    /**
     * @return The name of the agency.
     */
    public String getName();

    /**
     * @return The URL of the agency's website.
     */
    public String getUrl();

    /**
     * @return The Timezone of the agency.
     */
    public String getTimezone();

    /**
     * @return The agency language. Can be empty.
     */
    public String getLang();

    /**
     * @return The agency's phone number. Can be empty.
     */
    public String getPhone();

    /**
     * @return Any legal disclaimer that transit agencies would like displayed
     * to users when using the agency's data in an application. Can be empty.
     */
    public String getDisclaimer();

    /**
     *
     * @return The agency's email address. Can be empty.
     */
    public String getEmail();
}
