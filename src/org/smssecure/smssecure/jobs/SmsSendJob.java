package org.smssecure.smssecure.jobs;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SmsCipher;
import org.smssecure.smssecure.crypto.storage.SilenceSignalProtocolStore;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.EncryptingSmsDatabase;
import org.smssecure.smssecure.database.NoSuchMessageException;
import org.smssecure.smssecure.database.SmsDatabase;
import org.smssecure.smssecure.database.model.SmsMessageRecord;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.jobs.requirements.NetworkOrServiceRequirement;
import org.smssecure.smssecure.jobs.requirements.ServiceRequirement;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.service.SmsDeliveryListener;
import org.smssecure.smssecure.sms.MultipartSmsMessageHandler;
import org.smssecure.smssecure.sms.OutgoingTextMessage;
import org.smssecure.smssecure.transport.UndeliverableMessageException;
import org.smssecure.smssecure.util.NumberUtil;
import org.smssecure.smssecure.util.SilencePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.NoSessionException;

import java.util.ArrayList;

public class SmsSendJob extends SendJob {

  private static final String TAG = SmsSendJob.class.getSimpleName();

  private final long messageId;

  public SmsSendJob(Context context, long messageId, String name) {
    super(context, constructParameters(context, name));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onSend(MasterSecret masterSecret) throws NoSuchMessageException {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
    SmsMessageRecord      record   = database.getMessage(masterSecret, messageId);

    try {
      Log.w(TAG, "Sending message: " + messageId);

      deliver(masterSecret, record);
    } catch (UndeliverableMessageException ude) {
      Log.w(TAG, ude);
      DatabaseFactory.getSmsDatabase(context).markAsSentFailed(record.getId());
      MessageNotifier.notifyMessageDeliveryFailed(context, record.getRecipients(), record.getThreadId());
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception throwable) {
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "onCanceled()");
    long       threadId   = DatabaseFactory.getSmsDatabase(context).getThreadIdForMessage(messageId);
    Recipients recipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);

    DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageId);
    if (threadId != -1 && recipients != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipients, threadId);
    }
  }

  private void deliver(MasterSecret masterSecret, SmsMessageRecord message)
      throws UndeliverableMessageException
  {
    String recipient;
    ArrayList<String> messages;

    if (message.isSecure() || message.isKeyExchange() || message.isEndSession()) {
      recipient = message.getIndividualRecipient().getNumber();

      MultipartSmsMessageHandler multipartMessageHandler = new MultipartSmsMessageHandler();
      OutgoingTextMessage        transportMessage        = OutgoingTextMessage.from(message);

      if (!message.isKeyExchange()) {
        transportMessage = getAsymmetricEncrypt(masterSecret, transportMessage);
      }

      messages = multipartMessageHandler.divideMessage(transportMessage);
    } else {
      recipient = message.getIndividualRecipient().getNumber();

      // See issue #1516 for bug report, and discussion on commits related to #4833 for problems
      // related to the original fix to #1516. This still may not be a correct fix if networks allow
      // SMS/MMS sending to alphanumeric recipients other than email addresses, but should also
      // help to fix issue #3099.
      if (!NumberUtil.isValidEmail(recipient)) {
        recipient = PhoneNumberUtils.stripSeparators(PhoneNumberUtils.convertKeypadLettersToDigits(recipient));
      }

      if (!NumberUtil.isValidSmsOrEmail(recipient)) {
        throw new UndeliverableMessageException("Not a valid SMS destination! " + recipient);
      }

      messages = SmsManager.getDefault().divideMessage(message.getBody().getBody());
    }

    ArrayList<PendingIntent> sentIntents      = constructSentIntents(message.getId(), message.getType(), messages, message.isSecure());
    ArrayList<PendingIntent> deliveredIntents = constructDeliveredIntents(message.getId(), message.getType(), messages);

    // NOTE 11/04/14 -- There's apparently a bug where for some unknown recipients
    // and messages, this will throw an NPE.  We have no idea why, so we're just
    // catching it and marking the message as a failure.  That way at least it doesn't
    // repeatedly crash every time you start the app.
    try {
      getSmsManagerFor(message.getSubscriptionId()).sendMultipartTextMessage(recipient, null, messages, sentIntents, deliveredIntents);
    } catch (NullPointerException npe) {
      Log.w(TAG, npe);
      Log.w(TAG, "Recipient: " + recipient);
      Log.w(TAG, "Message Parts: " + messages.size());

      try {
        for (int i=0;i<messages.size();i++) {
          getSmsManagerFor(message.getSubscriptionId()).sendTextMessage(recipient, null, messages.get(i),
                                                                        sentIntents.get(i),
                                                                        deliveredIntents == null ? null : deliveredIntents.get(i));
        }
      } catch (NullPointerException npe2) {
        Log.w(TAG, npe);
        throw new UndeliverableMessageException(npe2);
      }
    }
  }

  private OutgoingTextMessage getAsymmetricEncrypt(MasterSecret masterSecret,
                                                   OutgoingTextMessage message)
      throws UndeliverableMessageException
  {
    try {
      return new SmsCipher(new SilenceSignalProtocolStore(context, masterSecret, message.getSubscriptionId())).encrypt(message);
    } catch (NoSessionException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  private ArrayList<PendingIntent> constructSentIntents(long messageId, long type,
                                                        ArrayList<String> messages, boolean secure)
  {
    ArrayList<PendingIntent> sentIntents = new ArrayList<>(messages.size());

    for (String ignored : messages) {
      sentIntents.add(PendingIntent.getBroadcast(context, 0,
                                                 constructSentIntent(context, messageId, type, secure, false),
                                                 0));
    }

    return sentIntents;
  }

  private ArrayList<PendingIntent> constructDeliveredIntents(long messageId, long type, ArrayList<String> messages) {
    if (!SilencePreferences.isSmsDeliveryReportsEnabled(context)) {
      return null;
    }

    ArrayList<PendingIntent> deliveredIntents = new ArrayList<>(messages.size());

    for (String ignored : messages) {
      deliveredIntents.add(PendingIntent.getBroadcast(context, 0,
                                                      constructDeliveredIntent(context, messageId, type),
                                                      0));
    }

    return deliveredIntents;
  }

  private Intent constructSentIntent(Context context, long messageId, long type,
                                       boolean upgraded, boolean push)
  {
    Intent pending = new Intent(SmsDeliveryListener.SENT_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);

    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);
    pending.putExtra("upgraded", upgraded);
    pending.putExtra("push", push);

    return pending;
  }

  private Intent constructDeliveredIntent(Context context, long messageId, long type) {
    Intent pending = new Intent(SmsDeliveryListener.DELIVERED_SMS_ACTION,
                                Uri.parse("custom://" + messageId + System.currentTimeMillis()),
                                context, SmsDeliveryListener.class);
    pending.putExtra("type", type);
    pending.putExtra("message_id", messageId);

    return pending;
  }

  private SmsManager getSmsManagerFor(int subscriptionId) {
    Log.w(TAG, "getSmsManagerFor(" + subscriptionId + ")");
    if (Build.VERSION.SDK_INT >= 22 && subscriptionId != -1) {
      return SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
    } else {
      return SmsManager.getDefault();
    }
  }

  private static JobParameters constructParameters(Context context, String name) {
    JobParameters.Builder builder = JobParameters.newBuilder()
                                                 .withPersistence()
                                                 .withRequirement(new MasterSecretRequirement(context))
                                                 .withRetryCount(15)
                                                 .withGroupId(name);

    if (SilencePreferences.isWifiSmsEnabled(context)) {
      builder.withRequirement(new NetworkOrServiceRequirement(context));
    } else {
      builder.withRequirement(new ServiceRequirement(context));
    }

    return builder.create();
  }


}
