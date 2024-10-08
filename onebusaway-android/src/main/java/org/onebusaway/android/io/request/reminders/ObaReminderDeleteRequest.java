package org.onebusaway.android.io.request.reminders;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import android.annotation.SuppressLint;
import android.util.Log;

/**
 * This class handles sending an HTTP DELETE request to remove a reminder alarm asynchronously.
 * It uses `HttpURLConnection` and provides callbacks to notify success or failure of the operation.
 */

public class ObaReminderDeleteRequest {

    private static final String TAG = "ObaReminderDeleteRequest";

    @SuppressLint("LongLogTag")
    public void sendDeleteRequest(String alarmDeletePath, DeleteRequestListener listener) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL deleteUrl = new URL(alarmDeletePath);
                connection = (HttpURLConnection) deleteUrl.openConnection();
                connection.setRequestMethod("DELETE");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    listener.onDeleteSuccess();
                } else {
                    listener.onDeleteFailed();
                }

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error during DELETE request: " + e.getMessage());
                listener.onDeleteFailed();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }


}
