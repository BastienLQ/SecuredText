package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.MmsDatabase;
import org.smssecure.smssecure.database.MmsSmsColumns;
import org.smssecure.smssecure.database.NoSuchMessageException;
import org.smssecure.smssecure.database.documents.NetworkFailure;
import org.smssecure.smssecure.dependencies.InjectableType;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.mms.PartParser;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.RecipientFormattingException;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.util.Base64;
import org.smssecure.smssecure.util.GroupUtil;
import org.securesms.jobqueue.JobParameters;
import org.securesms.jobqueue.requirements.NetworkRequirement;
import org.securesms.textsecure.api.TextSecureMessageSender;
import org.securesms.textsecure.api.crypto.UntrustedIdentityException;
import org.securesms.textsecure.api.messages.TextSecureAttachment;
import org.securesms.textsecure.api.messages.TextSecureGroup;
import org.securesms.textsecure.api.messages.TextSecureMessage;
import org.securesms.textsecure.api.push.TextSecureAddress;
import org.securesms.textsecure.api.push.exceptions.EncapsulatedExceptions;
import org.securesms.textsecure.api.push.exceptions.NetworkFailureException;
import org.securesms.textsecure.api.util.InvalidNumberException;
import org.securesms.textsecure.internal.push.PushMessageProtos;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.SendReq;

import static org.smssecure.smssecure.dependencies.SMSSecureCommunicationModule.TextSecureMessageSenderFactory;

public class PushGroupSendJob extends PushSendJob implements InjectableType {

  private static final String TAG = PushGroupSendJob.class.getSimpleName();

  @Inject transient TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;
  private final long filterRecipientId;

  public PushGroupSendJob(Context context, long messageId, String destination, long filterRecipientId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId(destination)
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withRetryCount(5)
                                .create());

    this.messageId         = messageId;
    this.filterRecipientId = filterRecipientId;
  }

  @Override
  public void onAdded() {
    DatabaseFactory.getMmsDatabase(context)
                   .markAsSending(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws MmsException, IOException, NoSuchMessageException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message, filterRecipientId);

      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId, "push".getBytes(), 0);
    } catch (InvalidNumberException | RecipientFormattingException e) {
      Log.w(TAG, e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    } catch (EncapsulatedExceptions e) {
      Log.w(TAG, e);
      List<NetworkFailure> failures = new LinkedList<>();

      for (NetworkFailureException nfe : e.getNetworkExceptions()) {
        Recipient recipient = RecipientFactory.getRecipientsFromString(context, nfe.getE164number(), false).getPrimaryRecipient();
        failures.add(new NetworkFailure(recipient.getRecipientId()));
      }

//      for (UnregisteredUserException uue : e.getUnregisteredUserExceptions()) {
//        Recipient recipient = RecipientFactory.getRecipientsFromString(context, uue.getE164Number(), false).getPrimaryRecipient();
//        failures.add(new NetworkFailure(recipient.getRecipientId(), NetworkFailure.UNREGISTERED_FAILURE));
//      }

      for (UntrustedIdentityException uie : e.getUntrustedIdentityExceptions()) {
        Recipient recipient = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false).getPrimaryRecipient();
        database.addMismatchedIdentity(messageId, recipient.getRecipientId(), uie.getIdentityKey());
      }

      database.addFailures(messageId, failures);
      database.markAsSentFailed(messageId);
      database.markAsPush(messageId);

      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
  }

  private void deliver(MasterSecret masterSecret, SendReq message, long filterRecipientId)
      throws IOException, RecipientFormattingException, InvalidNumberException, EncapsulatedExceptions
  {
    TextSecureMessageSender    messageSender = messageSenderFactory.create(masterSecret);
    byte[]                     groupId       = GroupUtil.getDecodedId(message.getTo()[0].getString());
    Recipients                 recipients    = DatabaseFactory.getGroupDatabase(context).getGroupMembers(groupId, false);
    List<TextSecureAttachment> attachments   = getAttachments(masterSecret, message);
    List<TextSecureAddress>    addresses;

    if (filterRecipientId >= 0) addresses = getPushAddresses(filterRecipientId);
    else                        addresses = getPushAddresses(recipients);

    if (MmsSmsColumns.Types.isGroupUpdate(message.getDatabaseMessageBox()) ||
        MmsSmsColumns.Types.isGroupQuit(message.getDatabaseMessageBox()))
    {
      String content = PartParser.getMessageText(message.getBody());

      if (content != null && !content.trim().isEmpty()) {
        PushMessageProtos.PushMessageContent.GroupContext groupContext = PushMessageProtos.PushMessageContent.GroupContext.parseFrom(Base64.decode(content));
        TextSecureAttachment avatar       = attachments.isEmpty() ? null : attachments.get(0);
        TextSecureGroup.Type type         = MmsSmsColumns.Types.isGroupQuit(message.getDatabaseMessageBox()) ? TextSecureGroup.Type.QUIT : TextSecureGroup.Type.UPDATE;
        TextSecureGroup      group        = new TextSecureGroup(type, groupId, groupContext.getName(), groupContext.getMembersList(), avatar);
        TextSecureMessage groupMessage = new TextSecureMessage(message.getSentTimestamp(), group, null, null);

        messageSender.sendMessage(addresses, groupMessage);
      }
    } else {
      String            body         = PartParser.getMessageText(message.getBody());
      TextSecureGroup   group        = new TextSecureGroup(groupId);
      TextSecureMessage groupMessage = new TextSecureMessage(message.getSentTimestamp(), group, attachments, body);

      messageSender.sendMessage(addresses, groupMessage);
    }
  }

  private List<TextSecureAddress> getPushAddresses(Recipients recipients) throws InvalidNumberException {
    List<TextSecureAddress> addresses = new LinkedList<>();

    for (Recipient recipient : recipients.getRecipientsList()) {
      addresses.add(getPushAddress(recipient.getNumber()));
    }

    return addresses;
  }

  private List<TextSecureAddress> getPushAddresses(long filterRecipientId) throws InvalidNumberException {
    List<TextSecureAddress> addresses = new LinkedList<>();
    addresses.add(getPushAddress(RecipientFactory.getRecipientForId(context, filterRecipientId, false).getNumber()));
    return addresses;
  }

}
