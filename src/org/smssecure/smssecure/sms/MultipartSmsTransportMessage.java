package org.smssecure.smssecure.sms;

import android.util.Log;

import org.smssecure.smssecure.protocol.EndSessionWirePrefix;
import org.smssecure.smssecure.protocol.KeyExchangeWirePrefix;
import org.smssecure.smssecure.protocol.PrekeyBundleWirePrefix;
import org.smssecure.smssecure.protocol.SecureMessageWirePrefix;
import org.smssecure.smssecure.protocol.WirePrefix;
import org.smssecure.smssecure.util.Base64;
import org.smssecure.smssecure.util.Conversions;

import java.io.IOException;
import java.lang.IllegalArgumentException;

public class MultipartSmsTransportMessage {
  private static final String TAG = MultipartSmsTransportMessage.class.getName();

  private static final int MULTIPART_SUPPORTED_AFTER_VERSION = 1;

  public static final int SINGLE_MESSAGE_MULTIPART_OVERHEAD      = 1;
  public static final int MULTI_MESSAGE_MULTIPART_OVERHEAD       = 3;
  public static final int FIRST_MULTI_MESSAGE_MULTIPART_OVERHEAD = 2;

  public static final int WIRETYPE_SECURE        = 1;
  public static final int WIRETYPE_PREKEY        = 2;
  public static final int WIRETYPE_END_SESSION   = 3;
  public static final int WIRETYPE_XMPP_EXCHANGE = 4;
  public static final int WIRETYPE_KEY           = 5;
  public static final int LAST_PREFIX_TO_TEST    = 5;

  private static final int VERSION_OFFSET    = 0;
  private static final int MULTIPART_OFFSET  = 1;
  private static final int IDENTIFIER_OFFSET = 2;

  private       int                 wireType;
  private final byte[]              decodedMessage;
  private final IncomingTextMessage message;

  public MultipartSmsTransportMessage(IncomingTextMessage message) throws IOException {
    try {
      this.message         = message;
      this.decodedMessage  = Base64.decodeWithoutPadding(message.getMessageBody().substring(WirePrefix.PREFIX_SIZE));

      redecodeWirePrefix(-1);
    } catch (IllegalArgumentException iae) {
      throw new IOException(iae);
    }
  }

  public void redecodeWirePrefix(int lastIncorrectWirePrefix) throws IOException {
    if (lastIncorrectWirePrefix >= LAST_PREFIX_TO_TEST)
      throw new IOException("Invalid message!");

    if      (lastIncorrectWirePrefix < WIRETYPE_SECURE        &&
             WirePrefix.isEncryptedMessage(message.getMessageBody())) wireType = WIRETYPE_SECURE;
    else if (lastIncorrectWirePrefix < WIRETYPE_PREKEY        &&
             WirePrefix.isPreKeyBundle(message.getMessageBody()))     wireType = WIRETYPE_PREKEY;
    else if (lastIncorrectWirePrefix < WIRETYPE_END_SESSION   &&
             WirePrefix.isEndSession(message.getMessageBody()))       wireType = WIRETYPE_END_SESSION;
    else if (lastIncorrectWirePrefix < WIRETYPE_XMPP_EXCHANGE &&
             WirePrefix.isXmppExchange(message.getMessageBody()))     wireType = WIRETYPE_XMPP_EXCHANGE;
    else                                                              wireType = WIRETYPE_KEY;

    Log.w(TAG, "Decoded message with version:   " + getCurrentVersion());
    Log.w(TAG, "Decoded message with wire type: " + wireType);
  }

  public int getWireType() {
    return wireType;
  }

  public int getCurrentVersion() {
    return Conversions.highBitsToInt(decodedMessage[VERSION_OFFSET]);
  }

  public int getMultipartIndex() {
    return Conversions.highBitsToInt(decodedMessage[MULTIPART_OFFSET]);
  }

  public int getMultipartCount() {
    if (isDeprecatedTransport())
      return 1;

    return Conversions.lowBitsToInt(decodedMessage[MULTIPART_OFFSET]);
  }

  public int getIdentifier() {
    return decodedMessage[IDENTIFIER_OFFSET] & 0xFF;
  }

  public boolean isDeprecatedTransport() {
    return getCurrentVersion() < MULTIPART_SUPPORTED_AFTER_VERSION;
  }

  public boolean isInvalid() {
    return getMultipartIndex() >= getMultipartCount();
  }

  public boolean isSinglePart() {
    return getMultipartCount() == 1;
  }

  public byte[] getStrippedMessage() {
    if      (isDeprecatedTransport())  return getStrippedMessageForDeprecatedTransport();
    else if (getMultipartCount() == 1) return getStrippedMessageForSinglePart();
    else                               return getStrippedMessageForMultiPart();
  }

  /*
   * We're dealing with a message that isn't using the multipart transport.
   *
   */
  private byte[] getStrippedMessageForDeprecatedTransport() {
    return decodedMessage;
  }

  /*
   * We're dealing with a transport message that is of the format:
   * Version         (1 byte)
   * Index_And_Count (1 byte)
   * Message         (remainder)
   *
   * The version byte was stolen off the message, so we strip Index_And_Count byte out,
   * put the version byte back on the front of the message, and return.
   */
  private byte[] getStrippedMessageForSinglePart() {
    byte[] stripped = new byte[decodedMessage.length - 1];
    System.arraycopy(decodedMessage, 1, stripped, 0, decodedMessage.length - 1);
    stripped[0] = decodedMessage[VERSION_OFFSET];

    return stripped;
  }

  /*
   * We're dealing with a transport message that is of the format:
   *
   * Version         (1 byte)
   * Index_And_Count (1 byte)
   * Identifier      (1 byte)
   * Message         (remainder)
   *
   * The version byte was stolen off the first byte of the message, but only for the first fragment
   * of the message.  So for the first fragment we strip off everything and put the version byte
   * back on.  For the remaining fragments, we just strip everything.
   */

  private byte[] getStrippedMessageForMultiPart() {
    byte[] strippedMessage    = new byte[decodedMessage.length - (getMultipartIndex() == 0 ? 2 : 3)];

    int copyDestinationIndex  = 0;
    int copyDestinationLength = strippedMessage.length;

    if (getMultipartIndex() == 0) {
      strippedMessage[0] = decodedMessage[0];
      copyDestinationIndex++;
      copyDestinationLength--;
    }

    System.arraycopy(decodedMessage, 3, strippedMessage, copyDestinationIndex, copyDestinationLength);
    return strippedMessage;

  }

  public String getKey() {
    return message.getSender() + getIdentifier();
  }

  public IncomingTextMessage getBaseMessage() {
    return message;
  }

  public static String getEncodedMessage(OutgoingTextMessage message, byte identifier)
  {
    try {
      byte[] decoded = Base64.decodeWithoutPadding(message.getMessageBody());
      int count      = new SmsTransportDetails().getMessageCountForBytes(decoded.length);

      WirePrefix prefix;

      if      (message.isKeyExchange())  prefix = new KeyExchangeWirePrefix();
      else if (message.isPreKeyBundle()) prefix = new PrekeyBundleWirePrefix();
      else if (message.isEndSession())   prefix = new EndSessionWirePrefix();
      else                               prefix = new SecureMessageWirePrefix();

      return getEncoded(decoded, prefix);

    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String getEncoded(byte[] decoded, WirePrefix prefix) {
    byte[] messageWithMultipartHeader = new byte[decoded.length + 1];
    System.arraycopy(decoded, 0, messageWithMultipartHeader, 1, decoded.length);

    messageWithMultipartHeader[VERSION_OFFSET]   = decoded[VERSION_OFFSET];
    messageWithMultipartHeader[MULTIPART_OFFSET] = Conversions.intsToByteHighAndLow(0, 1);

    String encodedMessage = Base64.encodeBytesWithoutPadding(messageWithMultipartHeader);

    return (prefix.calculatePrefix(encodedMessage) + encodedMessage);
  }

}
