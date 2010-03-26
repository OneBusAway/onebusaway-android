package com.joulespersecond.seattlebusbot;

import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

abstract class MyTabActivityBase extends TabActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String action = getIntent().getAction();
        if (!Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            setTitle(R.string.app_name);
        }

        // A hack inspired mostly by:
        // http://stackoverflow.com/questions/1906314/android-tabwidget-in-light-theme
        // (Question by David Hedlund, answer by yanoka)
        //
        // This doesn't change any of the font sizes or colors, since those are fine for me.
        //
        getTabHost().getTabWidget().setBackgroundColor(
                getResources().getColor(R.color.tab_widget_bg));
    }
    @Override
    public void onDestroy() {
        SharedPreferences.Editor settings = getSharedPreferences(UIHelp.PREFS_NAME, 0).edit();
        settings.putString(getLastTabPref(), getTabHost().getCurrentTabTag());
        settings.commit();

        super.onDestroy();
    }

    protected void restoreDefaultTab() {
        SharedPreferences settings = getSharedPreferences(UIHelp.PREFS_NAME, 0);
        final String def = settings.getString(getLastTabPref(), null);
        if (def != null) {
            getTabHost().setCurrentTabByTag(def);
        }
    }

    void returnShortcut(Intent intent) {
        setResult(RESULT_OK, intent);
        finish();
    }

    protected abstract String getLastTabPref();
}
