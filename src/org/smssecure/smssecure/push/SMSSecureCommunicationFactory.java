package org.smssecure.smssecure.push;

import android.content.Context;

import org.smssecure.smssecure.Release;
import org.smssecure.smssecure.crypto.SecurityEvent;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.storage.SMSSecureAxolotlStore;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.libaxolotl.util.guava.Optional;
import org.smssecure.textsecure.api.TextSecureAccountManager;
import org.smssecure.textsecure.api.TextSecureMessageReceiver;
import org.smssecure.textsecure.api.TextSecureMessageSender;

import static org.smssecure.textsecure.api.TextSecureMessageSender.EventListener;

public class SMSSecureCommunicationFactory {

  public static TextSecureAccountManager createManager(Context context) {
    return new TextSecureAccountManager(Release.PUSH_URL,
                                        new SMSSecurePushTrustStore(context),
                                        SMSSecurePreferences.getLocalNumber(context),
                                        SMSSecurePreferences.getPushServerPassword(context));
  }

  public static TextSecureAccountManager createManager(Context context, String number, String password) {
    return new TextSecureAccountManager(Release.PUSH_URL, new SMSSecurePushTrustStore(context),
                                        number, password);
  }

}
