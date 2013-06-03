/*
 * Copyright (C) 2010 Brian Ferris (bdferris@onebusaway.org)
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
package com.joulespersecond.seattlebusbot;

import com.actionbarsherlock.app.SherlockPreferenceActivity;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;

public class PreferencesActivity extends SherlockPreferenceActivity
            implements Preference.OnPreferenceClickListener, OnPreferenceChangeListener {
    
    Preference regionPref;
    Preference customApiUrlpref;
    
    // Soo... we can use SherlockPreferenceActivity to display the
    // action bar, but we can't use a PreferenceFragment?
    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelp.setupActionBar(getSupportActionBar());
        addPreferencesFromResource(R.xml.preferences);

        regionPref = findPreference(getString(R.string.preference_key_region));
        regionPref.setOnPreferenceClickListener(this);
        
        customApiUrlpref = findPreference(getString(R.string.preference_key_oba_api_url));
        customApiUrlpref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref.equals(regionPref)) {
            RegionsActivity.start(this);
        }        
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(customApiUrlpref) && newValue instanceof String) {
            String apiUrl = (String) newValue;
            if (BuildConfig.DEBUG) { Log.d("PreferenceActivity", "User set a custom API URL"); }
                        
            if(!TextUtils.isEmpty(apiUrl)){
                //User entered a custom API Url, so set the region info to null
                Application.get().setCurrentRegion(null);
                if (BuildConfig.DEBUG) { Log.d("PreferenceActivity", "Set region to null."); }
            }
        }  
        return true;
    }
}
