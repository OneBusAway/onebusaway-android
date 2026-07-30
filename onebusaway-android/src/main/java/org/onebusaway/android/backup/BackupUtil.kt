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
fun uriToTempFile(context: Context, uri: Uri): File? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        File.createTempFile("temp", "", context.cacheDir).also { file ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }
} catch (e: Exception) {
    e.printStackTrace()
    null
}
