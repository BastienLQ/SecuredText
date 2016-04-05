package org.smssecure.smssecure.attachments;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import ws.com.google.android.mms.ContentType;

public class UriAttachment extends Attachment {

  private final @NonNull Uri dataUri;
  private final @NonNull Uri thumbnailUri;

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState, long size, Context context) {
    this(uri, uri, contentType, transferState, size, UriAttachment.getFilenameFromUri(uri, context));
  }

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState, long size, String inputFilename) {
    this(uri, uri, contentType, transferState, size, inputFilename);
  }

  public UriAttachment(@NonNull Uri dataUri, @NonNull Uri thumbnailUri,
                       @NonNull String contentType, int transferState, long size, @Nullable String fileName)
  {
    super(contentType, transferState, size, null, null, null, fileName);
    this.dataUri      = dataUri;
    if(!ContentType.isVendorFileType(contentType)) {
      this.thumbnailUri = thumbnailUri;
    }
    else
      this.thumbnailUri = null;
  }

  @Override
  @NonNull
  public Uri getDataUri() {
    return dataUri;
  }

  @Override
  @NonNull
  public Uri getThumbnailUri() {
    return thumbnailUri;
  }

  @Override
  public boolean equals(Object other) {
    return other != null && other instanceof UriAttachment && ((UriAttachment) other).dataUri.equals(this.dataUri);
  }

  @Override
  public int hashCode() {
    return dataUri.hashCode();
  }

  public static String getFilenameFromUri(Uri uri, Context context) {
    String fileName = null;
    if (uri != null && uri.getScheme() != null && uri.getScheme().equals("content")) {
      Log.i("UriAttachment", "contenturi: "+uri.toString());
      Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
      } finally {
        if(cursor != null)
          cursor.close();
      }
    }
    if (fileName == null) {
      fileName = uri.getPath();
      int cut = fileName != null ? fileName.lastIndexOf('/') : -1;
      if (cut != -1) {
        fileName = fileName.substring(cut + 1);
      }
    }
    return fileName;
  }
}
