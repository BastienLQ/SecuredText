package org.smssecure.smssecure.jobs;

import android.app.Activity;
import android.content.Context;
import android.telephony.SmsManager;
import android.util.Log;

import org.smssecure.smssecure.ApplicationContext;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SecurityEvent;
import org.smssecure.smssecure.crypto.storage.SilenceSessionStore;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.EncryptingSmsDatabase;
import org.smssecure.smssecure.database.NoSuchMessageException;
import org.smssecure.smssecure.database.model.SmsMessageRecord;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.service.SmsDeliveryListener;
import org.smssecure.smssecure.util.SilencePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.state.SessionStore;

public class SmsSentJob extends MasterSecretJob {

  private static final String TAG = SmsSentJob.class.getSimpleName();

  private final long   messageId;
  private final String action;
  private final int    result;

  public SmsSentJob(Context context, long messageId, String action, int result) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());

    this.messageId = messageId;
    this.action    = action;
    this.result    = result;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun(MasterSecret masterSecret) {
    Log.w(TAG, "Got SMS callback: " + action + " , " + result);

    switch (action) {
      case SmsDeliveryListener.SENT_SMS_ACTION:
        handleSentResult(masterSecret, messageId, result);
        break;
      case SmsDeliveryListener.DELIVERED_SMS_ACTION:
        handleDeliveredResult(masterSecret, messageId, result);
        break;
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception throwable) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleDeliveredResult(MasterSecret masterSecret, long messageId, int result) {
    try {
      EncryptingSmsDatabase database      = DatabaseFactory.getEncryptingSmsDatabase(context);
      SmsMessageRecord      record        = database.getMessage(masterSecret, messageId);
      String                recipientName = (record.getIndividualRecipient().getName() == null ? record.getIndividualRecipient().getNumber() : record.getIndividualRecipient().getName());

      if (!record.isDelivered() && SilencePreferences.isSmsDeliveryReportsToastEnabled(context)){
        MessageNotifier.sendDeliveryToast(context, recipientName);
      }
      DatabaseFactory.getEncryptingSmsDatabase(context).markAsReceived(messageId);
    } catch (NoSuchMessageException e) {
      Log.w(TAG, e);
    }
  }

  private void handleSentResult(MasterSecret masterSecret, long messageId, int result) {
    try {
      EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
      SmsMessageRecord      record   = database.getMessage(masterSecret, messageId);

      switch (result) {
        case Activity.RESULT_OK:
          database.markAsSent(messageId, record.isSecure());

          if (record != null && record.isEndSession()) {
            Log.w(TAG, "Ending session...");
            SessionStore sessionStore = new SilenceSessionStore(context, masterSecret, record.getSubscriptionId());
            sessionStore.deleteAllSessions(record.getIndividualRecipient().getNumber());
            SecurityEvent.broadcastSecurityUpdateEvent(context, record.getThreadId());
          }

          break;
        case SmsManager.RESULT_ERROR_NO_SERVICE:
        case SmsManager.RESULT_ERROR_RADIO_OFF:
          Log.w(TAG, "Service connectivity problem, requeuing...");
          ApplicationContext.getInstance(context)
              .getJobManager()
              .add(new SmsSendJob(context, messageId, record.getIndividualRecipient().getNumber()));

          break;
        default:
          database.markAsSentFailed(messageId);
          MessageNotifier.notifyMessageDeliveryFailed(context, record.getRecipients(), record.getThreadId());
      }
    } catch (NoSuchMessageException e) {
      Log.w(TAG, e);
    }
  }
}
