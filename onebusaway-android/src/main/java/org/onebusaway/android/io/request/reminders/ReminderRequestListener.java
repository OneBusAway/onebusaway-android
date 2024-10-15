package org.onebusaway.android.io.request.reminders;

import org.onebusaway.android.io.request.reminders.model.ReminderResponse;
import org.onebusaway.android.io.request.survey.model.StudyResponse;

/**
 * Interface to handle the callbacks for create-alarm-related requests.
 * Implementations of this interface should define how to handle
 * successful and failed alarms responses.
 */

public interface ReminderRequestListener {
    void onReminderResponseReceived(ReminderResponse response);
    void onReminderResponseFailed();
}