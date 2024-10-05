package org.onebusaway.android.app;


import com.onesignal.notifications.IActionButton;
import com.onesignal.notifications.IDisplayableMutableNotification;
import com.onesignal.notifications.INotificationReceivedEvent;
import com.onesignal.notifications.INotificationServiceExtension;

import androidx.annotation.Keep;

@Keep
public class NotificationServiceExtension implements INotificationServiceExtension {

   @Override
   public void onNotificationReceived(INotificationReceivedEvent event) {
      IDisplayableMutableNotification notification = event.getNotification();

   }
}
