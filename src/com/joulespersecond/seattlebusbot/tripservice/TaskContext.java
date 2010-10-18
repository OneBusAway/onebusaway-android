package com.joulespersecond.seattlebusbot.tripservice;

import android.app.Notification;

public interface TaskContext {
    public void setNotification(int id, Notification notification);
    public void cancelNotification(int id);
    public Notification getNotification(int id);

    public void taskComplete();
}
