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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;

public class PreferencesActivity extends SherlockPreferenceActivity
            implements Preference.OnPreferenceClickListener, OnPreferenceChangeListener {
    private static final String TAG = "PreferencesActivity";
    
    Preference regionPref;
    Preference customApiUrlpref;
    Preference autoSelectRegion;
    
    boolean autoSelectInitialValue;  //Save initial value so we can compare to current value in onDestroy()
    
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
                                
        SharedPreferences settings = Application.getPrefs();        
        autoSelectInitialValue = settings.getBoolean(getString(R.string.preference_key_auto_select_region), true);         
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
                                    
            if(!TextUtils.isEmpty(apiUrl)){
                //User entered a custom API Url, so set the region info to null
                Application.get().setCurrentRegion(null);
                if (BuildConfig.DEBUG) { Log.d(TAG, "User entered new API URL, set region to null."); }
            }else{
                //User cleared the API URL preference value, so re-initialize regions
                if (BuildConfig.DEBUG) { Log.d(TAG, "User entered blank API URL, re-initializing regions..."); }
                NavHelp.goHome(this);
            }
        }  
        return true;
    }
    
    @Override
    protected void onDestroy() {
        SharedPreferences settings = Application.getPrefs();        
        boolean currentValue = settings.getBoolean(getString(R.string.preference_key_auto_select_region), true);
        
        //If the use has selected to auto-select region, and the previous state of the setting was false, 
        //then run the auto-select by going to HomeActivity
        if (currentValue && !autoSelectInitialValue) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "User re-enabled auto-select regions pref, auto-selecting via Home Activity..."); }
            NavHelp.goHome(this);
        }        
        super.onDestroy();
    }
}
