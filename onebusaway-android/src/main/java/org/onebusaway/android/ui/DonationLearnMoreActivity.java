package org.onebusaway.android.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.util.UIUtils;

public class DonationLearnMoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donation_learn_more);
        UIUtils.setupActionBar(this);

        // Update explanation text with app name for white-label support
        TextView explanationView = findViewById(R.id.textView5);
        explanationView.setText(getString(R.string.donation_learn_more_explanation, getString(R.string.app_name)));

        Button btn = findViewById(R.id.btnDonationViewDonate);
        btn.setOnClickListener(b -> {
            Application.getDonationsManager().dismissDonationRequests();
            Intent i = Application.getDonationsManager().buildOpenDonationsPageIntent();
            startActivity(i);
            finish();
        });
    }
}