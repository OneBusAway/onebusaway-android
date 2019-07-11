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
package org.onebusaway.android.travelbehavior.io.worker;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;

import org.apache.commons.io.IOUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaConnection;
import org.onebusaway.android.io.ObaDefaultConnectionFactory;
import org.onebusaway.android.travelbehavior.TravelBehaviorManager;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;
import org.onebusaway.android.travelbehavior.io.TravelBehaviorFileSaverExecutorManager;
import org.onebusaway.android.travelbehavior.utils.TravelBehaviorFirebaseIOUtils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.ResolvableFuture;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

public class RegisterTravelBehaviorParticipantWorker extends ListenableWorker {

    private static final String TAG = "RegisterTravelUser";

    private ResolvableFuture<Result> mFuture;

    public RegisterTravelBehaviorParticipantWorker(@NonNull Context context,
                                                   @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        mFuture = ResolvableFuture.create();
        registerUser();
        return mFuture;
    }

    private void registerUser() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Log.d(TAG, "Initializing anonymous Firebase user");
        TravelBehaviorFileSaverExecutorManager manager =
                TravelBehaviorFileSaverExecutorManager.getInstance();
        auth.signInAnonymously()
                .addOnCompleteListener(manager.getThreadPoolExecutor(), task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase user init success ID: " + auth.getUid());
                        saveEmailAddress(auth.getUid());
                        TravelBehaviorFirebaseIOUtils.initFirebaseUserWithId(auth.getUid());
                    } else {
                        TravelBehaviorFirebaseIOUtils.logErrorMessage(task.getException(),
                                "Firebase user init failed: ");
                        mFuture.set(Result.failure());
                    }
                });
    }

    private void saveEmailAddress(String uid) {
        String email = getInputData().getString(TravelBehaviorConstants.USER_EMAIL);
        Uri uri = buildUri(uid, email);
        try {
            ObaConnection connection = ObaDefaultConnectionFactory.getInstance().newConnection(uri);
            Reader reader = connection.get();
            String result = IOUtils.toString(reader);
            if (TravelBehaviorConstants.PARTICIPANT_SERVICE_RESULT.equals(result)) {
                TravelBehaviorManager.optInUser(uid);
                TravelBehaviorManager.startCollectingData(getApplicationContext());
                mFuture.set(Result.success());
            } else {
                mFuture.set(Result.failure());
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            mFuture.set(Result.failure());
        }
    }

    private Uri buildUri(String uid, String email) {
        return Uri.parse(Application.get().getResources().getString(R.string.
                travel_behavior_participants_url)).buildUpon().appendQueryParameter("id", uid)
                .appendQueryParameter("email", email).build();
    }
}
