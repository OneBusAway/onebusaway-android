package org.onebusaway.android.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;

public class DonationLearnMoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_donation_learn_more);

        Button btn = findViewById(R.id.btnDonationViewDonate);
        btn.setOnClickListener(b -> {
            Application.getDonationsManager().dismissDonationRequests();
            Intent i = Application.getDonationsManager().buildOpenDonationsPageIntent();
            startActivity(i);
            finish();
        });
    }
}