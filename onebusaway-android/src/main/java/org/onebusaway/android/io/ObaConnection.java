/*
 * Copyright (C) 2012 Paul Watts (paulcwatts@gmail.com)
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
package org.onebusaway.android.io;

import java.io.IOException;
import java.io.Reader;

/**
 * Implements a basic connection object for ObaRequests.
 * These are created by the ObaConnectionFactory class.
 *
 * Under normal circumstances this is always implemented by
 * the ObaDefaultConnection class. In the unit tests, it is
 * replaced by the ObaMockConnection class.
 *
 * @author paulw
 */
public interface ObaConnection {

    public void disconnect();

    public Reader get() throws IOException;

    public Reader post(String string) throws IOException;

    public int getResponseCode() throws IOException;
}
