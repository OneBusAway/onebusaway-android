/*
* Copyright (C) 2014-2015 University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by Cagri Cetin
 */
public class BaseReportActivity extends AppCompatActivity {

    public final static String CLOSE_REQUEST = "BaseReportActivityClose";

    protected RelativeLayout mInfoHeader;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data.getBooleanExtra(CLOSE_REQUEST, false)){
            finish();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    protected FragmentTransaction setFragment(Fragment fragment, int containerViewId) {
        FragmentManager fm = getSupportFragmentManager();
        return fm.beginTransaction().replace(containerViewId, fragment);
    }

    protected FragmentTransaction setFragment(Fragment fragment, String tag, int containerViewId) {
        FragmentManager fm = getSupportFragmentManager();
        return fm.beginTransaction().replace(containerViewId, fragment, tag);
    }

    protected FragmentTransaction addFragment(Fragment fragment, String tag, int containerViewId) {
        FragmentManager fm = getSupportFragmentManager();
        return fm.beginTransaction().add(containerViewId, fragment, tag);
    }

    protected void removeFragment(Fragment fragment) {
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction trans = manager.beginTransaction();
        trans.remove(fragment);
        trans.commit();
        manager.popBackStack();
    }

    protected void removeFragmentByTag(String tag) {
        FragmentManager manager = getSupportFragmentManager();
        Fragment fragment = manager.findFragmentByTag(tag);

        if (fragment != null) {
            FragmentTransaction trans = manager.beginTransaction();
            trans.remove(fragment);
            trans.commit();
            manager.popBackStack();
        }
    }

    protected void setUpProgressBar() {
        ActionBar.LayoutParams params = new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        progressBar.setLayoutParams(params);
        progressBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.MULTIPLY);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayShowCustomEnabled(true);
        ab.setCustomView(progressBar);
    }

    public void showProgress(Boolean visible) {
        setSupportProgressBarIndeterminateVisibility(Boolean.TRUE);
        if (visible)
            getSupportActionBar().getCustomView().setVisibility(View.VISIBLE);
        else
            getSupportActionBar().getCustomView().setVisibility(View.GONE);
    }

    protected void createToastMessage(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    protected void addInfoText(String text) {
        if (mInfoHeader == null){
            mInfoHeader = (RelativeLayout) findViewById(R.id.ri_info_header);
        }
        ((TextView) mInfoHeader.findViewById(R.id.ri_info_text)).setText(text);
        if (mInfoHeader.getVisibility() != View.VISIBLE) {
            mInfoHeader.setVisibility(View.VISIBLE);
        }
    }

    protected  boolean isInfoVisiable() {
        if (mInfoHeader == null){
            mInfoHeader = (RelativeLayout) findViewById(R.id.ri_info_header);
        }
        return mInfoHeader.getVisibility() == View.VISIBLE;
    }

    protected void removeInfoText() {
        if (mInfoHeader == null){
            mInfoHeader = (RelativeLayout) findViewById(R.id.ri_info_header);
        }
        ((TextView) mInfoHeader.findViewById(R.id.ri_info_text)).setText("");
        mInfoHeader.setVisibility(View.GONE);
    }
}
