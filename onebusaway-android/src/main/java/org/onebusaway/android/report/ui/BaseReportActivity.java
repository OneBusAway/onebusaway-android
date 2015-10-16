package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;

import android.graphics.Color;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by Cagri Cetin
 */
public class BaseReportActivity extends AppCompatActivity {

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
        ((TextView) findViewById(R.id.ri_info_text)).setText(text);
        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.ri_info_header);
        if (relativeLayout.getVisibility() != View.VISIBLE) {
            relativeLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void removeInfoText() {
        ((TextView) findViewById(R.id.ri_info_text)).setText("");
        findViewById(R.id.ri_info_header).setVisibility(View.GONE);
//        slideToTop(findViewById(R.id.ri_info_header));
    }

    // To animate view slide out from bottom to top
    public void slideToTop(View view){
        TranslateAnimation animate = new TranslateAnimation(0,0,0,-view.getHeight());
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.GONE);
    }

    public void slideToBottom(View view){
        TranslateAnimation animate = new TranslateAnimation(0,0,0,view.getHeight());
        animate.setDuration(500);
        animate.setFillAfter(true);
        view.startAnimation(animate);
        view.setVisibility(View.GONE);
    }
}
