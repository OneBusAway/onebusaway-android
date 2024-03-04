/*
 * Copyright (C) 2015-2017 University of South Florida (sjbarbeau@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onebusaway.android.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.widget.TextView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.textview.MaterialTextView;

import org.onebusaway.android.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * An Activity that displays version, license, and contributor information
 */
public class AboutActivity extends AppCompatActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, AboutActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        CollapsingToolbarLayout toolBarLayout = (CollapsingToolbarLayout) findViewById(
                R.id.toolbar_layout);
        toolBarLayout.setTitle(getTitle());

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

//        TextView tv = (TextView) findViewById(R.id.about_text);
        MaterialTextView versionText,codeContributorsText,translationsText,imageCreditsText,howToContributeText;
        versionText = findViewById(R.id.version_text);
        codeContributorsText = findViewById(R.id.code_contributor_text);
        translationsText = findViewById(R.id.translations_text);
        imageCreditsText = findViewById(R.id.image_credit_text);
        howToContributeText = findViewById(R.id.contribute_to_our_app_text);

        String versionString = "";
        int versionCode = 0;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionString = info.versionName;
            versionCode = info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        StringBuilder builder = new StringBuilder();
        // Version info
        builder.append("Version: ")
                .append(versionString)
                .append("(")
                .append(versionCode)
                .append(")");

        // Majority of content from string resource
//        builder.append(getString(R.string.about_text));
//        builder.append("\n\n");
        versionText.setText(builder);
        //set Html text to textview
        codeContributorsText.setText((Spannable) Html.fromHtml(getString(R.string.code_contributors_content)));
        translationsText.setText((Spannable) Html.fromHtml(getString(R.string.translations_content)));
        imageCreditsText.setText((Spannable) Html.fromHtml(getString(R.string.image_credits_content)));
        howToContributeText.setText((Spannable) Html.fromHtml(getString(R.string.contribute_to_our_app_content)));
    }
}
