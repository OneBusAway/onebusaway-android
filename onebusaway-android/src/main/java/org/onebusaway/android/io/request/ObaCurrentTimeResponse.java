/*
 * Copyright (C) 2010 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.io.request;


public final class ObaCurrentTimeResponse extends ObaResponse {

    private static final class Entry {

        private static final Entry EMPTY_OBJECT = new Entry();

        private final long time;

        private final String readableTime;

        private Entry() {
            time = 0;
            readableTime = "";
        }
    }

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final Entry entry;

        private Data() {
            entry = Entry.EMPTY_OBJECT;
        }
    }

    private final Data data;

    private ObaCurrentTimeResponse() {
        data = Data.EMPTY_OBJECT;
    }

    /**
     * @return The time as milliseconds past the epoch.
     */
    public long getTime() {
        return data.entry.time;
    }

    /**
     * @return The time in ISO8601 format.
     */
    public String getReadableTime() {
        return data.entry.readableTime;
    }
}
