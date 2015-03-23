package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.ApplicationContext;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SessionUtil;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.MmsDatabase;
import org.smssecure.smssecure.database.NoSuchMessageException;
import org.smssecure.smssecure.dependencies.InjectableType;
import org.smssecure.smssecure.mms.MediaConstraints;
import org.smssecure.smssecure.mms.PartParser;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.transport.InsecureFallbackApprovalException;
import org.smssecure.smssecure.transport.RetryLaterException;
import org.smssecure.smssecure.transport.SecureFallbackApprovalException;
import org.smssecure.smssecure.transport.UndeliverableMessageException;
import org.smssecure.textsecure.api.TextSecureMessageSender;
import org.smssecure.textsecure.api.crypto.UntrustedIdentityException;
import org.smssecure.textsecure.api.messages.TextSecureAttachment;
import org.smssecure.textsecure.api.messages.TextSecureMessage;
import org.smssecure.textsecure.api.push.TextSecureAddress;
import org.smssecure.textsecure.api.push.exceptions.UnregisteredUserException;
import org.smssecure.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.SendReq;

import static org.smssecure.smssecure.dependencies.SMSSecureCommunicationModule.TextSecureMessageSenderFactory;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination, true));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    mmsDatabase.markAsSending(messageId);
    mmsDatabase.markAsPush(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws RetryLaterException, MmsException, NoSuchMessageException,
             UndeliverableMessageException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      if (deliver(masterSecret, message)) {
        database.markAsPush(messageId);
        database.markAsSecure(messageId);
        database.markAsSent(messageId, "push".getBytes(), 0);
      }
    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      database.markAsPendingInsecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (SecureFallbackApprovalException sfae) {
      Log.w(TAG, sfae);
      database.markAsPendingSecureSmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (UntrustedIdentityException uie) {
      Log.w(TAG, uie);
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
      long       recipientId = recipients.getPrimaryRecipient().getRecipientId();

      database.addMismatchedIdentity(messageId, recipientId, uie.getIdentityKey());
      database.markAsSentFailed(messageId);
      database.markAsPush(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RequirementNotMetException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }


  private boolean deliver(MasterSecret masterSecret, SendReq message)
      throws RetryLaterException, SecureFallbackApprovalException,
             InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException
  {
    MmsDatabase             database               = DatabaseFactory.getMmsDatabase(context);
    TextSecureMessageSender messageSender          = messageSenderFactory.create(masterSecret);
    String                  destination            = message.getTo()[0].getString();
    boolean                 isSmsFallbackSupported = isSmsFallbackSupported(context, destination, true);

    try {
      prepareMessageMedia(masterSecret, message, MediaConstraints.PUSH_CONSTRAINTS, false);
      TextSecureAddress          address      = getPushAddress(destination);
      List<TextSecureAttachment> attachments  = getAttachments(masterSecret, message);
      String                     body         = PartParser.getMessageText(message.getBody());
      TextSecureMessage          mediaMessage = new TextSecureMessage(message.getSentTimestamp(), attachments, body);

      messageSender.sendMessage(address, mediaMessage);
      return true;
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        database.markAsSentFailed(messageId);
    } catch (IOException e) {
      Log.w(TAG, e);
      if (isSmsFallbackSupported) fallbackOrAskApproval(masterSecret, message, destination);
      else                        throw new RetryLaterException(e);
    }
    return false;
  }

  private void fallbackOrAskApproval(MasterSecret masterSecret, SendReq mediaMessage, String destination)
      throws SecureFallbackApprovalException, InsecureFallbackApprovalException
  {
    boolean   isSmsFallbackApprovalRequired = isSmsFallbackApprovalRequired(destination, true);

    if (!isSmsFallbackApprovalRequired) {
      Log.w(TAG, "Falling back to MMS");
      DatabaseFactory.getMmsDatabase(context).markAsForcedSms(mediaMessage.getDatabaseMessageId());
      ApplicationContext.getInstance(context).getJobManager().add(new MmsSendJob(context, messageId));
    } else if (!SessionUtil.hasSession(context, masterSecret, destination)) {
      Log.w(TAG, "Marking message as pending insecure SMS fallback");
      throw new InsecureFallbackApprovalException("Pending user approval for fallback to insecure SMS");
    } else {
      Log.w(TAG, "Marking message as pending secure SMS fallback");
      throw new SecureFallbackApprovalException("Pending user approval for fallback secure to SMS");
    }
  }


}
