/*
 * Copyright (C) 2016 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com)
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
package org.onebusaway.android.tripservice;

import android.app.Notification;

/**
 * Interface that defines interactions between the Tasks (i.e., threads)- SchedularTask,
 * PollerTask,
 * NotifierTask, and CancelNotifyTask - and the Service that contains the running threads.
 */
public interface TaskContext {

    public void setNotification(int id, Notification notification);

    public void cancelNotification(int id);

    public void taskComplete();
}
