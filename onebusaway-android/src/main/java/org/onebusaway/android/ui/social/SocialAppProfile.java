/*
* Copyright (c) 2017 Microsoft Corporation
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
package org.onebusaway.android.ui.social;

import com.microsoft.embeddedsocial.sdk.ui.AppProfile;

import org.onebusaway.android.R;

public class SocialAppProfile implements AppProfile {
    @Override
    public int getName() {
        return R.string.app_name;
    }

    @Override
    public int getImage() {
        return R.mipmap.ic_launcher;
    }
}
