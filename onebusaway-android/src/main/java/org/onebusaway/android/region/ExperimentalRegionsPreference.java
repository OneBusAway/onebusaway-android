/*
 * Copyright (C) 2013 University of South Florida (sjbarbeau@gmail.com).
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
package org.onebusaway.android.region;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.PreferenceUtils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.util.AttributeSet;

/**
 * Custom preference to handle enabling and disabling experimental
 * (i.e., non-production) OBA regions
 * <p/>
 * Code for saving/restoring state based on example at:
 * http://developer.android.com/guide/topics/ui/settings.html
 * <p/>
 * and Android internal YesNoPreference:
 * https://github.com/android/platform_frameworks_base/blob/master/core/java/com/android/internal/preference/YesNoPreference.java
 *
 * @author Sean Barbeau
 */
public class ExperimentalRegionsPreference extends CheckBoxPreference {

    private boolean mCurrentValue;

    private final boolean DEFAULT_VALUE = false;

    //
    // Needed constructors.
    //
    public ExperimentalRegionsPreference(Context context) {
        super(context);
        initCheckedState();
    }

    public ExperimentalRegionsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initCheckedState();
    }

    public ExperimentalRegionsPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initCheckedState();
    }

    protected void initCheckedState() {
        setChecked(Application.getPrefs()
                .getBoolean(getContext().getString(R.string.preference_key_experimental_regions),
                        DEFAULT_VALUE));
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    protected void onClick() {
        if (!isChecked()) {
            /*
            Warn the user before enabling, since experimental regions
            may not have real-time info or may be unavailable.
            */
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setMessage(R.string.preferences_experimental_regions_enable_warning)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            setValue(true);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
            dialog.show();
        } else {
            if (Application.get().getCurrentRegion() != null &&
                    Application.get().getCurrentRegion().getExperimental()) {
                // If the user is currently using an experimental region, warn that it won't be available
                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setMessage(R.string.preferences_experimental_regions_disable_warning)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        // Set the region info to null, so we no longer use the current experimental region
                                        Application.get().setCurrentRegion(null);
                                        setValue(false);
                                    }
                                }
                        )
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }
                        )
                        .create();
                dialog.show();
            } else {
                setValue(false);
            }
        }
    }

    /**
     * Sets the experimental regions preference (both user interface and shared preference value)
     * to
     * the input value
     *
     * @param newValue true if the preference should be set to true, false if it should be set to
     *                 false
     */
    public void setValue(boolean newValue) {
        mCurrentValue = newValue;
        setChecked(newValue);
        PreferenceUtils
                .saveBoolean(getContext().getString(R.string.preference_key_experimental_regions),
                        newValue);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore existing state
            mCurrentValue = this.getPersistedBoolean(mCurrentValue);
        } else {
            // Set default state from the XML attribute
            mCurrentValue = (Boolean) defaultValue;
            persistBoolean(mCurrentValue);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getBoolean(index, DEFAULT_VALUE);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();

        // Create instance of custom BaseSavedState
        final SavedState myState = new SavedState(superState);
        // Set the state's value with the class member that holds current setting value
        myState.value = mCurrentValue;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        // Check whether we saved the state in onSaveInstanceState
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save the state, so call superclass
            super.onRestoreInstanceState(state);
            return;
        }

        // Cast state to custom BaseSavedState and pass to superclass
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());

        // Set this Preference to reflect the restored state
        setValue(myState.value);
    }

    /**
     * Used to handle onSaveInstanceState()/onRestoreInstanceState properly, based on example
     * at http://developer.android.com/guide/topics/ui/settings.html
     */
    private static class SavedState extends Preference.BaseSavedState {

        // Member that holds the setting's value
        boolean value;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            // Get the current preference's value
            value = source.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            // Write the preference's value
            dest.writeByte((byte) (value ? 1 : 0));
        }

        // Standard creator object using an instance of this class
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}