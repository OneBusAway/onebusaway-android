/*
 * Copyright (C) 2019 University of South Florida
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
package org.onebusaway.android.travelbehavior;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.io.TravelBehaviorFileSaverExecutorManager;
import org.onebusaway.android.travelbehavior.io.task.ArrivalAndDepartureDataSaverTask;
import org.onebusaway.android.travelbehavior.io.task.DestinationReminderDataSaverTask;
import org.onebusaway.android.travelbehavior.io.task.TripPlanDataSaverTask;
import org.onebusaway.android.travelbehavior.io.worker.OptOutTravelBehaviorParticipantWorker;
import org.onebusaway.android.travelbehavior.io.worker.RegisterTravelBehaviorParticipantWorker;
import org.onebusaway.android.travelbehavior.io.worker.UpdateDeviceInfoWorker;
import org.onebusaway.android.travelbehavior.receiver.TransitionBroadcastReceiver;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorFirebaseIOUtils;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorUtils;
import org.onebusaway.android.util.PermissionUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.opentripplanner.api.model.TripPlan;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.core.graphics.drawable.DrawableCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

public class TravelBehaviorManager {

    private static final String TAG = "TravelBehaviorManager";

    private Context mActivityContext;

    private Context mApplicationContext;

    private FirebaseAnalytics mFirebaseAnalytics;

    public TravelBehaviorManager(Context activityContext, Context applicationContext) {
        mActivityContext = activityContext;
        mApplicationContext = applicationContext;
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(activityContext);
    }

    public void registerTravelBehaviorParticipant() {
        registerTravelBehaviorParticipant(false);
    }

    public void registerTravelBehaviorParticipant(boolean forceStart) {
        // Do not register if enrolling is no more allowed;
        if (!TravelBehaviorUtils.allowEnrollMoreParticipantsInStudy()) {
            return;
        }

        boolean isUserOptOut = PreferenceUtils.getBoolean(TravelBehaviorConstants.USER_OPT_OUT,
                false);
        if (forceStart) isUserOptOut = false;
        // If user opt out or Global switch is off then do nothing
        if (!TravelBehaviorUtils.isTravelBehaviorActiveInRegion() || isUserOptOut) {
            stopCollectingData();
            return;
        }

        boolean isUserOptIn = PreferenceUtils.getBoolean(TravelBehaviorConstants.USER_OPT_IN,
                false);

        // If the user not opt in yet
        if (!isUserOptIn) {
            showParticipationDialog();
        }
    }

    private void showParticipationDialog() {
        View v = LayoutInflater.from(mActivityContext).inflate(R.layout.research_participation_dialog, null);
        CheckBox neverShowDialog = v.findViewById(R.id.research_never_ask_again);

        new AlertDialog.Builder(mActivityContext)
                .setView(v)
                .setTitle(R.string.travel_behavior_opt_in_title)
                .setIcon(createIcon())
                .setCancelable(false)
                .setPositiveButton(R.string.travel_behavior_dialog_learn_more,
                        (dialog, which) -> showAgeDialog())
                .setNegativeButton(R.string.travel_behavior_dialog_not_now,
                        (dialog, which) -> {
                            // If the user has chosen not to see the dialog again opt them out of the study, otherwise do nothing so they are prompted again later
                            if (neverShowDialog.isChecked()) {
                                optOutUser();
                                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                        mApplicationContext.getString(R.string.analytics_label_button_travel_behavior_opt_out_at_first_dialog),
                                        null);
                            } else {
                                ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                        mApplicationContext.getString(R.string.analytics_label_button_travel_behavior_enroll_not_now),
                                        null);
                            }
                        })
                .create().show();
    }

    private void showAgeDialog() {
        new AlertDialog.Builder(mActivityContext)
                .setMessage(R.string.travel_behavior_age_message)
                .setTitle(R.string.travel_behavior_opt_in_title)
                .setIcon(createIcon())
                .setCancelable(false)
                .setPositiveButton(R.string.travel_behavior_dialog_yes,
                        (dialog, which) -> {
                            showInformedConsent();
                            dialog.dismiss();
                            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                    mApplicationContext.getString(R.string.analytics_label_button_travel_behavior_opt_in_over_18),
                                    null);
                        })
                .setNegativeButton(R.string.travel_behavior_dialog_no,
                        (dialog, which) -> {
                            Toast.makeText(mApplicationContext,
                                    R.string.travel_behavior_age_invalid_message, Toast.LENGTH_LONG).show();
                            optOutUser();
                            dialog.dismiss();
                            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                    mApplicationContext.getString(R.string.analytics_label_button_travel_behavior_opt_out_under_18),
                                    null);
                        })
                .create().show();
    }

    private void showInformedConsent() {
        String consentHtml = getHtmlConsentDocument();
        new AlertDialog.Builder(mActivityContext)
                .setMessage(Html.fromHtml(consentHtml))
                .setTitle(R.string.travel_behavior_opt_in_title)
                .setIcon(createIcon())
                .setCancelable(false)
                .setPositiveButton(R.string.travel_behavior_dialog_consent_agree,
                        (dialog, which) -> {
                            showEmailDialog();
                            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                    mApplicationContext.getString(R.string.analytics_label_button_travel_behavior_opt_in_informed_consent),
                                    null);
                        })
                .setNegativeButton(R.string.travel_behavior_dialog_consent_disagree,
                        (dialog, which) -> {
                            optOutUser();
                            ObaAnalytics.reportUiEvent(mFirebaseAnalytics,
                                    mApplicationContext.getString(R.string.analytics_label_button_travel_behavior_opt_out_informed_consent),
                                    null);
                        })
                .create().show();
    }

    private String getHtmlConsentDocument() {
        InputStream inputStream = mApplicationContext.getResources().
                openRawResource(R.raw.travel_behavior_informed_consent);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toString();
    }

    private void showEmailDialog() {
        showEmailDialog(null);
    }

    private void showEmailDialog(String email) {
        LayoutInflater inflater = ((Activity) mActivityContext).getLayoutInflater();
        final View editTextView = inflater.inflate(R.layout.travel_behavior_email_dialog, null);
        EditText emailEditText = editTextView.findViewById(R.id.tb_email_edittext);
        EditText emailEditTextConfirm = editTextView.findViewById(R.id.tb_email_edittext_confirm);

        if (email != null) {
            emailEditText.setText(email);
        }

        new AlertDialog.Builder(mActivityContext)
                .setTitle(R.string.travel_behavior_opt_in_title)
                .setMessage(R.string.travel_behavior_email_message)
                .setIcon(createIcon())
                .setCancelable(false)
                .setView(editTextView)
                .setPositiveButton(R.string.travel_behavior_dialog_email_save,
                        (dialog, which) -> {
                            String currentEmail = emailEditText.getText().toString();
                            String currentEmailConfirm = emailEditTextConfirm.getText().toString();
                            if (!TextUtils.isEmpty(currentEmail) &&
                                    Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches() &&
                                    currentEmail.equalsIgnoreCase(currentEmailConfirm)) {
                                registerUser(currentEmail);
                                checkPermissions();
                            } else {
                                Toast.makeText(mApplicationContext, R.string.travel_behavior_email_invalid,
                                        Toast.LENGTH_LONG).show();
                                // Android automatically dismisses the dialog.
                                // Show the dialog again if the email is invalid
                                showEmailDialog(currentEmail);
                            }
                        })
                .create().show();
    }

    private void checkPermissions() {
        if (!PermissionUtils.hasGrantedPermissions(mApplicationContext, TravelBehaviorConstants.PERMISSIONS)) {
            Activity homeActivity = (Activity) mActivityContext;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                homeActivity.requestPermissions(TravelBehaviorConstants.PERMISSIONS, 1);
            }
        }
    }


    private void registerUser(String email) {
        Data myData = new Data.Builder()
                .putString(TravelBehaviorConstants.USER_EMAIL, email)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.
                Builder(RegisterTravelBehaviorParticipantWorker.class)
                .setInputData(myData)
                .setConstraints(constraints)
                .build();

        WorkManager workManager = WorkManager.getInstance();
        workManager.enqueue(workRequest);
        ListenableFuture<WorkInfo> listenableFuture = workManager.
                getWorkInfoById(workRequest.getId());
        Futures.addCallback(listenableFuture, new FutureCallback<WorkInfo>() {
            @Override
            public void onSuccess(@NullableDecl WorkInfo result) {
                Activity activity = (Activity) mActivityContext;
                activity.runOnUiThread(() -> Toast.makeText(mApplicationContext, R.string.travel_behavior_enroll_success,
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onFailure(Throwable t) {
                Activity activity = (Activity) mActivityContext;
                activity.runOnUiThread(() -> Toast.makeText(mApplicationContext, R.string.travel_behavior_enroll_fail,
                        Toast.LENGTH_LONG).show());
            }
        }, TravelBehaviorFileSaverExecutorManager.getInstance().getThreadPoolExecutor());
    }

    public static void startCollectingData(Context applicationContext) {
        if (TravelBehaviorUtils.isUserParticipatingInStudy()) {
            new TravelBehaviorManager(null, applicationContext).startCollectingData();
        }
    }

    private void startCollectingData() {
        int[] activities = {DetectedActivity.IN_VEHICLE,
                DetectedActivity.ON_BICYCLE, DetectedActivity.WALKING,
                DetectedActivity.STILL, DetectedActivity.RUNNING};

        List<ActivityTransition> transitions = new ArrayList<>();

        for (int activity : activities) {
            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build());

            transitions.add(new ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build());
        }

        ActivityTransitionRequest atr = new ActivityTransitionRequest(transitions);
        Intent intent = new Intent(mApplicationContext, TransitionBroadcastReceiver.class);
        // If pending intent is already created do not create a new one

//        PendingIntent pi = PendingIntent.getBroadcast(mApplicationContext, 100, intent,
//                PendingIntent.FLAG_NO_CREATE);

        // The above method returns null if the pending intent is not active
        // it returns the pending intent object if the pending intent is alive
        // --> The idea is here that if the pending intent is alive (i.e., pi object is not null)
        // then do not create a new object
        // However, every time the above method is called, the application stops receiving
        // activity transitions
        // TODO: figure out the problem described above

        PendingIntent pi = PendingIntent.getBroadcast(mApplicationContext, 100,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Task<Void> task = ActivityRecognition.getClient(mApplicationContext)
                .requestActivityTransitionUpdates(atr, pi);
        task.addOnCompleteListener(task1 -> {
            if (task1.isSuccessful()) {
                Log.d(TAG, "Travel behavior activity-transition-update set up");
            } else {

                TravelBehaviorFirebaseIOUtils.logErrorMessage(task1.getException(),
                        "Travel behavior activity-transition-update failed set up: ");
            }
        });

        saveDeviceInformation();
    }

    private void saveDeviceInformation() {
        String uid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
        Data myData = new Data.Builder()
                .putString(TravelBehaviorConstants.USER_ID, uid)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                UpdateDeviceInfoWorker.class)
                .setInputData(myData)
                .build();
        WorkManager.getInstance().enqueue(workRequest);
    }

    public void stopCollectingData() {
        Intent intent = new Intent(mApplicationContext, TransitionBroadcastReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(mApplicationContext, 100, intent,
                PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            ActivityRecognition.getClient(mApplicationContext).removeActivityUpdates(pi);
            pi.cancel();
        }
    }

    private Drawable createIcon() {
        Drawable icon = mApplicationContext.getResources().getDrawable(R.drawable.ic_light_bulb);
        DrawableCompat.setTint(icon, mApplicationContext.getResources().getColor(R.color.theme_primary));
        return icon;
    }

    public static void optOutUser() {
        PreferenceUtils.saveBoolean(TravelBehaviorConstants.USER_OPT_OUT, true);
        PreferenceUtils.saveBoolean(TravelBehaviorConstants.USER_OPT_IN, false);
    }

    public static void optOutUserOnServer() {
        String uid = PreferenceUtils.getString(TravelBehaviorConstants.USER_ID);
        Data myData = new Data.Builder()
                .putString(TravelBehaviorConstants.USER_ID, uid)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(
                OptOutTravelBehaviorParticipantWorker.class)
                .setInputData(myData)
                .build();
        WorkManager.getInstance().enqueue(workRequest);
    }

    public static void optInUser(String uid) {
        PreferenceUtils.saveString(TravelBehaviorConstants.USER_ID, uid);
        PreferenceUtils.saveBoolean(TravelBehaviorConstants.USER_OPT_IN, true);
        PreferenceUtils.saveBoolean(TravelBehaviorConstants.USER_OPT_OUT, false);
    }

    public static void saveDestinationReminders(String currStopId, String destStopId, String tripId,
                                                String routeId, Long serverTime) {
        if (TravelBehaviorUtils.isUserParticipatingInStudy()) {
            DestinationReminderDataSaverTask saverTask = new DestinationReminderDataSaverTask(currStopId,
                    destStopId, tripId, routeId, serverTime, Application.get().getApplicationContext());
            TravelBehaviorFileSaverExecutorManager manager = TravelBehaviorFileSaverExecutorManager.getInstance();
            manager.runTask(saverTask);
        }
    }

    public static void saveArrivalInfo(ObaArrivalInfo[] info, String url, long serverTime, String stopId) {
        if (TravelBehaviorUtils.isUserParticipatingInStudy()) {
            ArrivalAndDepartureDataSaverTask saverTask = new ArrivalAndDepartureDataSaverTask(info,
                    serverTime, url, stopId, Application.get().getApplicationContext());
            TravelBehaviorFileSaverExecutorManager manager = TravelBehaviorFileSaverExecutorManager.getInstance();
            manager.runTask(saverTask);
        }
    }

    public static void saveTripPlan(TripPlan tripPlan, String url, Context applicationContext) {
        if (TravelBehaviorUtils.isUserParticipatingInStudy()) {
            TripPlanDataSaverTask dataSaverTask = new TripPlanDataSaverTask(tripPlan, url,
                    applicationContext);
            TravelBehaviorFileSaverExecutorManager executorManager =
                    TravelBehaviorFileSaverExecutorManager.getInstance();
            executorManager.runTask(dataSaverTask);
        }
    }
}
