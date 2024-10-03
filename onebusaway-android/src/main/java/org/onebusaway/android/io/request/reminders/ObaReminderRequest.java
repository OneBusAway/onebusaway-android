package org.onebusaway.android.io.request.reminders;

import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.io.request.reminders.model.ReminderResponse;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.Callable;

import androidx.annotation.NonNull;

/**
 * Represents a request to create an alarm.
 * This class is responsible for creating and executing a POST request
 * to create bus reminder alarms.
 */

public final class ObaReminderRequest extends RequestBase implements Callable<ReminderResponse> {

    private final ReminderRequestListener listener;
    private static final String TAG = "ObaReminderRequest";

    private ObaReminderRequest(Uri uri, String body, ReminderRequestListener listener) {
        super(uri, body);
        this.listener = listener;
    }

    public static class Builder extends PostBuilderBase {

        private static Uri URI = null;
        private ReminderRequestListener listener;

        public Builder(Context context, String path) {
            super(context, path);
            URI = Uri.parse(path);
        }

        public Builder setStopID(String stopID) {
            try {
                mPostData.appendQueryParameter("stop_id", stopID);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setServiceDate(long serviceDate) {
            try {
                mPostData.appendQueryParameter("service_date", String.valueOf(serviceDate));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setStopSequence(int stopSequence) {
            try {
                mPostData.appendQueryParameter("stop_sequence", String.valueOf(stopSequence));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setTripID(String tripID) {
            try {
                mPostData.appendQueryParameter("trip_id", tripID);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setUserPushId(String userPushId) {
            try {
                mPostData.appendQueryParameter("user_push_id", userPushId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }


        public Builder setSecondsBefore(int secondsBefore) {
            try {
                mPostData.appendQueryParameter("seconds_before", String.valueOf(secondsBefore));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return this;
        }

        public Builder setListener(ReminderRequestListener listener) {
            this.listener = listener;
            return this;
        }

        @Override
        public String buildPostData() {
            return mPostData.build().getEncodedQuery();
        }

        public ObaReminderRequest build() {
            return new ObaReminderRequest(URI, buildPostData(), listener);
        }
    }

    public static ObaReminderRequest newRequest(Context context, String path) {
        return new Builder(context, path).build();
    }

    @Override
    public ReminderResponse call() {
        ReminderResponse response = call(ReminderResponse.class);
        if (response != null) {
            Log.d(TAG, "Response received: " + response);
            if (listener != null) {
                listener.onReminderResponseReceived(response);
            }
        } else {
            Log.d(TAG, "No response received or response is null");
            if (listener != null) {
                listener.onReminderResponseFailed();
            }
        }
        return response;
    }

    @NonNull
    @Override
    public String toString() {
        return TAG + "[mUri=" + mUri + "]";
    }
}
