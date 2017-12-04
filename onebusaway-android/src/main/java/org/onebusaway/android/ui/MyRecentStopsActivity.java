/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com)
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v7.app.AppCompatActivity;

import org.onebusaway.android.R;
import org.onebusaway.android.util.UIUtils;

public class MyRecentStopsActivity extends AppCompatActivity {

    //
    // The only thing this is used for anymore is to create
    // a shortcut to the recent stops list.
    //
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent myIntent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(myIntent.getAction())) {
            ShortcutInfoCompat shortcut = getShortcut();
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null);
            setResult(RESULT_OK, shortcut.getIntent());
        }
        finish();
    }

    private ShortcutInfoCompat getShortcut() {
        final Uri uri = MyTabActivityBase.getDefaultTabUri(MyRecentStopsFragment.TAB_NAME);
        return UIUtils.makeShortcutInfo(this,
                getString(R.string.recent_stops_shortcut),
                new Intent(this, MyStopsActivity.class).setData(uri),
                R.drawable.ic_history);
    }
}
