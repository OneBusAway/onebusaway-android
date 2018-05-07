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

import com.microsoft.embeddedsocial.sdk.INavigationDrawerHandler;

import org.onebusaway.android.R;
import org.onebusaway.android.ui.NavigationDrawerFragment;

import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;

public class SocialNavigationDrawerHandler implements INavigationDrawerHandler {
    private NavigationDrawerFragment fragment;

    @Override
    public Fragment getFragment() {
        fragment = new NavigationDrawerFragment();
        return fragment;
    }

    @Override
    public void setUp(int fragmentId, DrawerLayout drawerLayout) {
        fragment.setUp(fragmentId, drawerLayout);
    }

    @Override
    public int getBackgroundColor() {
        return R.color.navdrawer_background;
    }

    @Override
    public int getWidth() {
        return R.dimen.navigation_drawer_width;
    }

    @Override
    public boolean displayToolbar() {
        return true;
    }
}
