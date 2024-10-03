package org.onebusaway.android.io.request.reminders.model;

import androidx.annotation.NonNull;

public class ReminderResponse {
    String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @NonNull
    @Override
    public String toString() {
        return "ReminderResponse{" +
                "url='" + url + '\'' +
                '}';
    }
}
