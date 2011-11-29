package com.joulespersecond.seattlebusbot;

import com.joulespersecond.oba.provider.ObaContract;
import com.joulespersecond.seattlebusbot.map.MapFragment;

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
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentMapActivity;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.view.Window;
import android.view.MenuInflater;
import android.widget.Toast;

public class HomeActivity extends FragmentMapActivity {
    public static final String HELP_URL = "http://www.joulespersecond.com/onebusaway-userguide2/";
    public static final String TWITTER_URL = "http://mobile.twitter.com/seattlebusbot";

    private static final String TAG_HELP_DIALOG = ".HelpDialog";
    private static final String TAG_WHATSNEW_DIALOG = ".WhatsNew";

    private MapFragment mMapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        autoShowWhatsNew();
        UIHelp.checkAirplaneMode(this);

        FragmentManager fm = getSupportFragmentManager();

        // Create the list fragment and add it as our sole content.
        if (fm.findFragmentById(android.R.id.content) == null) {
            mMapFragment = new MapFragment();

            // TODO: The HomeActivity always goes to the user's current location.
            // TODO: We could do one of two things:
            // 1. We could have the home activity implement everything about
            // every map;
            // 2. We could have multiple map activities, each in its separate process.

            fm.beginTransaction().add(android.R.id.content, mMapFragment).commit();
        }
    }

    @Override
    protected boolean isRouteDisplayed() {
        return (mMapFragment != null) && mMapFragment.isRouteDisplayed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.find_stop) {
            Intent myIntent = new Intent(this, MyStopsActivity.class);
            startActivity(myIntent);
            return true;
        } else if (id == R.id.view_trips) {
            Intent myIntent = new Intent(this, TripListActivity.class);
            startActivity(myIntent);
            return true;
        } else if (id == R.id.help) {
            new HelpDialog().show(getSupportFragmentManager(), TAG_HELP_DIALOG);
            return true;
        }
        return false;
    }

    private static class HelpDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.main_help_title);
            builder.setItems(R.array.main_help_options,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                            case 0:
                                UIHelp.goToUrl(getActivity(), HELP_URL);
                                break;
                            case 1:
                                UIHelp.goToUrl(getActivity(), TWITTER_URL);
                                break;
                            case 2:
                                new WhatsNewDialog().show(getSupportFragmentManager(),
                                        TAG_WHATSNEW_DIALOG);
                                break;
                            case 3:
                                goToBugReport(getActivity());
                                break;
                            case 4:
                                Intent preferences = new Intent(
                                        getActivity(),
                                        EditPreferencesActivity.class);
                                startActivity(preferences);
                                break;
                            }
                        }
                    });
            return builder.create();
        }
    }

    private static class WhatsNewDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.main_help_whatsnew_title);
            builder.setIcon(R.drawable.icon);
            builder.setMessage(R.string.main_help_whatsnew);
            builder.setNeutralButton(R.string.main_help_close,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            return builder.create();
        }
    }

    private static final String WHATS_NEW_VER = "whatsNewVer";

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

        if (oldVer != newVer) {
            // It's impossible to tell the difference from people updating
            // from an older version without a What's New dialog and people
            // with fresh installs just by the settings alone.
            // So we'll do a heuristic and just check to see if they have
            // visited any stops -- in most cases that will mean they have
            // just installed.
            if (oldVer == 0 && newVer == 7) {
                Integer count = UIHelp
                        .intForQuery(this, ObaContract.Stops.CONTENT_URI,
                                ObaContract.Stops._COUNT);
                if (count != null && count != 0) {
                    new WhatsNewDialog().show(getSupportFragmentManager(),
                            TAG_WHATSNEW_DIALOG);
                }
            } else if ((oldVer > 0) && (oldVer < newVer)) {
                new WhatsNewDialog().show(getSupportFragmentManager(),
                        TAG_WHATSNEW_DIALOG);
            }
            // Updates will remove the alarms. This should put them back.
            // (Unfortunately I can't find a way to reschedule them without
            // having the app run again).
            TripService.scheduleAll(this);

            SharedPreferences.Editor edit = settings.edit();
            edit.putInt(WHATS_NEW_VER, appInfo.versionCode);
            edit.commit();
        }
    }

    private static void goToBugReport(Context ctxt) {
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
                 Build.VERSION.SDK_INT);
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
