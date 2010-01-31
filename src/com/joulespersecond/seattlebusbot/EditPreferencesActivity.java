package com.joulespersecond.seattlebusbot;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class EditPreferencesActivity extends PreferenceActivity {

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }
}
