package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.smssecure.smssecure.crypto.AsymmetricMasterCipher;
import org.smssecure.smssecure.crypto.AsymmetricMasterSecret;
import org.smssecure.smssecure.crypto.KeyExchangeInitiator;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.MasterSecretUtil;
import org.smssecure.smssecure.crypto.SecurityEvent;
import org.smssecure.smssecure.crypto.SmsCipher;
import org.smssecure.smssecure.crypto.storage.SilenceSignalProtocolStore;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.EncryptingSmsDatabase;
import org.smssecure.smssecure.database.NoSuchMessageException;
import org.smssecure.smssecure.database.model.SmsMessageRecord;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.sms.IncomingEncryptedMessage;
import org.smssecure.smssecure.sms.IncomingEndSessionMessage;
import org.smssecure.smssecure.sms.IncomingKeyExchangeMessage;
import org.smssecure.smssecure.sms.IncomingPreKeyBundleMessage;
import org.smssecure.smssecure.sms.IncomingTextMessage;
import org.smssecure.smssecure.sms.IncomingXmppExchangeMessage;
import org.smssecure.smssecure.sms.MessageSender;
import org.smssecure.smssecure.sms.OutgoingKeyExchangeMessage;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.smssecure.smssecure.util.SilencePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.StaleKeyExchangeException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;

public class SmsDecryptJob extends MasterSecretJob {

  private static final String TAG = SmsDecryptJob.class.getSimpleName();

  private final long    messageId;
  private final boolean manualOverride;
  private final Boolean isReceivedWhenLocked;

  public SmsDecryptJob(Context context, long messageId, boolean manualOverride, boolean isReceivedWhenLocked) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());

    this.messageId            = messageId;
    this.manualOverride       = manualOverride;
    this.isReceivedWhenLocked = isReceivedWhenLocked;

    Log.w(TAG, "manualOverride: " + manualOverride);
    Log.w(TAG, "isReceivedWhenLocked: " + isReceivedWhenLocked);
  }

  public SmsDecryptJob(Context context, long messageId) {
    this(context, messageId, false, false);
  }

  public SmsDecryptJob(Context context, long messageId, boolean isReceivedWhenLocked) {
    this(context, messageId, false, isReceivedWhenLocked);
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws NoSuchMessageException {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    try {
      SmsMessageRecord    record    = database.getMessage(masterSecret, messageId);
      IncomingTextMessage message   = createIncomingTextMessage(masterSecret, record);
      long                messageId = record.getId();
      long                threadId  = record.getThreadId();

      if      (message.isSecureMessage()) handleSecureMessage(masterSecret, messageId, threadId, message);
      else if (message.isPreKeyBundle())  handlePreKeySignalMessage(masterSecret, messageId, threadId, (IncomingPreKeyBundleMessage) message);
      else if (message.isKeyExchange())   handleKeyExchangeMessage(masterSecret, messageId, threadId, (IncomingKeyExchangeMessage) message);
      else if (message.isEndSession())    handleSecureMessage(masterSecret, messageId, threadId, message);
      else if (message.isXmppExchange())  handleXmppExchangeMessage(masterSecret, messageId, threadId, (IncomingXmppExchangeMessage) message);
      else                                database.updateMessageBody(masterSecret, messageId, message.getMessageBody());

      if (!isReceivedWhenLocked) {
        MessageNotifier.updateNotification(context, masterSecret, threadId);
      } else {
        MessageNotifier.updateNotification(context, masterSecret);
      }
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      database.markAsLegacyVersion(messageId);
    } catch (InvalidMessageException e) {
      Log.w(TAG, e);
      database.markAsDecryptFailed(messageId);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      database.markAsDecryptDuplicate(messageId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      database.markAsNoSession(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    // TODO
  }

  private void handleSecureMessage(MasterSecret masterSecret, long messageId, long threadId,
                                   IncomingTextMessage message)
      throws NoSessionException, DuplicateMessageException,
      InvalidMessageException, LegacyMessageException
  {
    EncryptingSmsDatabase database  = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsCipher             cipher    = new SmsCipher(new SilenceSignalProtocolStore(context, masterSecret, message.getSubscriptionId()));
    IncomingTextMessage   plaintext = cipher.decrypt(context, message);

    database.updateMessageBody(masterSecret, messageId, plaintext.getMessageBody());

    if (message.isEndSession()) SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
  }

  private void handlePreKeySignalMessage(MasterSecret masterSecret, long messageId, long threadId,
                                          IncomingPreKeyBundleMessage message)
      throws NoSessionException, DuplicateMessageException,
      InvalidMessageException, LegacyMessageException
  {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    try {
      SmsCipher                smsCipher = new SmsCipher(new SilenceSignalProtocolStore(context, masterSecret, message.getSubscriptionId()));
      IncomingEncryptedMessage plaintext = smsCipher.decrypt(context, message);

      database.updateBundleMessageBody(masterSecret, messageId, plaintext.getMessageBody());

      SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      database.markAsInvalidVersionKeyExchange(messageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
    }
  }

  private void handleKeyExchangeMessage(MasterSecret masterSecret, long messageId, long threadId,
                                      IncomingKeyExchangeMessage message)
  {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    try {
      SmsCipher                  cipher   = new SmsCipher(new SilenceSignalProtocolStore(context, masterSecret, message.getSubscriptionId()));
      OutgoingKeyExchangeMessage response = cipher.process(context, message);

      if (shouldSend()) {
        database.markAsProcessedKeyExchange(messageId);
        SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);

        if (response != null) {
          MessageSender.send(context, masterSecret, response, threadId, true);
        }
      }
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      database.markAsInvalidVersionKeyExchange(messageId);
    } catch (InvalidMessageException e) {
      Log.w(TAG, e);
      database.markAsCorruptKeyExchange(messageId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      database.markAsLegacyVersion(messageId);
      if (shouldSend()) {
        Log.w(TAG, "Legacy message found, sending updated key exchange message...");
        Recipients recipients = RecipientFactory.getRecipientsFromString(context, message.getSender(), false);
        KeyExchangeInitiator.initiate(context, masterSecret, recipients, false, message.getSubscriptionId());
        database.markAsProcessedKeyExchange(messageId);
      }
    } catch (StaleKeyExchangeException e) {
      Log.w(TAG, e);
      database.markAsStaleKeyExchange(messageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
    }
  }

  private boolean shouldSend() {
    return (SilencePreferences.isAutoRespondKeyExchangeEnabled(context) || manualOverride);
  }

  private void handleXmppExchangeMessage(MasterSecret masterSecret, long messageId, long threadId,
                                         IncomingXmppExchangeMessage message)
     throws NoSessionException, DuplicateMessageException, InvalidMessageException, LegacyMessageException
  {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
    database.markAsXmppExchange(messageId);
  }

  private String getAsymmetricDecryptedBody(MasterSecret masterSecret, String body, int subscriptionId)
      throws InvalidMessageException
  {
    try {
      AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret);
      AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(asymmetricMasterSecret);

      return asymmetricMasterCipher.decryptBody(body);
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }

  private IncomingTextMessage createIncomingTextMessage(MasterSecret masterSecret, SmsMessageRecord record)
      throws InvalidMessageException
  {
    String plaintextBody = record.getBody().getBody();

    if (record.isAsymmetricEncryption()) {
      plaintextBody = getAsymmetricDecryptedBody(masterSecret, record.getBody().getBody(), record.getSubscriptionId());
    }

    IncomingTextMessage message = new IncomingTextMessage(record.getRecipients().getPrimaryRecipient().getNumber(),
                                                          record.getRecipientDeviceId(),
                                                          record.getDateSent(),
                                                          plaintextBody,
                                                          record.getSubscriptionId());

    if (record.isEndSession()) {
      return new IncomingEndSessionMessage(message);
    } else if (record.isBundleKeyExchange()) {
      return new IncomingPreKeyBundleMessage(message, message.getMessageBody());
    } else if (record.isKeyExchange()) {
      return new IncomingKeyExchangeMessage(message, message.getMessageBody());
    } else if (record.isXmppExchange()) {
      return new IncomingXmppExchangeMessage(message, message.getMessageBody());
    } else if (record.isSecure()) {
      return new IncomingEncryptedMessage(message, message.getMessageBody());
    }

    return message;
  }
}
