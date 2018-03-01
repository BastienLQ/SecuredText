/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure.database;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class EncryptedBackupExporter {

  private static final String TAG = EncryptedBackupExporter.class.getSimpleName();

  public static void exportToStorage(Context context) throws NoExternalStorageException, IOException {
    verifyExternalStorageForExport();
    exportDirectory(context, "");
  }

  public static void importFromStorage(Context context) throws NoExternalStorageException, IOException {
    verifyExternalStorageForImport();
    importDirectory(context, "");
  }

  private static String getExportDirectoryPath() {
    File sdDirectory  = Environment.getExternalStorageDirectory();
    return sdDirectory.getAbsolutePath() + File.separator + "SilenceExport";
  }

  private static void verifyExternalStorageForExport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canWrite())
      throw new NoExternalStorageException();

    String exportDirectoryPath = getExportDirectoryPath();
    File exportDirectory       = new File(exportDirectoryPath);

    if (!exportDirectory.exists())
      exportDirectory.mkdir();
  }

  private static void verifyExternalStorageForImport() throws NoExternalStorageException {
    if (!Environment.getExternalStorageDirectory().canRead()) {
      Log.w(TAG, "Cannot get external storage directory!");
      throw new NoExternalStorageException();
    }
    if (!new File(getExportDirectoryPath()).exists()) {
      Log.w(TAG, "Cannot get export directory path \"" + getExportDirectoryPath() + "\"!");
      throw new NoExternalStorageException();
    }
  }

  private static void migrateFile(File from, File to) {
    try {
      if (from.exists()) {
        FileChannel source      = new FileInputStream(from).getChannel();
        FileChannel destination = new FileOutputStream(to).getChannel();

        destination.transferFrom(source, 0, source.size());
        source.close();
        destination.close();
      }
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    }
  }

  private static void exportDirectory(Context context, String directoryName) throws IOException {
    if (!directoryName.equals("/lib")) {
      File directory       = new File(context.getFilesDir().getParent() + File.separatorChar + directoryName);
      File exportDirectory = new File(getExportDirectoryPath() + File.separatorChar + directoryName);

      if (directory.exists() && directory.isDirectory()) {
        exportDirectory.mkdirs();

        File[] contents = directory.listFiles();

        if (contents == null)
          throw new IOException("directory.listFiles() is null for " + context.getFilesDir().getParent() + File.separatorChar + directoryName + "!");

          for (File localFile : contents) {
              if (localFile.isFile() && !localFile.getAbsolutePath().contains("libcurve25519.so")) {
                  File exportedFile = new File(exportDirectory.getAbsolutePath() + File.separator + localFile.getName());
                  migrateFile(localFile, exportedFile);
              } else {
                  exportDirectory(context, directoryName + File.separator + localFile.getName());
              }
          }
      } else {
        Log.w(TAG, "Could not find directory: " + directory.getAbsolutePath() + " (or it is not a directory)");
      }
    }
  }

  private static void importDirectory(Context context, String directoryName) throws IOException {
    File directory       = new File(getExportDirectoryPath() + File.separator + directoryName);
    File importDirectory = new File(context.getFilesDir().getParent() + File.separator + directoryName);

    if (directory.exists() && directory.isDirectory()) {
      importDirectory.mkdirs();

      File[] contents = directory.listFiles();

      for (File exportedFile : contents) {
        if (exportedFile.isFile()) {
          File localFile = new File(importDirectory.getAbsolutePath() + File.separator + exportedFile.getName());
          migrateFile(exportedFile, localFile);
        } else if (exportedFile.isDirectory()) {
          importDirectory(context, directoryName + File.separator + exportedFile.getName());
        }
      }
    }
  }
}
