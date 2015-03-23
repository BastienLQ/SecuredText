package org.smssecure.smssecure.push;

import android.content.Context;

import org.smssecure.smssecure.R;
import org.securesms.textsecure.api.push.TrustStore;
import org.securesms.textsecure.internal.push.PushServiceSocket;

import java.io.InputStream;

public class SMSSecurePushTrustStore implements TrustStore {

  private final Context context;

  public SMSSecurePushTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.whisper);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}
