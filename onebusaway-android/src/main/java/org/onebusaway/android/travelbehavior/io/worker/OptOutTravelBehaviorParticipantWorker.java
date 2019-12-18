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

import org.apache.commons.io.IOUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.ObaConnection;
import org.onebusaway.android.io.ObaDefaultConnectionFactory;
import org.onebusaway.android.travelbehavior.constants.TravelBehaviorConstants;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.Reader;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class OptOutTravelBehaviorParticipantWorker extends Worker {

    private static final String TAG = "OptOutTravelUser";


    public OptOutTravelBehaviorParticipantWorker(@NonNull Context context,
                                                 @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String uid = getInputData().getString(TravelBehaviorConstants.USER_ID);
        optOutUser(uid);
        return Result.success();
    }

    private void optOutUser(String uid) {
        Uri uri = buildUri(uid);
        try {
            ObaConnection connection = ObaDefaultConnectionFactory.getInstance().newConnection(uri);
            Reader reader = connection.get();
            String result = IOUtils.toString(reader);
            if (TravelBehaviorConstants.PARTICIPANT_SERVICE_RESULT.equals(result)) {
                Log.d(TAG, "Opt-out user success");
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    private Uri buildUri(String uid) {
        return Uri.parse(Application.get().getResources().getString(R.string.
                travel_behavior_participants_opt_out_url)).buildUpon().
                appendQueryParameter("id", uid).build();
    }
}
