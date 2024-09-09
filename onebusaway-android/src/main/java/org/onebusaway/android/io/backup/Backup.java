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
package org.onebusaway.android.io.backup;

import static org.onebusaway.android.util.BackupUtilKt.uriToTempFile;

import android.content.ContentProviderClient;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.R;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.provider.ObaProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Big note, that this is currently fairly unsafe.
 * There's no thread safety at all to make sure the database file
 * isn't being written on another thread while we are copying the file.
 * If that becomes an issue, I would rather just dump everything
 * into a CSV file, since we know that reading/writing to the
 * ContentProvider interface is going to be threadsafe.
 *
 * @author paulw
 */
public final class Backup {

    public static final String FILE_NAME = "OneBusAway.backup";

    private static File getDB(Context context) {
        return ObaProvider.getDatabasePath(context);
    }

    /**
     * Initiates a backup process, allowing the user to choose a location
     * (such as the Documents directory) to save the backup file.
     */
    public static void backup(Context context,Uri uri) throws IOException{
        try (InputStream inputStream = new FileInputStream(getDB(context));
             OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                if (outputStream != null) {
                    outputStream.write(buffer, 0, length);
                }
            }
            if (outputStream != null) {
                outputStream.flush();
            }
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_saved),
                    Toast.LENGTH_LONG).show();
            Log.d("Backup", "Database backup saved successfully to: " + uri);
        } catch (IOException e) {
            Toast.makeText(context,
                    context.getString(R.string.preferences_db_save_error, e.getMessage()),
                    Toast.LENGTH_LONG).show();
            Log.e("Backup", "Error saving database backup", e);
        }
    }

    /**
     * Restores data from the location where the user saved the backup.
     * @param uri URI to the backup file, as returned by the system UI picker. Following targeting
     *            Android 11 we can't access this directory and need to rely on the system UI picker.
     */
    public static void restore(Context context, Uri uri) throws IOException {
        File dbPath = getDB(context);
        File backupPath = uriToTempFile(context, uri);

        // At least here we can decide that the database is closed.
        ContentProviderClient client = null;
        try {
            client = context.getContentResolver()
                    .acquireContentProviderClient(ObaContract.AUTHORITY);
            ObaProvider provider = (ObaProvider) client.getLocalContentProvider();
            provider.closeDB();

            FileUtils.copyFile(backupPath, dbPath);

        } finally {
            if (client != null) {
                client.release();
            }
            if (backupPath != null) {
                backupPath.delete();
            }
        }
    }

}
