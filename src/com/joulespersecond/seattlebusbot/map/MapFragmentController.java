/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com) and individual contributors.
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
package com.joulespersecond.seattlebusbot.map;

import com.google.android.maps.MapView;
import com.joulespersecond.oba.elements.ObaReferences;
import com.joulespersecond.oba.elements.ObaStop;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.view.View;

import java.util.List;

public interface MapFragmentController {
    /**
     * Controllers should make every attempt to communicate through
     * the Callback interface rather than accessing the MapFragment
     * directly, even if it means duplicating some functionality,
     * just to keep the separation between them clean.
     *
     * @author paulw
     *
     */
    interface FragmentCallback {
        // Used by the controller to tell the Fragment what to do.
        Activity getActivity();
        View getView();

        LoaderManager getLoaderManager();

        void showProgress(boolean show);

        String getMapMode();
        void setMapMode(String mode, Bundle args);

        MapView getMapView();

        void showStops(List<ObaStop> stops, ObaReferences refs);

        void setMyLocation();
        void notifyOutOfRange();
    }

    String getMode();

    void setState(Bundle args);
    void destroy();

    void onPause();
    void onResume();

    void onSaveInstanceState(Bundle outState);

    /**
     * Called when we have the user's location,
     * or when they explicitly said to go to their location.
     */
    void onLocation();

    /**
     * Called when we don't know the user's location.
     */
    void onNoLocation();
}
