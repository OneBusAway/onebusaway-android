package org.onebusaway.android.io.request.reminders;

/**
 * Listener interface for handling the result of a delete request.
 * This interface should be implemented by any class that wants to respond
 * to the success or failure of a delete operation related to reminders.
 */
public interface DeleteRequestListener {
    void onDeleteSuccess();

    void onDeleteFailed();
}
