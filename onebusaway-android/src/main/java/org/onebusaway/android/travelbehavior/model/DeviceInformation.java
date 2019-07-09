/*
 * Copyright (C) 2019 University of South Florida
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
package org.onebusaway.android.travelbehavior.model;

import org.apache.commons.lang3.builder.HashCodeBuilder;

public class DeviceInformation {

    public String appVersion;

    public String deviceModel;

    public String sdkVersion;

    public Integer sdkVersionInt;

    public String googlePlayServicesApp;

    public Integer googlePlayServicesLib;

    public Long regionId;

    public Boolean isTalkBackEnabled;

    public DeviceInformation(String appVersion, String deviceModel, String sdkVersion,
                             Integer sdkVersionInt, String googlePlayServicesApp,
                             Integer googlePlayServicesLib, Long regionId, Boolean isTalkBackEnabled) {
        this.appVersion = appVersion;
        this.deviceModel = deviceModel;
        this.sdkVersion = sdkVersion;
        this.sdkVersionInt = sdkVersionInt;
        this.googlePlayServicesApp = googlePlayServicesApp;
        this.googlePlayServicesLib = googlePlayServicesLib;
        this.regionId = regionId;
        this.isTalkBackEnabled = isTalkBackEnabled;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(appVersion).append(deviceModel).append(sdkVersion)
                .append(sdkVersionInt).append(googlePlayServicesApp).append(googlePlayServicesLib)
                .append(regionId).append(isTalkBackEnabled).toHashCode();
    }
}
