package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.crypto.AsymmetricMasterCipher;
import org.smssecure.smssecure.crypto.AsymmetricMasterSecret;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.MasterSecretUtil;
import org.smssecure.smssecure.crypto.SecurityEvent;
import org.smssecure.smssecure.crypto.SmsCipher;
import org.smssecure.smssecure.crypto.storage.SMSSecureAxolotlStore;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.EncryptingSmsDatabase;
import org.smssecure.smssecure.database.NoSuchMessageException;
import org.smssecure.smssecure.database.model.SmsMessageRecord;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.sms.IncomingEncryptedMessage;
import org.smssecure.smssecure.sms.IncomingEndSessionMessage;
import org.smssecure.smssecure.sms.IncomingKeyExchangeMessage;
import org.smssecure.smssecure.sms.IncomingPreKeyBundleMessage;
import org.smssecure.smssecure.sms.IncomingTextMessage;
import org.smssecure.smssecure.sms.MessageSender;
import org.smssecure.smssecure.sms.OutgoingKeyExchangeMessage;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.jobqueue.JobParameters;
import org.smssecure.libaxolotl.DuplicateMessageException;
import org.smssecure.libaxolotl.InvalidMessageException;
import org.smssecure.libaxolotl.InvalidVersionException;
import org.smssecure.libaxolotl.LegacyMessageException;
import org.smssecure.libaxolotl.NoSessionException;
import org.smssecure.libaxolotl.StaleKeyExchangeException;
import org.smssecure.libaxolotl.UntrustedIdentityException;
import org.smssecure.libaxolotl.util.guava.Optional;
import org.smssecure.textsecure.api.messages.TextSecureGroup;

import java.io.IOException;

public class SmsDecryptJob extends MasterSecretJob {

  private static final String TAG = SmsDecryptJob.class.getSimpleName();

  private final long messageId;

  public SmsDecryptJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    if (KeyCachingService.getMasterSecret(context) == null) {
      MessageNotifier.updateNotification(context, null, -2);
    }
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws NoSuchMessageException {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    try {
      SmsMessageRecord    record    = database.getMessage(masterSecret, messageId);
      IncomingTextMessage message   = createIncomingTextMessage(masterSecret, record);
      long                messageId = record.getId();
      long                threadId  = record.getThreadId();

      if      (message.isSecureMessage()) handleSecureMessage(masterSecret, messageId, threadId, message);
      else if (message.isPreKeyBundle())  handlePreKeyWhisperMessage(masterSecret, messageId, threadId, (IncomingPreKeyBundleMessage) message);
      else if (message.isKeyExchange())   handleKeyExchangeMessage(masterSecret, messageId, threadId, (IncomingKeyExchangeMessage) message);
      else if (message.isEndSession())    handleSecureMessage(masterSecret, messageId, threadId, message);
      else                                database.updateMessageBody(masterSecret, messageId, message.getMessageBody());

      MessageNotifier.updateNotification(context, masterSecret);
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
    SmsCipher             cipher    = new SmsCipher(new SMSSecureAxolotlStore(context, masterSecret));
    IncomingTextMessage   plaintext = cipher.decrypt(context, message);

    database.updateMessageBody(masterSecret, messageId, plaintext.getMessageBody());

    if (message.isEndSession()) SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
  }

  private void handlePreKeyWhisperMessage(MasterSecret masterSecret, long messageId, long threadId,
                                          IncomingPreKeyBundleMessage message)
      throws NoSessionException, DuplicateMessageException,
      InvalidMessageException, LegacyMessageException
  {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    try {
      SmsCipher                smsCipher = new SmsCipher(new SMSSecureAxolotlStore(context, masterSecret));
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

    if (SMSSecurePreferences.isAutoRespondKeyExchangeEnabled(context)) {
      try {
        SmsCipher                  cipher   = new SmsCipher(new SMSSecureAxolotlStore(context, masterSecret));
        OutgoingKeyExchangeMessage response = cipher.process(context, message);

        database.markAsProcessedKeyExchange(messageId);

        SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);

        if (response != null) {
          MessageSender.send(context, masterSecret, response, threadId, true);
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
      } catch (StaleKeyExchangeException e) {
        Log.w(TAG, e);
        database.markAsStaleKeyExchange(messageId);
      } catch (UntrustedIdentityException e) {
        Log.w(TAG, e);
      }
    }
  }

  private String getAsymmetricDecryptedBody(MasterSecret masterSecret, String body)
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
      plaintextBody = getAsymmetricDecryptedBody(masterSecret, record.getBody().getBody());
    }

    IncomingTextMessage message = new IncomingTextMessage(record.getRecipients().getPrimaryRecipient().getNumber(),
                                                          record.getRecipientDeviceId(),
                                                          record.getDateSent(),
                                                          plaintextBody,
                                                          Optional.<TextSecureGroup>absent());

    if (record.isEndSession()) {
      return new IncomingEndSessionMessage(message);
    } else if (record.isBundleKeyExchange()) {
      return new IncomingPreKeyBundleMessage(message, message.getMessageBody());
    } else if (record.isKeyExchange()) {
      return new IncomingKeyExchangeMessage(message, message.getMessageBody());
    } else if (record.isSecure()) {
      return new IncomingEncryptedMessage(message, message.getMessageBody());
    }

    return message;
  }
}
