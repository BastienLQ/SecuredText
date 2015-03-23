package org.smssecure.smssecure.dependencies;

import android.content.Context;

import org.smssecure.smssecure.Release;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.storage.SMSSecureAxolotlStore;
import org.smssecure.smssecure.jobs.AttachmentDownloadJob;
import org.smssecure.smssecure.jobs.CleanPreKeysJob;
import org.smssecure.smssecure.jobs.CreateSignedPreKeyJob;
import org.smssecure.smssecure.jobs.DeliveryReceiptJob;
import org.smssecure.smssecure.jobs.PushGroupSendJob;
import org.smssecure.smssecure.jobs.PushMediaSendJob;
import org.smssecure.smssecure.jobs.PushTextSendJob;
import org.smssecure.smssecure.jobs.RefreshPreKeysJob;
import org.smssecure.smssecure.push.SecurityEventListener;
import org.smssecure.smssecure.push.SMSSecurePushTrustStore;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.RecipientFormattingException;
import org.smssecure.smssecure.service.MessageRetrievalService;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.libaxolotl.util.guava.Optional;
import org.smssecure.textsecure.api.TextSecureAccountManager;
import org.smssecure.textsecure.api.TextSecureMessageReceiver;
import org.smssecure.textsecure.api.TextSecureMessageSender;
import org.smssecure.textsecure.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReceiptJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class})
public class SMSSecureCommunicationModule {

  private final Context context;

  public SMSSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides TextSecureAccountManager provideTextSecureAccountManager() {
    return new TextSecureAccountManager(Release.PUSH_URL,
                                        new SMSSecurePushTrustStore(context),
                                        SMSSecurePreferences.getLocalNumber(context),
                                        SMSSecurePreferences.getPushServerPassword(context));
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public TextSecureMessageSender create(MasterSecret masterSecret) {
        return new TextSecureMessageSender(Release.PUSH_URL,
                                           new SMSSecurePushTrustStore(context),
                                           SMSSecurePreferences.getLocalNumber(context),
                                           SMSSecurePreferences.getPushServerPassword(context),
                                           new SMSSecureAxolotlStore(context, masterSecret),
                                           Optional.of((TextSecureMessageSender.EventListener)
                                                           new SecurityEventListener(context)));
      }
    };
  }

  @Provides TextSecureMessageReceiver provideTextSecureMessageReceiver() {
    return new TextSecureMessageReceiver(Release.PUSH_URL,
                                         new SMSSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context));
  }

  public static interface TextSecureMessageSenderFactory {
    public TextSecureMessageSender create(MasterSecret masterSecret);
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return SMSSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return SMSSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return SMSSecurePreferences.getSignalingKey(context);
    }
  }

}
