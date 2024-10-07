package org.onebusaway.android.io.request.reminders;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.request.RequestBase;
import org.onebusaway.android.io.request.reminders.model.ReminderResponse;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.concurrent.Callable;

import androidx.annotation.NonNull;

/**
 * This class represent the POST request to create a arrivals alarm.
 */

public final class ObaReminderRequest extends RequestBase implements Callable<ReminderResponse> {

    private final ReminderRequestListener listener;
    private static final String TAG = "ObaReminderRequest";

    private ObaReminderRequest(Uri uri, String body, ReminderRequestListener listener) {
        super(uri, body);
        this.listener = listener;
    }

    public static class Builder extends PostBuilderBase {

        private final Uri.Builder uriBuilder;
        private ReminderRequestListener listener;

        public Builder(Context context) {
            super(context, null);
            String baseUrl = context.getString(R.string.arrivals_reminders_api_url);
            Application app = Application.get();
            uriBuilder = Uri.parse(baseUrl).buildUpon();
            buildAPIURL(String.valueOf(app.getCurrentRegion().getId()));
        }

        public void buildAPIURL(String regionId) {
            uriBuilder.appendPath(regionId).appendPath("alarms");
        }

        public Builder setStopID(String stopID) {
            mPostData.appendQueryParameter("stop_id", stopID);
            return this;
        }

        public Builder setServiceDate(long serviceDate) {
            mPostData.appendQueryParameter("service_date", String.valueOf(serviceDate));
            return this;
        }

        public Builder setStopSequence(int stopSequence) {
            mPostData.appendQueryParameter("stop_sequence", String.valueOf(stopSequence));
            return this;
        }

        public Builder setTripID(String tripID) {
            mPostData.appendQueryParameter("trip_id", tripID);
            return this;
        }

        public Builder setUserPushId(String userPushId) {
            mPostData.appendQueryParameter("user_push_id", userPushId);
            return this;
        }

        public Builder setSecondsBefore(int secondsBefore) {
            mPostData.appendQueryParameter("seconds_before", String.valueOf(secondsBefore));
            return this;
        }

        public Builder setVehicleID(String vehicleID) {
            if (vehicleID != null) {
                mPostData.appendQueryParameter("vehicle_id", vehicleID);
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
            Uri finalUri = uriBuilder.build();
            return new ObaReminderRequest(finalUri, buildPostData(), listener);
        }
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
