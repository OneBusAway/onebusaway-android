/*
 * Copyright (C) 2010-2017 Brian Ferris (bdferris@onebusaway.org),
 * University of South Florida (sjbarbeau@gmail.com),
 * Microsoft Corporation
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.region.ObaRegionsTask;
import org.onebusaway.android.travelbehavior.TravelBehaviorManager;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorUtils;
import org.onebusaway.android.util.BackupUtils;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.PermissionUtils;
import org.onebusaway.android.util.ShowcaseViewUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import static org.onebusaway.android.util.PermissionUtils.RESTORE_BACKUP_PERMISSION_REQUEST;
import static org.onebusaway.android.util.PermissionUtils.SAVE_BACKUP_PERMISSION_REQUEST;
import static org.onebusaway.android.util.PermissionUtils.STORAGE_PERMISSIONS;

public class PreferencesActivity extends PreferenceActivity
        implements Preference.OnPreferenceClickListener, OnPreferenceChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener, ObaRegionsTask.Callback {

    private static final String TAG = "PreferencesActivity";

    public static final String SHOW_CHECK_REGION_DIALOG = ".checkRegionDialog";

    Preference mPreference;

    Preference mLeftHandMode;

    Preference mCustomApiUrlPref;

    Preference mCustomOtpApiUrlPref;

    Preference mAnalyticsPref;

    CheckBoxPreference mTravelBehaviorPref;

    Preference mTutorialPref;

    Preference mDonatePref;

    Preference mPoweredByObaPref;

    Preference mAboutPref;

    Preference mSaveBackup;

    Preference mRestoreBackup;

    boolean mAutoSelectInitialValue;

    boolean mOtpCustomAPIUrlChanged = false;
    //Save initial value so we can compare to current value in onDestroy()

    ListPreference preferredUnits;

    private FirebaseAnalytics mFirebaseAnalytics;

    @SuppressWarnings("deprecation")
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setProgressBarIndeterminate(true);

        addPreferencesFromResource(R.xml.preferences);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mPreference = findPreference(getString(R.string.preference_key_region));
        mPreference.setOnPreferenceClickListener(this);

        mLeftHandMode = findPreference(getString(R.string.preference_key_left_hand_mode));
        mLeftHandMode.setOnPreferenceChangeListener(this);

        mSaveBackup = findPreference(getString(R.string.preference_key_save_backup));
        mSaveBackup.setOnPreferenceClickListener(this);

        mRestoreBackup = findPreference(getString(R.string.preference_key_restore_backup));
        mRestoreBackup.setOnPreferenceClickListener(this);

        mCustomApiUrlPref = findPreference(getString(R.string.preference_key_oba_api_url));
        mCustomApiUrlPref.setOnPreferenceChangeListener(this);

        mCustomOtpApiUrlPref = findPreference(getString(R.string.preference_key_otp_api_url));
        mCustomOtpApiUrlPref.setOnPreferenceChangeListener(this);

        mAnalyticsPref = findPreference(getString(R.string.preferences_key_analytics));
        mAnalyticsPref.setOnPreferenceChangeListener(this);

        mTravelBehaviorPref = (CheckBoxPreference) findPreference(getString(R.string.preferences_key_travel_behavior));
        mTravelBehaviorPref.setOnPreferenceChangeListener(this);

        if (!TravelBehaviorUtils.isTravelBehaviorActiveInRegion() ||
                (!TravelBehaviorUtils.allowEnrollMoreParticipantsInStudy() &&
                        !TravelBehaviorUtils.isUserParticipatingInStudy())) {
            PreferenceCategory aboutCategory = (PreferenceCategory)
                    findPreference(getString(R.string.preferences_category_about));
            aboutCategory.removePreference(mTravelBehaviorPref);
        } else {
            mTravelBehaviorPref.setChecked(TravelBehaviorUtils.isUserParticipatingInStudy());
        }

        mTutorialPref = findPreference(getString(R.string.preference_key_tutorial));
        mTutorialPref.setOnPreferenceClickListener(this);

        mDonatePref = findPreference(getString(R.string.preferences_key_donate));
        mDonatePref.setOnPreferenceClickListener(this);

        mPoweredByObaPref = findPreference(getString(R.string.preferences_key_powered_by_oba));
        mPoweredByObaPref.setOnPreferenceClickListener(this);

        mAboutPref = findPreference(getString(R.string.preferences_key_about));
        mAboutPref.setOnPreferenceClickListener(this);

        SharedPreferences settings = Application.getPrefs();
        mAutoSelectInitialValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        preferredUnits = (ListPreference) findPreference(
                getString(R.string.preference_key_preferred_units));

        settings.registerOnSharedPreferenceChangeListener(this);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        // Hide any preferences that shouldn't be shown if the region is hard-coded via build flavor
        if (BuildConfig.USE_FIXED_REGION) {
            PreferenceCategory regionCategory = (PreferenceCategory)
                    findPreference(getString(R.string.preferences_category_location));
            regionCategory.removeAll();
            preferenceScreen.removePreference(regionCategory);
            PreferenceCategory advancedCategory = (PreferenceCategory)
                    findPreference(getString(R.string.preferences_category_advanced));
            Preference experimentalRegion = findPreference(
                    getString(R.string.preference_key_experimental_regions));
            advancedCategory.removePreference(experimentalRegion);
        }

        // If the Android version is Oreo (8.0) hide "Notification" preference
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getPreferenceScreen().removePreference(findPreference(getString(R.string.preference_key_notifications)));
        }

        // If the Android version is lower than Nougat (7.0) and equal to or above Pie (9.0) hide "Share trip logs" preference
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.N) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {
            getPreferenceScreen().removePreference(findPreference(getString(R.string.preferences_key_user_debugging_logs_category)));
        }

        // If its the OBA brand flavor, then show the "Donate" preference and hide "Powered by OBA"
        PreferenceCategory aboutCategory = (PreferenceCategory)
                findPreference(getString(R.string.preferences_category_about));
        if (BuildConfig.FLAVOR_brand.equalsIgnoreCase(BuildFlavorUtils.OBA_FLAVOR_BRAND)) {
            aboutCategory.removePreference(mPoweredByObaPref);
        } else {
            // Its not the OBA brand flavor, then hide the "Donate" preference and show "Powered by OBA"
            aboutCategory.removePreference(mDonatePref);
        }

        boolean showCheckRegionDialog = getIntent().getBooleanExtra(SHOW_CHECK_REGION_DIALOG, false);
        if (showCheckRegionDialog) {
            showCheckRegionDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        changePreferenceSummary(getString(R.string.preference_key_region));
        changePreferenceSummary(getString(R.string.preference_key_preferred_units));
        changePreferenceSummary(getString(R.string.preference_key_otp_api_url));

        // Remove preferences for notifications if no trip planning
        ObaRegion obaRegion = Application.get().getCurrentRegion();
        if (obaRegion != null && TextUtils.isEmpty(obaRegion.getOtpBaseUrl())) {
            PreferenceCategory notifications = (PreferenceCategory)
                    findPreference(getString(R.string.preference_key_notifications));
            Preference tripPlan = findPreference(
                    getString(R.string.preference_key_trip_plan_notifications));

            if (notifications != null && tripPlan != null) {
                notifications.removePreference(tripPlan);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        setupActionBar();
    }

    private void showCheckRegionDialog() {
        ObaRegion obaRegion = Application.get().getCurrentRegion();
        if (obaRegion == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.preference_region_dialog_title))
                .setMessage(getString(R.string.preference_region_dialog_message, obaRegion.getName()))
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
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
                mPreference.setSummary(Application.get().getCurrentRegion().getName());
                mCustomApiUrlPref
                        .setSummary(getString(R.string.preferences_oba_api_servername_summary));
                String customOtpApiUrl = Application.get().getCustomOtpApiUrl();
                if (!TextUtils.isEmpty(customOtpApiUrl)) {
                    mCustomOtpApiUrlPref.setSummary(customOtpApiUrl);
                } else {
                    mCustomOtpApiUrlPref
                            .setSummary(getString(R.string.preferences_otp_api_servername_summary));
                }
            } else {
                mPreference.setSummary(getString(R.string.preferences_region_summary_custom_api));
                mCustomApiUrlPref.setSummary(Application.get().getCustomApiUrl());
            }
        } else if (preferenceKey
                .equalsIgnoreCase(getString(R.string.preference_key_preferred_units))) {
            preferredUnits.setSummary(preferredUnits.getValue());
        } else if (preferenceKey
                .equalsIgnoreCase(getString(R.string.preference_key_otp_api_url))) {
            String customOtpApiUrl = Application.get().getCustomOtpApiUrl();
            if (!TextUtils.isEmpty(customOtpApiUrl)) {
                mCustomOtpApiUrlPref.setSummary(customOtpApiUrl);
            } else {
                mCustomOtpApiUrlPref.setSummary(
                        getString(R.string.preferences_otp_api_servername_summary));
            }
            Application.get().setUseOldOtpApiUrlVersion(false);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        Log.d(TAG, "preference - " + pref.getKey());
        if (pref.equals(mPreference)) {
            RegionsActivity.start(this);
        } else if (pref.equals(mTutorialPref)) {
            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                    getString(R.string.analytics_label_button_press_tutorial),
                    null);
            ShowcaseViewUtils.resetAllTutorials(this);
            NavHelp.goHome(this, true);
        } else if (pref.equals(mDonatePref)) {
            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                    getString(R.string.analytics_label_button_press_donate),
                    null);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donate_url)));
            startActivity(intent);
        } else if (pref.equals(mPoweredByObaPref)) {
            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                    getString(R.string.analytics_label_button_press_powered_by_oba),
                    null);
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.powered_by_oba_url)));
            startActivity(intent);
        } else if (pref.equals(mAboutPref)) {
            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                    getString(R.string.analytics_label_button_press_about),
                    null);
            AboutActivity.start(this);
        } else if (pref.equals(mSaveBackup)) {
            // SavePreference will get the click event but will ignore it if permissions haven't
            // been granted yet so we can handle permissions here
            maybeRequestPermissions(SAVE_BACKUP_PERMISSION_REQUEST);
        } else if (pref.equals(mRestoreBackup)){
            // RestorePreference will get the click event but will ignore it if permissions haven't
            // been granted yet so we can handle permissions here.
            maybeRequestPermissions(RESTORE_BACKUP_PERMISSION_REQUEST);
        }
        return true;
    }

    private void maybeRequestPermissions(int permissionRequest) {
        if (!PermissionUtils.hasGrantedPermissions(this, STORAGE_PERMISSIONS)) {
            // Request permissions from the user
            ActivityCompat.requestPermissions(this, STORAGE_PERMISSIONS, permissionRequest);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        int result = PackageManager.PERMISSION_DENIED;
        if (requestCode == SAVE_BACKUP_PERMISSION_REQUEST || requestCode == RESTORE_BACKUP_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                result = PackageManager.PERMISSION_GRANTED;
                // The first time the user grants permission, we have to explictly call the save
                // or restore utility method so that the save or restore is triggered after the permission is granted
                if (requestCode == SAVE_BACKUP_PERMISSION_REQUEST) {
                    BackupUtils.save(this);
                } else {
                    BackupUtils.restore(this);
                }
            } else {
                showStoragePermissionDialog(this, requestCode);
            }
        }
    }

    /**
     * Shows the dialog to explain why storage permissions are needed
     * @param activity Activity used to show the dialog
     * @param requestCode The requesting permission code (SAVE_BACKUP_PERMISSION_REQUEST or RESTORE_BACKUP_PERMISSION_REQUEST)
     */
    private void showStoragePermissionDialog(Activity activity, int requestCode) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.storage_permissions_title)
                .setMessage(R.string.storage_permissions_message)
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        (dialog, which) -> {
                            // Request permissions from the user
                            ActivityCompat
                                    .requestPermissions(activity, STORAGE_PERMISSIONS, requestCode);
                        }
                )
                .setNegativeButton(R.string.no_thanks,
                        (dialog, which) -> {
                            // No-op
                        }
                );
        builder.create().show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.equals(mCustomApiUrlPref) && newValue instanceof String) {
            String apiUrl = (String) newValue;

            if (!TextUtils.isEmpty(apiUrl)) {
                boolean validUrl = validateUrl(apiUrl);
                if (!validUrl) {
                    Toast.makeText(this, getString(R.string.custom_api_url_error),
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                //User entered a custom API Url, so set the region info to null
                Application.get().setCurrentRegion(null);
                Log.d(TAG, "User entered new API URL, set region to null.");
            } else {
                //User cleared the API URL preference value, so re-initialize regions
                Log.d(TAG, "User entered blank API URL, re-initializing regions...");
                NavHelp.goHome(this, false);
            }
        }
        if (preference.equals(mCustomOtpApiUrlPref) && newValue instanceof String) {
            String apiUrl = (String) newValue;

            if (!TextUtils.isEmpty(apiUrl)) {
                boolean validUrl = validateUrl(apiUrl);
                if (!validUrl) {
                    Toast.makeText(this, getString(R.string.custom_otp_api_url_error),
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            mOtpCustomAPIUrlChanged = true;
        } else if (preference.equals(mAnalyticsPref) && newValue instanceof Boolean) {
            Boolean isAnalyticsActive = (Boolean) newValue;
            //Report if the analytics turns off, just before shared preference changed
            ObaAnalytics.setSendAnonymousData(mFirebaseAnalytics, isAnalyticsActive);
        } else if (preference.equals(mTravelBehaviorPref) && newValue instanceof Boolean) {
            Boolean isTravelBehaviorActive = (Boolean) newValue;
            if(isTravelBehaviorActive) {
                new TravelBehaviorManager(this, getApplicationContext()).
                        registerTravelBehaviorParticipant(true);
            } else {
                new TravelBehaviorManager(this, getApplicationContext()).
                        stopCollectingData();
                TravelBehaviorManager.optOutUser();
                TravelBehaviorManager.optOutUserOnServer();
            }

        } else if (preference.equals(mLeftHandMode) && newValue instanceof Boolean) {
            Boolean isLeftHandEnabled = (Boolean) newValue;
            //Report if left handed mode is turned on, just before shared preference changed
            ObaAnalytics.setLeftHanded(mFirebaseAnalytics, isLeftHandEnabled);
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
        if ((currentValue && !mAutoSelectInitialValue)) {
            Log.d(TAG,
                    "User re-enabled auto-select regions pref, auto-selecting via Home Activity...");
            NavHelp.goHome(this, false);
        } else if (mOtpCustomAPIUrlChanged) {
            // Redraw the navigation drawer when custom otp api url is entered
            NavHelp.goHome(this, false);
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
            Log.d(TAG, "Experimental regions shared preference changed to " + experimentalServers);

            /*
            Force a refresh of the regions list, but don't using blocking progress dialog
            inside the ObaRegionsTask AsyncTask.
            We need to use our own Action Bar progress bar here so its linked to this activity,
            which will survive orientation changes.
            */
            setProgressBarIndeterminateVisibility(true);
            List<ObaRegionsTask.Callback> callbacks = new ArrayList<>();
            callbacks.add(this);
            ObaRegionsTask task = new ObaRegionsTask(this, callbacks, true, false);
            task.execute();

            // Wait to change the region preference description until the task callback
            //Analytics
            if (experimentalServers) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        getString(R.string.analytics_label_button_press_experimental_on),
                        null);
            } else {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        getString(R.string.analytics_label_button_press_experimental_off),
                        null);
            }
        } else if (key.equals(getString(R.string.preference_key_oba_api_url))) {
            // Change the region preference description to show we're not using a region
            changePreferenceSummary(key);
        } else if (key.equals(getString(R.string.preference_key_otp_api_url))) {
            // Change the otp url preference description
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_preferred_units))) {
            // Change the preferred units description
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_auto_select_region))) {
            //Analytics
            boolean autoSelect = settings
                    .getBoolean(getString(R.string.preference_key_auto_select_region), true);
            if (autoSelect) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        getString(R.string.analytics_label_button_press_auto),
                        null);
            } else {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        getString(R.string.analytics_label_button_press_manual),
                        null);
            }
        } else if (key.equalsIgnoreCase(getString(R.string.preferences_key_analytics))) {
            Boolean isAnalyticsActive = settings.getBoolean(Application.get().
                    getString(R.string.preferences_key_analytics), Boolean.TRUE);
            //Report if the analytics turns on, just after shared preference changed
            ObaAnalytics.setSendAnonymousData(mFirebaseAnalytics, isAnalyticsActive);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_arrival_info_style))) {
            // Change the arrival info description
            changePreferenceSummary(key);
        } else if (key.equalsIgnoreCase(getString(R.string.preference_key_show_negative_arrivals))) {
            boolean showDepartedBuses = settings.getBoolean(Application.get().
                    getString(R.string.preference_key_show_negative_arrivals), Boolean.FALSE);
            ObaAnalytics.setShowDepartedVehicles(mFirebaseAnalytics, showDepartedBuses);
        }
    }

    /**
     * Returns true if the provided apiUrl could be a valid URL, false if it could not
     *
     * @param apiUrl the URL to validate
     * @return true if the provided apiUrl could be a valid URL, false if it could not
     */
    private boolean validateUrl(String apiUrl) {
        try {
            // URI.parse() doesn't tell us if the scheme is missing, so use URL() instead (#126)
            URL url = new URL(apiUrl);
        } catch (MalformedURLException e) {
            // Assume HTTPS scheme if none is provided
            apiUrl = getString(R.string.https_prefix) + apiUrl;
        }
        return Patterns.WEB_URL.matcher(apiUrl).matches();
    }

    /**
     * Imitate Action Bar with back button - from http://stackoverflow.com/a/27455363/937715
     */
    private void setupActionBar() {
        Toolbar bar;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            LinearLayout root = (LinearLayout) findViewById(android.R.id.list).getParent()
                    .getParent().getParent();
            bar = (Toolbar) LayoutInflater.from(this)
                    .inflate(R.layout.settings_toolbar, root, false);
            root.addView(bar, 0); // insert at top
        } else {
            // For Gingerbread
            ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
            ListView content = (ListView) root.getChildAt(0);
            root.removeAllViews();

            bar = (Toolbar) LayoutInflater.from(this)
                    .inflate(R.layout.settings_toolbar, root, false);

            int height;
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
                height = TypedValue
                        .complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            } else {
                height = bar.getHeight();
            }
            content.setPadding(0, height, 0, 0);
            root.addView(content);
            root.addView(bar);
        }

        bar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
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
            NavHelp.goHome(this, false);
        }
    }
}
