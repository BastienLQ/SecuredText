package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;


import org.smssecure.smssecure.dependencies.InjectableType;
import org.securesms.jobqueue.JobParameters;
import org.securesms.jobqueue.requirements.NetworkRequirement;
import org.securesms.libaxolotl.util.guava.Optional;
import org.securesms.textsecure.api.TextSecureMessageSender;
import org.securesms.textsecure.api.push.TextSecureAddress;
import org.securesms.textsecure.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.securesms.textsecure.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

import static org.smssecure.smssecure.dependencies.SMSSecureCommunicationModule.TextSecureMessageSenderFactory;

public class DeliveryReceiptJob extends ContextJob implements InjectableType {

  private static final String TAG = DeliveryReceiptJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final String destination;
  private final long   timestamp;
  private final String relay;

  public DeliveryReceiptJob(Context context, String destination, long timestamp, String relay) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .withRetryCount(50)
                                .create());

    this.destination = destination;
    this.timestamp   = timestamp;
    this.relay       = relay;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    Log.w("DeliveryReceiptJob", "Sending delivery receipt...");
    TextSecureMessageSender messageSender     = messageSenderFactory.create(null);
    TextSecureAddress       textSecureAddress = new TextSecureAddress(destination, Optional.fromNullable(relay));

    messageSender.sendDeliveryReceipt(textSecureAddress, timestamp);
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to send receipt after retry exhausted!");
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    Log.w(TAG, exception);
    if (exception instanceof NonSuccessfulResponseCodeException) return false;
    if (exception instanceof PushNetworkException)               return true;

    return false;
  }
}
