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

import static org.onebusaway.android.util.UIUtils.setAppTheme;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.onebusaway.android.BuildConfig;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.PlausibleAnalytics;
import org.onebusaway.android.io.elements.ObaRegion;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.region.ObaRegionsTask;
import org.onebusaway.android.travelbehavior.io.coroutines.FirebaseDataPusher;
import org.onebusaway.android.util.BackupUtils;
import org.onebusaway.android.util.BuildFlavorUtils;
import org.onebusaway.android.util.ShowcaseViewUtils;
import org.onebusaway.android.util.UIUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        ObaRegionsTask.Callback {

    private static final String TAG = "SettingsActivity";

    public static final String SHOW_CHECK_REGION_DIALOG = ".checkRegionDialog";

    public static final int REQUEST_CODE_RESTORE_BACKUP = 1234;
    public static final int REQUEST_CODE_SAVE_BACKUP = 1199;

    boolean mAutoSelectInitialValue;
    boolean mOtpCustomAPIUrlChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        UIUtils.setupActionBar(this);

        setTitle(R.string.navdrawer_item_settings);
        SharedPreferences settings = Application.getPrefs();
        mAutoSelectInitialValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }

        boolean showCheckRegionDialog = getIntent()
                .getBooleanExtra(SHOW_CHECK_REGION_DIALOG, false);
        if (showCheckRegionDialog) {
            showCheckRegionDialog();
        }

        onAddCustomRegion();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                             @NonNull Preference pref) {
        String fragmentClass = pref.getFragment();
        if (fragmentClass == null) return false;

        Fragment fragment = getSupportFragmentManager().getFragmentFactory()
                .instantiate(getClassLoader(), fragmentClass);
        fragment.setArguments(pref.getExtras());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        SharedPreferences settings = Application.getPrefs();
        boolean currentValue = settings
                .getBoolean(getString(R.string.preference_key_auto_select_region), true);

        if (currentValue && !mAutoSelectInitialValue) {
            Log.d(TAG,
                    "User re-enabled auto-select regions pref, auto-selecting via Home Activity...");
            NavHelp.goHome(this, false);
        } else if (mOtpCustomAPIUrlChanged) {
            NavHelp.goHome(this, false);
        }

        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri != null) {
            if (requestCode == REQUEST_CODE_RESTORE_BACKUP) {
                BackupUtils.restore(this, uri);
            } else if (requestCode == REQUEST_CODE_SAVE_BACKUP) {
                BackupUtils.save(this, uri);
            }
        }
    }

    @Override
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        if (currentRegionChanged) {
            Application.get().setUseOldOtpApiUrlVersion(false);
            if (Application.getPrefs()
                    .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                    && Application.get().getCurrentRegion() != null) {
                Toast.makeText(this,
                        getString(R.string.region_region_found,
                                Application.get().getCurrentRegion().getName()),
                        Toast.LENGTH_LONG
                ).show();
            }
            NavHelp.goHome(this, false);
        }
    }

    void setOtpCustomAPIUrlChanged(boolean changed) {
        mOtpCustomAPIUrlChanged = changed;
    }

    private void showCheckRegionDialog() {
        ObaRegion obaRegion = Application.get().getCurrentRegion();
        if (obaRegion == null) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.preference_region_dialog_title))
                .setMessage(getString(R.string.preference_region_dialog_message,
                        obaRegion.getName()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {})
                .show();
    }

    private void onAddCustomRegion() {
        Uri deepLink = getIntent().getData();
        if (deepLink == null) return;

        String obaCustomUrl = deepLink.getQueryParameter("oba-url");
        String otpCustomUrl = deepLink.getQueryParameter("otp-url");

        if (obaCustomUrl != null && validateUrl(obaCustomUrl)) {
            Application.get().setCustomApiUrl(obaCustomUrl);
            Application.get().setCurrentRegion(null);
        }

        if (otpCustomUrl != null && validateUrl(otpCustomUrl)) {
            Application.get().setCustomOtpApiUrl(otpCustomUrl);
        }
        Intent i = new Intent(this, HomeActivity.class);
        startActivity(i);
        finish();
    }

    static boolean validateUrl(String apiUrl) {
        if (!apiUrl.startsWith("http")) {
            apiUrl = "https://" + apiUrl;
        }
        try {
            URL url = new URL(apiUrl);
            if (url.getHost().equals("localhost")) {
                return true;
            }
            return Patterns.WEB_URL.matcher(apiUrl).matches();
        } catch (MalformedURLException e) {
            return false;
        }
    }

    static void setIconSpaceReservedRecursive(PreferenceGroup group, boolean reserved) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            pref.setIconSpaceReserved(reserved);
            if (pref instanceof PreferenceGroup) {
                setIconSpaceReservedRecursive((PreferenceGroup) pref, reserved);
            }
        }
    }

    // ========================================================================
    // Main settings fragment
    // ========================================================================
    public static class SettingsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceClickListener,
            Preference.OnPreferenceChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener {

        private static final int REQUEST_CODE_RINGTONE = 2000;

        private Preference mRegionPref;
        private Preference mLeftHandMode;
        private Preference mAnalyticsPref;
        private Preference mHideAlertsPref;
        private Preference mTutorialPref;
        private Preference mDonatePref;
        private Preference mPoweredByObaPref;
        private Preference mAboutPref;
        private Preference mSaveBackup;
        private Preference mRestoreBackup;
        private Preference mRingtonePref;
        private ListPreference mPreferredUnits;
        private ListPreference mPreferredTempUnits;
        private ListPreference mThemePref;
        private ListPreference mMapMode;
        private FirebaseAnalytics mFirebaseAnalytics;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());

            mRegionPref = findPreference(getString(R.string.preference_key_region));
            mRegionPref.setOnPreferenceClickListener(this);

            mLeftHandMode = findPreference(getString(R.string.preference_key_left_hand_mode));
            mLeftHandMode.setOnPreferenceChangeListener(this);

            mSaveBackup = findPreference(getString(R.string.preference_key_save_backup));
            mSaveBackup.setOnPreferenceClickListener(this);

            mRestoreBackup = findPreference(getString(R.string.preference_key_restore_backup));
            mRestoreBackup.setOnPreferenceClickListener(this);

            mAnalyticsPref = findPreference(getString(R.string.preferences_key_analytics));
            mAnalyticsPref.setOnPreferenceChangeListener(this);

            mHideAlertsPref = findPreference(getString(R.string.preference_key_hide_alerts));
            mHideAlertsPref.setOnPreferenceChangeListener(this);

            mTutorialPref = findPreference(getString(R.string.preference_key_tutorial));
            mTutorialPref.setOnPreferenceClickListener(this);

            mDonatePref = findPreference(getString(R.string.preferences_key_donate));
            mDonatePref.setOnPreferenceClickListener(this);

            mPoweredByObaPref = findPreference(getString(R.string.preferences_key_powered_by_oba));
            mPoweredByObaPref.setOnPreferenceClickListener(this);

            mAboutPref = findPreference(getString(R.string.preferences_key_about));
            mAboutPref.setOnPreferenceClickListener(this);

            mMapMode = findPreference(getString(R.string.preference_key_map_mode));
            mMapMode.setOnPreferenceChangeListener(this);

            mPreferredUnits = findPreference(getString(R.string.preference_key_preferred_units));

            mPreferredTempUnits = findPreference(
                    getString(R.string.preference_key_preferred_temperature_units));

            mThemePref = findPreference(getString(R.string.preference_key_app_theme));
            mThemePref.setOnPreferenceChangeListener(this);

            mRingtonePref = findPreference(getString(R.string.preference_key_notification_sound));
            if (mRingtonePref != null) {
                mRingtonePref.setOnPreferenceClickListener(this);
            }

            PreferenceScreen preferenceScreen = getPreferenceScreen();

            if (BuildConfig.USE_FIXED_REGION) {
                PreferenceCategory regionCategory = findPreference(
                        getString(R.string.preferences_category_location));
                if (regionCategory != null) {
                    regionCategory.removeAll();
                    preferenceScreen.removePreference(regionCategory);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Preference notifications = findPreference(
                        getString(R.string.preference_key_notifications));
                if (notifications != null) {
                    preferenceScreen.removePreference(notifications);
                }
            }

            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {
                Preference logsCategory = findPreference(
                        getString(R.string.preferences_key_user_debugging_logs_category));
                if (logsCategory != null) {
                    preferenceScreen.removePreference(logsCategory);
                }
            }

            PreferenceCategory aboutCategory = findPreference(
                    getString(R.string.preferences_category_about));
            if (aboutCategory != null) {
                if (BuildFlavorUtils.isOBABuildFlavor()) {
                    aboutCategory.removePreference(mPoweredByObaPref);
                } else {
                    aboutCategory.removePreference(mDonatePref);
                }
            }

            updateBrandedPreferenceSummaries();
            setIconSpaceReservedRecursive(getPreferenceScreen(), false);
        }

        @Override
        public void onResume() {
            super.onResume();
            Application.getPrefs().registerOnSharedPreferenceChangeListener(this);

            changePreferenceSummary(getString(R.string.preference_key_region));
            changePreferenceSummary(getString(R.string.preference_key_preferred_units));
            changePreferenceSummary(getString(R.string.preference_key_preferred_temperature_units));
            changePreferenceSummary(getString(R.string.preference_key_app_theme));
            changePreferenceSummary(getString(R.string.preference_key_map_mode));

            ObaRegion obaRegion = Application.get().getCurrentRegion();
            if (obaRegion != null && TextUtils.isEmpty(obaRegion.getOtpBaseUrl())) {
                PreferenceCategory notifications = findPreference(
                        getString(R.string.preference_key_notifications));
                Preference tripPlan = findPreference(
                        getString(R.string.preference_key_trip_plan_notifications));
                if (notifications != null && tripPlan != null) {
                    notifications.removePreference(tripPlan);
                }
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            Application.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceClick(@NonNull Preference pref) {
            String key = pref.getKey();
            Log.d(TAG, "preference - " + key);

            if (pref.equals(mRegionPref)) {
                RegionsActivity.start(requireActivity());
            } else if (pref.equals(mTutorialPref)) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        Application.get().getPlausibleInstance(),
                        PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                        getString(R.string.analytics_label_button_press_tutorial),
                        null);
                ShowcaseViewUtils.resetAllTutorials(requireContext());
                NavHelp.goHome(requireActivity(), true);
            } else if (pref.equals(mDonatePref)) {
                startActivity(Application.getDonationsManager().buildOpenDonationsPageIntent());
            } else if (pref.equals(mPoweredByObaPref)) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        Application.get().getPlausibleInstance(),
                        PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                        getString(R.string.analytics_label_button_press_powered_by_oba),
                        null);
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.powered_by_oba_url)));
                startActivity(intent);
            } else if (pref.equals(mAboutPref)) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                        Application.get().getPlausibleInstance(),
                        PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                        getString(R.string.analytics_label_button_press_about),
                        null);
                AboutActivity.start(requireActivity());
            } else if (pref.equals(mSaveBackup)) {
                BackupUtils.createBackupFile(requireActivity());
            } else if (pref.equals(mRestoreBackup)) {
                BackupUtils.selectBackupFile(requireActivity());
            } else if (pref.equals(mRingtonePref)) {
                launchRingtonePicker();
            }
            return true;
        }

        @Override
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (preference instanceof ListPreference) {
                // This ensures the popup uses the M3 Alert Dialog Builder
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(preference.getTitle())
                        .setSingleChoiceItems(
                                ((ListPreference) preference).getEntries(),
                                ((ListPreference) preference).findIndexOfValue(((ListPreference) preference).getValue()),
                                (dialog, which) -> {
                                    String value = ((ListPreference) preference).getEntryValues()[which].toString();
                                    if (preference.callChangeListener(value)) {
                                        ((ListPreference) preference).setValue(value);
                                    }
                                    dialog.dismiss();
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                super.onDisplayPreferenceDialog(preference);
            }
        }

        @SuppressWarnings("deprecation")
        private void launchRingtonePicker() {
            String existingValue = Application.getPrefs().getString(
                    getString(R.string.preference_key_notification_sound), null);
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (existingValue != null) {
                if (existingValue.isEmpty()) {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri) null);
                } else {
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            Uri.parse(existingValue));
                }
            }
            startActivityForResult(intent, REQUEST_CODE_RINGTONE);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REQUEST_CODE_RINGTONE && resultCode == Activity.RESULT_OK && data != null) {
                Uri ringtoneUri = data.getParcelableExtra(
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                String value = ringtoneUri != null ? ringtoneUri.toString() : "";
                Application.getPrefs().edit()
                        .putString(getString(R.string.preference_key_notification_sound), value)
                        .apply();
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
            if (preference.equals(mLeftHandMode) && newValue instanceof Boolean) {
                ObaAnalytics.setLeftHanded(mFirebaseAnalytics, (Boolean) newValue);
            } else if (preference.equals(mHideAlertsPref) && newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    ObaContract.ServiceAlerts.hideAllAlerts();
                }
            } else if (preference.equals(mThemePref) && newValue instanceof String) {
                setAppTheme((String) newValue);
                requireActivity().recreate();
            } else if (preference.equals(mAnalyticsPref) && newValue instanceof Boolean) {
                ObaAnalytics.setSendAnonymousData(mFirebaseAnalytics, (Boolean) newValue);
            }
            return true;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null) return;

            if (key.equals(getString(R.string.preference_key_oba_api_url))) {
                changePreferenceSummary(key);
            } else if (key.equals(getString(R.string.preference_key_otp_api_url))) {
                changePreferenceSummary(key);
            } else if (key.equalsIgnoreCase(
                    getString(R.string.preference_key_preferred_units))) {
                changePreferenceSummary(key);
            } else if (key.equalsIgnoreCase(getString(R.string.preference_key_app_theme))) {
                changePreferenceSummary(key);
                setAppTheme(sharedPreferences.getString(key,
                        getString(R.string.preferences_app_theme_option_system_default)));
            } else if (key.equalsIgnoreCase(
                    getString(R.string.preference_key_auto_select_region))) {
                boolean autoSelect = sharedPreferences.getBoolean(key, true);
                if (autoSelect) {
                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                            getString(R.string.analytics_label_button_press_auto),
                            null);
                } else {
                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                            getString(R.string.analytics_label_button_press_manual),
                            null);
                }
            } else if (key.equalsIgnoreCase(getString(R.string.preferences_key_analytics))) {
                Boolean isAnalyticsActive = sharedPreferences.getBoolean(key, true);
                ObaAnalytics.setSendAnonymousData(mFirebaseAnalytics, isAnalyticsActive);
            } else if (key.equalsIgnoreCase(
                    getString(R.string.preference_key_show_negative_arrivals))) {
                boolean showDepartedBuses = sharedPreferences.getBoolean(key, false);
                ObaAnalytics.setShowDepartedVehicles(mFirebaseAnalytics, showDepartedBuses);
            } else if (key.equalsIgnoreCase(
                    getString(R.string.preference_key_preferred_temperature_units))) {
                changePreferenceSummary(key);
            } else if (key.equalsIgnoreCase(getString(R.string.preference_key_map_mode))) {
                changePreferenceSummary(key);
            }
        }

        private void changePreferenceSummary(String preferenceKey) {
            if (preferenceKey.equalsIgnoreCase(getString(R.string.preference_key_region))
                    || preferenceKey.equalsIgnoreCase(
                    getString(R.string.preference_key_oba_api_url))) {
                if (Application.get().getCurrentRegion() != null) {
                    mRegionPref.setSummary(Application.get().getCurrentRegion().getName());
                } else {
                    mRegionPref.setSummary(
                            getString(R.string.preferences_region_summary_custom_api));
                }
            } else if (preferenceKey.equalsIgnoreCase(
                    getString(R.string.preference_key_preferred_units))) {
                mPreferredUnits.setSummary(mPreferredUnits.getValue());
            } else if (preferenceKey.equalsIgnoreCase(
                    getString(R.string.preference_key_app_theme))) {
                mThemePref.setSummary(mThemePref.getValue());
            } else if (preferenceKey.equalsIgnoreCase(
                    getString(R.string.preference_key_preferred_temperature_units))) {
                mPreferredTempUnits.setSummary(mPreferredTempUnits.getValue());
            } else if (preferenceKey.equalsIgnoreCase(
                    getString(R.string.preference_key_map_mode))) {
                mMapMode.setSummary(mMapMode.getValue());
            }
        }

        private void updateBrandedPreferenceSummaries() {
            String appName = getString(R.string.app_name);

            Preference soundPref = findPreference(
                    getString(R.string.preference_key_notification_sound));
            if (soundPref != null) {
                soundPref.setSummary(
                        getString(R.string.preferences_preferred_sound_summary, appName));
            }

            Preference vibratePref = findPreference(
                    getString(R.string.preference_key_preference_vibrate_allowed));
            if (vibratePref != null) {
                vibratePref.setSummary(
                        getString(R.string.preferences_preferred_vibration_summary, appName));
            }

            if (mRestoreBackup != null) {
                mRestoreBackup.setSummary(
                        getString(R.string.preferences_restore_summary, appName));
            }

            if (mDonatePref != null) {
                mDonatePref.setSummary(
                        getString(R.string.preferences_donate_summary, appName));
            }

            if (mPoweredByObaPref != null) {
                mPoweredByObaPref.setTitle(
                        getString(R.string.preferences_powered_by_oba_title, appName));
            }

            Preference destLogsPref = findPreference(
                    getString(R.string.preferences_key_user_share_destination_logs));
            if (destLogsPref != null) {
                destLogsPref.setSummary(
                        getString(R.string.preferences_user_share_destination_logs_summary,
                                appName));
            }
        }
    }

    // ========================================================================
    // Advanced settings fragment
    // ========================================================================
    public static class AdvancedSettingsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceClickListener,
            Preference.OnPreferenceChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener {

        private Preference mCustomApiUrlPref;
        private Preference mCustomOtpApiUrlPref;
        private Preference mPushFirebaseData;
        private Preference mResetDonationTimestamps;
        private FirebaseAnalytics mFirebaseAnalytics;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_advanced, rootKey);
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());

            mCustomApiUrlPref = findPreference(getString(R.string.preference_key_oba_api_url));
            if (mCustomApiUrlPref != null) {
                mCustomApiUrlPref.setOnPreferenceChangeListener(this);
                String appName = getString(R.string.app_name);
                mCustomApiUrlPref.setTitle(
                        getString(R.string.preferences_oba_api_servername_title, appName));
            }

            mCustomOtpApiUrlPref = findPreference(getString(R.string.preference_key_otp_api_url));
            if (mCustomOtpApiUrlPref != null) {
                mCustomOtpApiUrlPref.setOnPreferenceChangeListener(this);
            }

            mPushFirebaseData = findPreference(
                    getString(R.string.preference_key_push_firebase_data));
            if (mPushFirebaseData != null) {
                mPushFirebaseData.setOnPreferenceClickListener(this);
            }

            mResetDonationTimestamps = findPreference(
                    getString(R.string.preference_key_reset_donation_timestamps));
            if (mResetDonationTimestamps != null) {
                mResetDonationTimestamps.setOnPreferenceClickListener(this);
            }

            if (BuildConfig.USE_FIXED_REGION) {
                Preference experimentalRegion = findPreference(
                        getString(R.string.preference_key_experimental_regions));
                PreferenceCategory advancedCategory = findPreference(
                        getString(R.string.preferences_category_advanced));
                if (advancedCategory != null && experimentalRegion != null) {
                    advancedCategory.removePreference(experimentalRegion);
                }
            }

            setIconSpaceReservedRecursive(getPreferenceScreen(), false);
        }

        @Override
        public void onResume() {
            super.onResume();
            Application.getPrefs().registerOnSharedPreferenceChangeListener(this);
            updateApiUrlSummaries();
        }

        @Override
        public void onPause() {
            super.onPause();
            Application.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceClick(@NonNull Preference pref) {
            if (pref.equals(mPushFirebaseData)) {
                FirebaseDataPusher pusher = new FirebaseDataPusher();
                pusher.push(requireContext());
            } else if (pref.equals(mResetDonationTimestamps)) {
                Application.getDonationsManager().setDonationRequestReminderDate(null);
                Application.getDonationsManager().setDonationRequestDismissedDate(null);
            }
            return true;
        }

        @Override
        public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
            if (preference.equals(mCustomApiUrlPref) && newValue instanceof String) {
                String apiUrl = (String) newValue;
                if (!TextUtils.isEmpty(apiUrl)) {
                    if (!validateUrl(apiUrl)) {
                        Toast.makeText(requireContext(),
                                getString(R.string.custom_api_url_error,
                                        getString(R.string.app_name)),
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    Application.get().setCurrentRegion(null);
                    Log.d(TAG, "User entered new API URL, set region to null.");
                } else {
                    Log.d(TAG, "User entered blank API URL, re-initializing regions...");
                    NavHelp.goHome(requireActivity(), false);
                }
            } else if (preference.equals(mCustomOtpApiUrlPref) && newValue instanceof String) {
                String apiUrl = (String) newValue;
                if (!TextUtils.isEmpty(apiUrl)) {
                    if (!validateUrl(apiUrl)) {
                        Toast.makeText(requireContext(),
                                getString(R.string.custom_otp_api_url_error),
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
                SettingsActivity activity = (SettingsActivity) requireActivity();
                activity.setOtpCustomAPIUrlChanged(true);
            }
            return true;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key == null) return;

            if (key.equals(getString(R.string.preference_key_experimental_regions))) {
                boolean experimentalServers = sharedPreferences.getBoolean(key, false);
                Log.d(TAG, "Experimental regions preference changed to " + experimentalServers);

                List<ObaRegionsTask.Callback> callbacks = new ArrayList<>();
                SettingsActivity activity = (SettingsActivity) requireActivity();
                callbacks.add(activity);
                ObaRegionsTask task = new ObaRegionsTask(requireContext(), callbacks, true, false);
                task.execute();

                if (experimentalServers) {
                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                            getString(R.string.analytics_label_button_press_experimental_on),
                            null);
                } else {
                    ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                            Application.get().getPlausibleInstance(),
                            PlausibleAnalytics.REPORT_PREFERENCES_EVENT_URL,
                            getString(R.string.analytics_label_button_press_experimental_off),
                            null);
                }
            } else if (key.equals(getString(R.string.preference_key_oba_api_url))
                    || key.equals(getString(R.string.preference_key_otp_api_url))) {
                updateApiUrlSummaries();
            }
        }

        private void updateApiUrlSummaries() {
            if (Application.get().getCurrentRegion() != null) {
                if (mCustomApiUrlPref != null) {
                    mCustomApiUrlPref.setSummary(
                            getString(R.string.preferences_oba_api_servername_summary,
                                    getString(R.string.app_name)));
                }
                String customOtpApiUrl = Application.get().getCustomOtpApiUrl();
                if (mCustomOtpApiUrlPref != null) {
                    if (!TextUtils.isEmpty(customOtpApiUrl)) {
                        mCustomOtpApiUrlPref.setSummary(customOtpApiUrl);
                    } else {
                        mCustomOtpApiUrlPref.setSummary(
                                getString(R.string.preferences_otp_api_servername_summary));
                    }
                }
            } else {
                if (mCustomApiUrlPref != null) {
                    mCustomApiUrlPref.setSummary(Application.get().getCustomApiUrl());
                }
            }
            Application.get().setUseOldOtpApiUrlVersion(false);
        }
    }
}
