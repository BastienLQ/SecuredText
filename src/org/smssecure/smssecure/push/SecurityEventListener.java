package org.smssecure.smssecure.push;

import android.content.Context;

import org.smssecure.smssecure.crypto.SecurityEvent;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.securesms.textsecure.api.TextSecureMessageSender;
import org.securesms.textsecure.api.push.TextSecureAddress;

public class SecurityEventListener implements TextSecureMessageSender.EventListener {

  private static final String TAG = SecurityEventListener.class.getSimpleName();

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(TextSecureAddress textSecureAddress) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, textSecureAddress.getNumber(), false);
    long       threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
  }
}
