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
package org.onebusaway.android.api

/**
 * OBA-specific API constants: the client-side error sentinels the app maps onto error messages,
 * protocol versions, and per-request identity. (Standard HTTP statuses returned in the envelope's
 * `code` field are referenced directly from [java.net.HttpURLConnection] at the call sites.)
 */
object ObaApi {

    // OBA-specific client-side sentinels (not HTTP statuses) the app maps onto error messages.
    const val OBA_OUT_OF_MEMORY = 666

    const val OBA_IO_EXCEPTION = 700

    const val VERSION1 = "1"

    const val VERSION2 = "2"

    /** The OBA REST API key appended to every request. */
    const val API_KEY = "v1_BktoDJ2gJlu6nLM6LsT9H8IUbWc=cGF1bGN3YXR0c0BnbWFpbC5jb20="

    /** Preferences key holding the persisted per-install app UID (sent as `app_uid`). */
    const val APP_UID = "app_uid"
}
