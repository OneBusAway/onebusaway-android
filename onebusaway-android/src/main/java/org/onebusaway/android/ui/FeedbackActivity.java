package org.onebusaway.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.ImageButton;

import org.onebusaway.android.R;

import androidx.appcompat.app.AppCompatActivity;

public class FeedbackActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        setTitle(getResources().getString(R.string.feedback_label));

        Intent intent = this.getIntent();
        if(intent != null){
            String action = intent.getExtras().getString("CallingAction");
            if(action.equals("Dislike")){
                Log.d("FeedbackActivity", "Thumbs down tapped");
                ImageButton feedbackButton = findViewById(R.id.ImageBtn_Dislike);
                feedbackButton.setSelected(true);

            }
            else if(action.equals("Like")){
                Log.d("FeedbackActivity", "Thumbs up tapped");
                ImageButton feedbackButton = findViewById(R.id.ImageBtn_like);
                feedbackButton.setSelected(true);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.report_issue_action, menu);
        return true;
    }

/*    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_name) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }*/
}
