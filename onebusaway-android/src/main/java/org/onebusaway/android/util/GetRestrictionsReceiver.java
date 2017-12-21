/*
 * Copyright (C) 2013-2017 The Android Open Source Project,
 * Microsoft Corporation
 *
 * https://github.com/googlesamples/android-AppRestrictions/blob/master/Application/src/main/java/com/example/android/apprestrictions/GetRestrictionsReceiver.java
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
package org.onebusaway.android.util;

import org.onebusaway.android.R;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionEntry;
import android.content.res.Resources;
import android.os.Bundle;

import java.util.ArrayList;

@TargetApi(18) // Restricted profiles introduced in API 18
public class GetRestrictionsReceiver extends BroadcastReceiver {
    public static final String EMBEDDED_SOCIAL_KEY = "embedded_social_enabled";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult result = goAsync();
        final Bundle existingRestrictions =
                intent.getBundleExtra(Intent.EXTRA_RESTRICTIONS_BUNDLE);
        new Thread() {
            public void run() {
                createRestrictions(context, result, existingRestrictions);
            }
        }.start();
    }

    private static RestrictionEntry createSocialRestriction(Resources res) {
        RestrictionEntry socialRestriction = new RestrictionEntry(EMBEDDED_SOCIAL_KEY, true);
        socialRestriction.setType(RestrictionEntry.TYPE_BOOLEAN);
        socialRestriction.setTitle(res.getString(R.string.embedded_social_restriction));

        return socialRestriction;
    }

    private ArrayList<RestrictionEntry> initRestrictions(Context context) {
        ArrayList<RestrictionEntry> newRestrictions = new ArrayList<>();
        Resources res = context.getResources();

        newRestrictions.add(createSocialRestriction(res));

        return newRestrictions;
    }

    private void createRestrictions(Context context, PendingResult result,
                                    Bundle existingRestrictions) {
        ArrayList<RestrictionEntry> newEntries = initRestrictions(context);

        // If app restrictions were not previously configured for the package, create the default
        // restrictions entries and return them.
        if (existingRestrictions == null) {
            finishBroadcast(result, newEntries);
            return;
        }

        // Retains current restriction settings by transferring existing restriction entries to
        // new ones.
        for (RestrictionEntry entry : newEntries) {
            final String key = entry.getKey();
            if (EMBEDDED_SOCIAL_KEY.equals(key)) {
                entry.setSelectedState(existingRestrictions.getBoolean(EMBEDDED_SOCIAL_KEY));
            }
        }

        finishBroadcast(result, newEntries);
    }

    private void finishBroadcast(PendingResult result, ArrayList<RestrictionEntry> newEntries) {
        Bundle extras = new Bundle();
        extras.putParcelableArrayList(Intent.EXTRA_RESTRICTIONS_LIST, newEntries);
        result.setResult(Activity.RESULT_OK, null, extras);
        result.finish();
    }
}
