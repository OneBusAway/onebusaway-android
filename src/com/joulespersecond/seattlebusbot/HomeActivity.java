/*
 * Copyright (C) 2011 Paul Watts (paulcwatts@gmail.com)
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

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.joulespersecond.seattlebusbot.map.BaseMapActivity;
import com.joulespersecond.seattlebusbot.map.MapParams;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import java.util.Iterator;
import java.util.List;

public class HomeActivity extends BaseMapActivity {
    public static final String TWITTER_URL = "http://mobile.twitter.com/onebusaway";

    private static final int HELP_DIALOG = 1;
    private static final int WHATSNEW_DIALOG = 2;

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context
     *            The context of the activity.
     * @param focusId
     *            The stop to focus.
     * @param lat
     *            The latitude of the map center.
     * @param lon
     *            The longitude of the map center.
     */
    public static final void start(Context context,
            String focusId,
            double lat,
            double lon) {
        context.startActivity(makeIntent(context, focusId, lat, lon));
    }

    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     *
     * @param context
     *            The context of the activity.
     * @param routeId
     *            The route to show.
     */
    public static final void start(Context context, String routeId) {
        context.startActivity(makeIntent(context, routeId));
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context
     *            The context of the activity.
     * @param focusId
     *            The stop to focus.
     * @param lat
     *            The latitude of the map center.
     * @param lon
     *            The longitude of the map center.
     */
    public static final Intent makeIntent(Context context,
            String focusId,
            double lat,
            double lon) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, focusId);
        myIntent.putExtra(MapParams.CENTER_LAT, lat);
        myIntent.putExtra(MapParams.CENTER_LON, lon);
        return myIntent;
    }

    /**
     * Returns an intent that starts the MapActivity in "RouteMode", which shows
     * stops along a route, and does not get new stops when the user pans the
     * map.
     *
     * @param context
     *            The context of the activity.
     * @param routeId
     *            The route to show.
     */
    public static final Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.MODE, MapParams.MODE_ROUTE);
        myIntent.putExtra(MapParams.ZOOM_TO_ROUTE, true);
        myIntent.putExtra(MapParams.ROUTE_ID, routeId);
        return myIntent;
    }

    private int mWhatsNewMessage = R.string.main_help_whatsnew;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.main);

        UIHelp.setupActionBar(getSupportActionBar());

        autoShowWhatsNew();
    }

    @Override
    protected int getContentView() {
        return R.layout.main;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.main_options, menu);
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            NavHelp.goHome(this);
            return true;
        } else if (id == R.id.find_stop) {
            Intent myIntent = new Intent(this, MyStopsActivity.class);
            startActivity(myIntent);
            return true;
        } else if (id == R.id.find_route) {
            Intent myIntent = new Intent(this, MyRoutesActivity.class);
            startActivity(myIntent);
            return true;
        } else if (id == R.id.view_trips) {
            Intent myIntent = new Intent(this, TripListActivity.class);
            startActivity(myIntent);
            return true;
        } else if (id == R.id.help) {
            showDialog(HELP_DIALOG);
            return true;
        } else if (id == R.id.preferences) {
            Intent preferences = new Intent(
                    HomeActivity.this,
                    EditPreferencesActivity.class);
            startActivity(preferences);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case HELP_DIALOG:
            return createHelpDialog();

        case WHATSNEW_DIALOG:
            return createWhatsNewDialog();
        }
        return super.onCreateDialog(id);
    }

    @SuppressWarnings("deprecation")
    private Dialog createHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_title);
        builder.setItems(R.array.main_help_options,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                        case 0:
                            UIHelp.goToUrl(HomeActivity.this, TWITTER_URL);
                            break;
                        case 1:
                            showDialog(WHATSNEW_DIALOG);
                            break;
                        case 2:
                            goToContactEmail(HomeActivity.this);
                            break;
                        }
                    }
                });
        return builder.create();
    }

    @SuppressWarnings("deprecation")
    private Dialog createWhatsNewDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.main_help_whatsnew_title);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setMessage(mWhatsNewMessage);
        builder.setNeutralButton(R.string.main_help_close,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dismissDialog(WHATSNEW_DIALOG);
                    }
                });
        return builder.create();
    }

    private static final String WHATS_NEW_VER = "whatsNewVer";

    @SuppressWarnings("deprecation")
    private void autoShowWhatsNew() {
        SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0);

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }

        final int oldVer = settings.getInt(WHATS_NEW_VER, 0);
        final int newVer = appInfo.versionCode;

        if ((oldVer > 0) && (oldVer < newVer)) {
            if (oldVer <= 22) {
                mWhatsNewMessage = R.string.main_help_whatsnew_22;
            } else {
                mWhatsNewMessage = R.string.main_help_whatsnew;
            }
            showDialog(WHATSNEW_DIALOG);

            // Updates will remove the alarms. This should put them back.
            // (Unfortunately I can't find a way to reschedule them without
            // having the app run again).
            TripService.scheduleAll(this);

            SharedPreferences.Editor edit = settings.edit();
            edit.putInt(WHATS_NEW_VER, appInfo.versionCode);
            edit.commit();
        }
    }

    private static String getLocationString(Context context) {
        LocationManager mgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        String provider = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext();) {
            String p = i.next();
            Location loc = mgr.getLastKnownLocation(p);
            if (loc != null && (last == null || loc.getTime() > last.getTime())) {
                last = loc;
                provider = p;
            }
        }

        if (last == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(provider);
        sb.append(' ');
        sb.append(last.getLatitude());
        sb.append(',');
        sb.append(last.getLongitude());
        if (last.hasAccuracy()) {
            sb.append(' ');
            sb.append(last.getAccuracy());
        }

        return sb.toString();
    }

    private static void goToContactEmail(Context ctxt) {
        PackageManager pm = ctxt.getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(ctxt.getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        // appInfo.versionName
        // Build.MODEL
        // Build.VERSION.RELEASE
        // Build.VERSION.SDK
        // %s\nModel: %s\nOS Version: %s\nSDK Version: %s\
        final String body = ctxt.getString(R.string.bug_report_body,
                 appInfo.versionName,
                 Build.MODEL,
                 Build.VERSION.RELEASE,
                 Build.VERSION.SDK_INT,
                 getLocationString(ctxt));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.putExtra(Intent.EXTRA_EMAIL,
                new String[] { ctxt.getString(R.string.bug_report_dest) });
        send.putExtra(Intent.EXTRA_SUBJECT,
                ctxt.getString(R.string.bug_report_subject));
        send.putExtra(Intent.EXTRA_TEXT, body);
        send.setType("message/rfc822");
        try {
            ctxt.startActivity(Intent.createChooser(send,
                    ctxt.getString(R.string.bug_report_subject)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctxt, R.string.bug_report_error, Toast.LENGTH_LONG)
                    .show();
        }
    }
}
