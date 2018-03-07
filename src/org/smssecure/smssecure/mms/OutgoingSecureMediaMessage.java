package org.smssecure.smssecure.mms;

import org.smssecure.smssecure.attachments.Attachment;
import org.smssecure.smssecure.recipients.Recipients;

import java.util.List;

public class OutgoingSecureMediaMessage extends OutgoingMediaMessage {

  public OutgoingSecureMediaMessage(Recipients recipients, String body,
                                    List<Attachment> attachments,
                                    long sentTimeMillis,
                                    int distributionType)
  {
    super(recipients, body, attachments, sentTimeMillis, -1, distributionType);
  }

  public OutgoingSecureMediaMessage(OutgoingMediaMessage base) {
    super(base);
  }

  @Override
  public boolean isSecure() {
    return true;
  }
}
