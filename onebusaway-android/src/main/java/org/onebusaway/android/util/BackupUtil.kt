package org.onebusaway.android.util

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