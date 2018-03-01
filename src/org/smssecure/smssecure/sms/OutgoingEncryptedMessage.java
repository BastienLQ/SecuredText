package org.smssecure.smssecure.sms;

import org.smssecure.smssecure.recipients.Recipients;

public class OutgoingEncryptedMessage extends OutgoingTextMessage {

  public OutgoingEncryptedMessage(Recipients recipients, String body, int subscriptionId) {
    super(recipients, body, subscriptionId);
  }

  private OutgoingEncryptedMessage(OutgoingEncryptedMessage base, String body) {
    super(base, body);
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingEncryptedMessage(this, body);
  }
}
