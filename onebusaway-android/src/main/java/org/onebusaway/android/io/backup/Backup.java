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
import android.os.Build;
import android.os.Environment;

import org.apache.commons.io.FileUtils;
import org.onebusaway.android.provider.ObaContract;
import org.onebusaway.android.provider.ObaProvider;

import java.io.File;
import java.io.IOException;

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

    private static final String FILE_NAME = "OneBusAway.backup";

    private static final String DIRECTORY_NAME = "OBABackups";

    private static File getDB(Context context) {
        return ObaProvider.getDatabasePath(context);
    }

    private static File getBackup() {
        File backupDir = getBackupDirectory();
        return new File(backupDir, FILE_NAME);
    }

    /**
     * Performs a backup to the SD card.
     */
    public static String backup(Context context) throws IOException {
        // We need two things:
        // 1. The path to the database;
        // 2. The path on the SD card to the backup file.
        File backupPath = getBackup();
        FileUtils.copyFile(getDB(context), backupPath);
        return backupPath.getAbsolutePath();
    }

    /**
     * Performs a restore from the SD card.
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

    public static boolean isRestoreAvailable() {
        return getBackup().exists();
    }

    public static File getBackupDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/" + DIRECTORY_NAME);
        } else {
            return Environment.getExternalStoragePublicDirectory(DIRECTORY_NAME);
        }
    }
}
