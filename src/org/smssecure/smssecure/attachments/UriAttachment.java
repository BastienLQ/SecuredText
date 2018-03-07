package org.smssecure.smssecure.attachments;

import android.net.Uri;
import android.support.annotation.NonNull;

public class UriAttachment extends Attachment {

  private final @NonNull Uri dataUri;
  private final @NonNull Uri thumbnailUri;

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState) {
    this(uri, contentType, transferState, 0);
  }

  public UriAttachment(@NonNull Uri uri, @NonNull String contentType, int transferState, long size) {
    this(uri, uri, contentType, transferState, size);
  }

  public UriAttachment(@NonNull Uri dataUri, @NonNull Uri thumbnailUri,
                       @NonNull String contentType, int transferState, long size)
  {
    super(contentType, transferState, size, null, null, null);
    this.dataUri      = dataUri;
    this.thumbnailUri = thumbnailUri;
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
}
