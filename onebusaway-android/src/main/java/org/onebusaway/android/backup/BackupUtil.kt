/*
 * Copyright The OneBusAway Authors.
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

package org.onebusaway.android.backup

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Converts the provided [uri] to a temporary file. This is needed to support the way that Android
 * references documents following targeting Android 11 (i.e., you can't just do new File(uri.getPath())).
 *
 * You MUST delete this temporary file yourself after use.
 */
fun uriToTempFile(context: Context, uri: Uri): File? {
    return try {
        val stream = context.contentResolver.openInputStream(uri)
        val file = File.createTempFile("temp", "", context.cacheDir)
        org.apache.commons.io.FileUtils.copyInputStreamToFile(stream,file)
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}