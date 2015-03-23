package org.smssecure.smssecure.crypto;

import android.content.Context;

import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.RecipientFormattingException;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.sms.IncomingEncryptedMessage;
import org.smssecure.smssecure.sms.IncomingKeyExchangeMessage;
import org.smssecure.smssecure.sms.IncomingPreKeyBundleMessage;
import org.smssecure.smssecure.sms.IncomingTextMessage;
import org.smssecure.smssecure.sms.OutgoingKeyExchangeMessage;
import org.smssecure.smssecure.sms.OutgoingPrekeyBundleMessage;
import org.smssecure.smssecure.sms.OutgoingTextMessage;
import org.smssecure.smssecure.sms.SmsTransportDetails;
import org.securesms.libaxolotl.AxolotlAddress;
import org.securesms.libaxolotl.DuplicateMessageException;
import org.securesms.libaxolotl.InvalidKeyException;
import org.securesms.libaxolotl.InvalidKeyIdException;
import org.securesms.libaxolotl.InvalidMessageException;
import org.securesms.libaxolotl.InvalidVersionException;
import org.securesms.libaxolotl.LegacyMessageException;
import org.securesms.libaxolotl.NoSessionException;
import org.securesms.libaxolotl.SessionBuilder;
import org.securesms.libaxolotl.SessionCipher;
import org.securesms.libaxolotl.StaleKeyExchangeException;
import org.securesms.libaxolotl.UntrustedIdentityException;
import org.securesms.libaxolotl.protocol.CiphertextMessage;
import org.securesms.libaxolotl.protocol.KeyExchangeMessage;
import org.securesms.libaxolotl.protocol.PreKeyWhisperMessage;
import org.securesms.libaxolotl.protocol.WhisperMessage;
import org.securesms.libaxolotl.state.AxolotlStore;
import org.securesms.textsecure.api.push.TextSecureAddress;

import java.io.IOException;

public class SmsCipher {

  private final SmsTransportDetails transportDetails = new SmsTransportDetails();

  private final AxolotlStore axolotlStore;

  public SmsCipher(AxolotlStore axolotlStore) {
    this.axolotlStore = axolotlStore;
  }

  public IncomingTextMessage decrypt(Context context, IncomingTextMessage message)
      throws LegacyMessageException, InvalidMessageException,
             DuplicateMessageException, NoSessionException
  {
    try {
      byte[]         decoded        = transportDetails.getDecodedMessage(message.getMessageBody().getBytes());
      WhisperMessage whisperMessage = new WhisperMessage(decoded);
      SessionCipher  sessionCipher  = new SessionCipher(axolotlStore, new AxolotlAddress(message.getSender(), TextSecureAddress.DEFAULT_DEVICE_ID));
      byte[]         padded         = sessionCipher.decrypt(whisperMessage);
      byte[]         plaintext      = transportDetails.getStrippedPaddingMessageBody(padded);

      if (message.isEndSession() && "TERMINATE".equals(new String(plaintext))) {
        axolotlStore.deleteSession(new AxolotlAddress(message.getSender(), TextSecureAddress.DEFAULT_DEVICE_ID));
      }

      return message.withMessageBody(new String(plaintext));
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }

  public IncomingEncryptedMessage decrypt(Context context, IncomingPreKeyBundleMessage message)
      throws InvalidVersionException, InvalidMessageException, DuplicateMessageException,
             UntrustedIdentityException, LegacyMessageException
  {
    try {
      byte[]               decoded       = transportDetails.getDecodedMessage(message.getMessageBody().getBytes());
      PreKeyWhisperMessage preKeyMessage = new PreKeyWhisperMessage(decoded);
      SessionCipher        sessionCipher = new SessionCipher(axolotlStore, new AxolotlAddress(message.getSender(), TextSecureAddress.DEFAULT_DEVICE_ID));
      byte[]               padded        = sessionCipher.decrypt(preKeyMessage);
      byte[]               plaintext     = transportDetails.getStrippedPaddingMessageBody(padded);

      return new IncomingEncryptedMessage(message, new String(plaintext));
    } catch (IOException | InvalidKeyException | InvalidKeyIdException e) {
      throw new InvalidMessageException(e);
    }
  }

  public OutgoingTextMessage encrypt(OutgoingTextMessage message) throws NoSessionException {
    byte[] paddedBody      = transportDetails.getPaddedMessageBody(message.getMessageBody().getBytes());
    String recipientNumber = message.getRecipients().getPrimaryRecipient().getNumber();

    if (!axolotlStore.containsSession(new AxolotlAddress(recipientNumber, TextSecureAddress.DEFAULT_DEVICE_ID))) {
      throw new NoSessionException("No session for: " + recipientNumber);
    }

    SessionCipher     cipher            = new SessionCipher(axolotlStore, new AxolotlAddress(recipientNumber, TextSecureAddress.DEFAULT_DEVICE_ID));
    CiphertextMessage ciphertextMessage = cipher.encrypt(paddedBody);
    String            encodedCiphertext = new String(transportDetails.getEncodedMessage(ciphertextMessage.serialize()));

    if (ciphertextMessage.getType() == CiphertextMessage.PREKEY_TYPE) {
      return new OutgoingPrekeyBundleMessage(message, encodedCiphertext);
    } else {
      return message.withBody(encodedCiphertext);
    }
  }

  public OutgoingKeyExchangeMessage process(Context context, IncomingKeyExchangeMessage message)
      throws UntrustedIdentityException, StaleKeyExchangeException,
             InvalidVersionException, LegacyMessageException, InvalidMessageException
  {
    try {
      Recipient          recipient       = RecipientFactory.getRecipientsFromString(context, message.getSender(), false).getPrimaryRecipient();
      AxolotlAddress     axolotlAddress  = new AxolotlAddress(message.getSender(), TextSecureAddress.DEFAULT_DEVICE_ID);
      KeyExchangeMessage exchangeMessage = new KeyExchangeMessage(transportDetails.getDecodedMessage(message.getMessageBody().getBytes()));
      SessionBuilder     sessionBuilder  = new SessionBuilder(axolotlStore, axolotlAddress);

      KeyExchangeMessage response        = sessionBuilder.process(exchangeMessage);

      if (response != null) {
        byte[] serializedResponse = transportDetails.getEncodedMessage(response.serialize());
        return new OutgoingKeyExchangeMessage(recipient, new String(serializedResponse));
      } else {
        return null;
      }
    } catch (IOException | InvalidKeyException e) {
      throw new InvalidMessageException(e);
    }
  }

}
