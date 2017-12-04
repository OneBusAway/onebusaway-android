/*
 * Copyright 2013-2015 Colin McDonough, University of South Florida,
 * Sean J. Barbeau
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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import org.onebusaway.android.R;
import org.onebusaway.android.util.UIUtils;

/**
 * A flashing light that riders can show at night to flag bus drivers
 */
public class NightLightActivity extends AppCompatActivity {

    private static final String TAG = "NightLightActivity";

    private static final String PREFERENCE_SHOWED_DIALOG = "showed_night_light_dialog";

    static final String INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT";

    private static final int COLOR_DARK = 0xCC000000;

    private boolean lightOn;

    private boolean dialogShown;

    private View screen;

    boolean active = true;

    // Amount of time between flashes, in milliseconds
    private int[] waitTime = {100, 100, 400};

    // Amount of time light is left on for single flash, in milliseconds
    private static final int FLASH_TIME_ON = 75;

    private int counter = 0;

    private int[] mColors;

    private float mOldScreenBrightness;

    /**
     * Starts the activity
     */
    public static void start(Context context) {
        Intent i = new Intent(context, NightLightActivity.class);
        context.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.setupActionBar(this);

        Intent intent = getIntent();
        if (Intent.ACTION_CREATE_SHORTCUT.equals(intent.getAction())) {
            ShortcutInfoCompat shortcut = createShortcut();
            setResult(RESULT_OK, shortcut.getIntent());
            finish();
        }

        setContentView(R.layout.night_light);
        screen = findViewById(R.id.screen);
        disableScreenSleep();

        // Set up colors to flash on screen
        mColors = new int[3];
        mColors[0] = Color.WHITE;
        mColors[1] = getResources().getColor(R.color.theme_primary);
        mColors[2] = Color.WHITE;

        maybeShowIntroDialog();
    }

    @Override
    public void onResume() {
        super.onResume();
        turnLightOn();

        active = true;

        // Flash the light via a Thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (active) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            turnLightOn();

                        }
                    });

                    Log.d(TAG, "Flashing for " + FLASH_TIME_ON + "ms");

                    try {
                        Thread.sleep(FLASH_TIME_ON);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            turnLightOff();
                        }
                    });

                    try {
                        Log.d(TAG, "Sleeping for " + waitTime[counter % 3] + "ms");
                        Thread.sleep(waitTime[counter % 3]);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    counter++;
                }
            }
        }).start();
    }

    @Override
    public void onPause() {
        super.onPause();
        turnLightOff();
        active = false;

        restoreScreenBrightness();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.night_light, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.create_shortcut) {
            createShortcut();
            return true;
        }
        return false;
    }

    /**
     * Called after its confirmed that the user has seen the intro dialog to start the flashing
     */
    public void onViewedDialog() {
        dialogShown = true;

        // Set screen brightness to full
        setScreenBrightness();

        turnLightOn();
    }

    private void disableScreenSleep() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Shows the initial intro dialog if the user hasn't yet seen it, and then start the flashing.
     * If the user has already seen the dialog, immediately start flashing
     */
    private void maybeShowIntroDialog() {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_SHOWED_DIALOG, false)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.night_light_dialog_title);
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    sp.edit().putBoolean(PREFERENCE_SHOWED_DIALOG, true).commit();
                    // Start the flashing
                    onViewedDialog();
                }
            });
            builder.setMessage(R.string.night_light_dialog_message);
            builder.create().show();
        } else {
            // Start the flashing
            onViewedDialog();
        }
    }

    /*
     * Called by the view (see main.xml)
     */
    public void toggleLight(View view) {
        toggleLight();
    }

    private void toggleLight() {
        if (lightOn) {
            turnLightOff();
        } else {
            turnLightOn();
        }
    }

    private void turnLightOn() {
        if (!dialogShown) {
            return;
        }

        lightOn = true;

        // Use the screen as a flashlight
        screen.setBackgroundColor(mColors[counter % 3]);
    }

    private void turnLightOff() {
        if (lightOn) {
            // Set the background to dark
            screen.setBackgroundColor(COLOR_DARK);
            lightOn = false;
        }
    }

    private void setScreenBrightness() {
        WindowManager.LayoutParams lp = this.getWindow().getAttributes();
        mOldScreenBrightness = lp.screenBrightness;
        lp.screenBrightness = 1.0f;
        this.getWindow().setAttributes(lp);
    }

    private void restoreScreenBrightness() {
        WindowManager.LayoutParams lp = this.getWindow().getAttributes();
        lp.screenBrightness = mOldScreenBrightness;
    }

    /**
     * Create a shortcut on the home screen
     * @return shortcut info that was created
     */
    private ShortcutInfoCompat createShortcut() {
        final ShortcutInfoCompat shortcut =
                UIUtils.makeShortcutInfo(this,
                        getString(R.string.stop_info_option_night_light),
                        new Intent(this,
                                NightLightActivity.class),
                        R.drawable.ic_night_light);
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null);
        return shortcut;
    }
}
