/*
 * Copyright (C) 2010-2015 Brian Ferris (bdferris@onebusaway.org), University of South Florida
 * and individual contributors
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

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.region.ObaRegionsTask;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;

public class PreferencesActivity extends PreferenceActivity
        implements Preference.OnPreferenceClickListener, OnPreferenceChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener, ObaRegionsTask.Callback {

    private static final String TAG = "PreferencesActivity";

    Preference regionPref;

    Preference customApiUrlPref;

    Preference analyticsPref;

    boolean autoSelectInitialValue;
    //Save initial value so we can compare to current value in onDestroy()

    ListPreference preferredUnits;

    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setProgressBarIndeterminate(true);

        addPreferencesFromResource(R.xml.preferences);

        regionPref = findPreference(getString(R.string.preference_key_region));
        regionPref.setOnPreferenceClickListener(this);

        customApiUrlPref = findPreference(getString(R.string.preference_key_oba_api_url));
        customApiUrlPref.setOnPreferenceChangeListener(this);

        analyticsPref = findPreference(getString(R.string.preferences_key_analytics));
        analyticsPref.setOnPreferenceChangeListener(this);

        SharedPreferences settings = Application.getPrefs();
        autoSelectInitialValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        preferredUnits = (ListPreference) findPreference(
                getString(R.string.preference_key_preferred_units));

        settings.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        changePreferenceSummary(getString(R.string.preference_key_region));
        changePreferenceSummary(getString(R.string.preference_key_preferred_units));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ObaAnalytics.reportActivityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * Changes the summary of a preference based on a given preference key
     *
     * @param preferenceKey preference key that triggers a change in summary
     */
    private void changePreferenceSummary(String preferenceKey) {
        // Change the current region summary and server API URL summary
        if (preferenceKey.equalsIgnoreCase(getString(R.string.preference_key_region))
                || preferenceKey.equalsIgnoreCase(getString(R.string.preference_key_oba_api_url))) {
            if (Application.get().getCurrentRegion() != null) {
                regionPref.setSummary(Application.get().getCurrentRegion().getName());
                customApiUrlPref
                        .setSummary(getString(R.string.preferences_oba_api_servername_summary));
            } else {
                regionPref.setSummary(getString(R.string.preferences_region_summary_custom_api));
                customApiUrlPref.setSummary(Application.get().getCustomApiUrl());
            }
        } else if (preferenceKey
                .equalsIgnoreCase(getString(R.string.preference_key_preferred_units))) {
            preferredUnits.setSummary(preferredUnits.getValue());
        }
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
        if (preference.equals(customApiUrlPref) && newValue instanceof String) {
            String apiUrl = (String) newValue;

            if (!TextUtils.isEmpty(apiUrl)) {
                //User entered a custom API Url, so set the region info to null
                Application.get().setCurrentRegion(null);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "User entered new API URL, set region to null.");
                }
            } else {
                //User cleared the API URL preference value, so re-initialize regions
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "User entered blank API URL, re-initializing regions...");
                }
                NavHelp.goHome(this);
            }
        } else if (preference.equals(analyticsPref) && newValue instanceof Boolean) {
            Boolean isAnalyticsActive = (Boolean) newValue;
            GoogleAnalytics.getInstance(this).setAppOptOut(!isAnalyticsActive);
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        SharedPreferences settings = Application.getPrefs();
        boolean currentValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        //If the use has selected to auto-select region, and the previous state of the setting was false, 
        //then run the auto-select by going to HomeFragment
        if (currentValue && !autoSelectInitialValue) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "User re-enabled auto-select regions pref, auto-selecting via Home Activity...");
            }
            NavHelp.goHome(this);
        }
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SharedPreferences settings = Application.getPrefs();
        // Listening to changes to a custom Preference doesn't seem to work, so we can listen to changes to the shared pref value instead
        if (key.equals(getString(R.string.preference_key_experimental_regions))) {
            boolean experimentalServers = settings
                    .getBoolean(getString(R.string.preference_key_experimental_regions), false);
            if (BuildConfig.DEBUG) {
                Log.d(TAG,
                        "Experimental regions shared preference changed to " + experimentalServers);
            }

            /*
            Force a refresh of the regions list, but don't using blocking progress dialog
            inside the ObaRegionsTask AsyncTask.
            We need to use our own Action Bar progress bar here so its linked to this activity,
            which will survive orientation changes.
            */
            setProgressBarIndeterminateVisibility(true);
            ObaRegionsTask task = new ObaRegionsTask(this, this, true, false);
            task.execute();

            // Wait to change the region preference description until the task callback

            //Analytics
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_button_press_experimental) + experimentalServers);
        } else if (key.equals(getString(R.string.preference_key_oba_api_url))) {
            // Change the region preference description to show we're not using a region
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_preferred_units))) {
            // Change the preferred units description
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_auto_select_region))) {
            //Analytics
            boolean autoSelect = settings
                    .getBoolean(getString(R.string.preference_key_auto_select_region), false);

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.UI_ACTION.toString(),
                    getString(R.string.analytics_action_button_press),
                    getString(R.string.analytics_label_button_press_auto) + autoSelect);
        }
    }

    //
    // Region Task Callback
    //
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        setProgressBarIndeterminateVisibility(false);

        if (currentRegionChanged) {
            // If region was auto-selected, show user the region we're using
            if (Application.getPrefs()
                    .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                    && Application.get().getCurrentRegion() != null) {
                Toast.makeText(this,
                        getString(R.string.region_region_found,
                                Application.get().getCurrentRegion().getName()),
                        Toast.LENGTH_LONG
                ).show();
            }

            // Update the preference summary to show the newly selected region
            changePreferenceSummary(getString(R.string.preference_key_region));

            // Since the current region was updated as a result of enabling/disabling experimental servers, go home
            NavHelp.goHome(this);
        }
    }
}
