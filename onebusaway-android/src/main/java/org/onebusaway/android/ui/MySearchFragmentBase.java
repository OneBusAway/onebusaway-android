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
package org.onebusaway.android.ui;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

import org.onebusaway.android.R;
import org.onebusaway.android.util.LocationUtil;
import org.onebusaway.android.util.UIHelp;
import org.onebusaway.android.view.SearchView;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import java.util.Timer;
import java.util.TimerTask;

abstract class MySearchFragmentBase extends ListFragment
        implements SearchView.OnQueryTextListener, MyListConstants {
    //private static final String TAG = "MySearchFragmentBase";

    private Timer mSearchTimer;

    private SearchView mSearchView;

    private static final int DELAYED_SEARCH_TIMEOUT = 1000;

    /**
     * Google Location Services
     */
    LocationClient mLocationClient;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity())
                == ConnectionResult.SUCCESS) {
            LocationUtil.LocationServicesCallback locCallback
                    = new LocationUtil.LocationServicesCallback();
            mLocationClient = new LocationClient(getActivity(), locCallback, locCallback);
            mLocationClient.connect();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        registerForContextMenu(getListView());

        TextView empty = (TextView) getView().findViewById(android.R.id.empty);
        empty.setMovementMethod(LinkMovementMethod.getInstance());
        // Set empty text
        setEmptyText(getHintText());

        mSearchView.setQueryHint(getString(getEditBoxHintText()));
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(mOnQueryTextFocusChangeListener);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSearchView = (SearchView) getView().findViewById(R.id.search);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Make sure LocationClient is connected, if available
        if (mLocationClient != null && !mLocationClient.isConnected()) {
            mLocationClient.connect();
        }
    }

    @Override
    public void onStop() {
        // Tear down LocationClient
        if (mLocationClient != null && mLocationClient.isConnected()) {
            mLocationClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        cancelDelayedSearch();
        super.onDestroy();
    }

    @Override
    public void setEmptyText(CharSequence seq) {
        TextView empty = (TextView) getView().findViewById(R.id.internalEmpty);
        if (empty != null) {
            empty.setText(seq, BufferType.SPANNABLE);
        }
        empty = (TextView) getView().findViewById(android.R.id.empty);
        if (empty != null) {
            empty.setText(seq, BufferType.SPANNABLE);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        // Called when the action bar search text has changed.  Update
        // the search filter, and restart the loader to do a new query
        // with this filter.
        //Log.d(TAG, "new text: " + newText);
        cancelDelayedSearch();

        if (newText.length() == 0) {
            //Log.d(TAG, "CANCEL SEARCH");
            // Set hint text, etc.
            return true;
        }

        final String query = newText;
        final Runnable doSearch = new Runnable() {
            public void run() {
                doSearch(query);
            }
        };
        mSearchTimer = new Timer();
        mSearchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mSearchHandler.post(doSearch);
            }
        }, DELAYED_SEARCH_TIMEOUT);
        return true;
    }

    final Handler mSearchHandler = new Handler();

    @Override
    public boolean onQueryTextSubmit(String query) {
        // Don't care about this.
        //Log.d(TAG, "text submit: " + query);
        return true;
    }

    protected void cancelDelayedSearch() {
        if (mSearchTimer != null) {
            mSearchTimer.cancel();
            mSearchTimer = null;
        }
    }

    protected SearchView getSearchView() {
        return mSearchView;
    }

    /*
    private void doSearchFromText() {
        doSearch(mSearchText.getText());
        InputMethodManager imm =
            (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
    }
    */

    /**
     * Default implementation for creating a shortcut when in shortcut mode.
     *
     * @param name       The name of the shortcut.
     * @param destIntent The destination intent.
     */
    protected final void makeShortcut(String name, Intent destIntent) {
        final Intent shortcut = UIHelp.makeShortcut(getActivity(), name, destIntent);

        Activity activity = getActivity();
        activity.setResult(Activity.RESULT_OK, shortcut);
        activity.finish();
    }

    protected boolean isShortcutMode() {
        Activity act = getActivity();
        if (act instanceof MyTabActivityBase) {
            MyTabActivityBase base = (MyTabActivityBase) act;
            return base.isShortcutMode();
        }
        return false;
    }

    protected final Location getSearchCenter() {
        Activity act = getActivity();
        Location result = null;
        if (act instanceof MyTabActivityBase) {
            MyTabActivityBase base = (MyTabActivityBase) act;
            result = base.getSearchCenter();
        }
        if (result == null) {
            result = LocationUtil.getLocation(act, mLocationClient);
        }
        return result;
    }

    private final OnFocusChangeListener mOnQueryTextFocusChangeListener
            = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (hasFocus) {
                getActivity().getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }
    };


    /**
     * Tells the subclass to start a search
     */
    abstract protected void doSearch(String text);

    /**
     * @return The hint text for the search box.
     */
    abstract protected int getEditBoxHintText();

    /**
     * @return The minimum number of characters that need to be in the find
     * search box before an automatic search is performed.
     */
    abstract protected int getMinSearchLength();

    /**
     * This is called to set the hint text in the R.id.empty view.
     */
    abstract protected CharSequence getHintText();
}
