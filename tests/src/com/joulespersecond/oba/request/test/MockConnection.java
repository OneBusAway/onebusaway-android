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
package com.joulespersecond.oba.request.test;

import com.joulespersecond.oba.ObaConnection;

import android.content.Context;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;

public class MockConnection implements ObaConnection {
    private final Context mContext;
    private final URL mUrl;

    MockConnection(Context context, URL url) {
        mContext = context;
        mUrl = url;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public Reader get() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Reader post(String string) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }
}
