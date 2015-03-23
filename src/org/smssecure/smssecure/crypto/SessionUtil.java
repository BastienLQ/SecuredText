package org.smssecure.smssecure.crypto;

import android.content.Context;
import android.support.annotation.NonNull;

import org.smssecure.smssecure.crypto.storage.SMSSecureSessionStore;
import org.smssecure.smssecure.recipients.Recipient;
import org.securesms.libaxolotl.AxolotlAddress;
import org.securesms.libaxolotl.state.SessionStore;
import org.securesms.textsecure.api.push.TextSecureAddress;

public class SessionUtil {

  public static boolean hasSession(Context context, MasterSecret masterSecret, Recipient recipient) {
    return hasSession(context, masterSecret, recipient.getNumber());
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String number) {
    SessionStore   sessionStore   = new SMSSecureSessionStore(context, masterSecret);
    AxolotlAddress axolotlAddress = new AxolotlAddress(number, TextSecureAddress.DEFAULT_DEVICE_ID);

    return sessionStore.containsSession(axolotlAddress);
  }
}
