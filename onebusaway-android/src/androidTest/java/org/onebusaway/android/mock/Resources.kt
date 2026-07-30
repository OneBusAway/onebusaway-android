package org.onebusaway.android.mock

import android.content.Context
import android.net.Uri
import java.io.InputStreamReader
import java.io.Reader
import org.onebusaway.android.BuildConfig

object Resources {
    private val testRawUri = Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}.test/raw/")

    fun getTestUri(path: String): Uri = Uri.withAppendedPath(testRawUri, path)

    fun read(context: Context, uri: Uri): Reader = InputStreamReader(requireNotNull(context.contentResolver.openInputStream(uri)), Charsets.UTF_8)
}
