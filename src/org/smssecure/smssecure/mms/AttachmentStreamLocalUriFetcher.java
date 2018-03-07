package org.smssecure.smssecure.mms;

import android.util.Log;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;

import org.smssecure.smssecure.crypto.AttachmentCipherInputStream;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AttachmentStreamLocalUriFetcher implements DataFetcher<InputStream> {
  private static final String TAG = AttachmentStreamLocalUriFetcher.class.getSimpleName();
  private File        attachment;
  private byte[]      key;
  private InputStream is;

  public AttachmentStreamLocalUriFetcher(File attachment, byte[] key) {
    this.attachment = attachment;
    this.key        = key;
  }

  @Override public InputStream loadData(Priority priority) throws Exception {
    is = new AttachmentCipherInputStream(attachment, key, Optional.<byte[]>absent());
    return is;
  }

  @Override public void cleanup() {
    try {
      if (is != null) is.close();
      is = null;
    } catch (IOException ioe) {
      Log.w(TAG, "ioe");
    }
  }

  @Override public String getId() {
    return AttachmentStreamLocalUriFetcher.class.getCanonicalName() + "::" + attachment.getAbsolutePath();
  }

  @Override public void cancel() {

  }
}
