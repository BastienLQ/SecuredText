/**
 * Copyright (C) 2012 Moxie Marlinspike
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure.database.model;

import android.content.Context;
import android.text.SpannableString;

import org.smssecure.smssecure.database.MmsSmsColumns;
import org.smssecure.smssecure.database.SmsDatabase;
import org.smssecure.smssecure.recipients.Recipients;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DisplayRecord {

  protected final Context context;
  protected final long type;

  private final Recipients recipients;
  private final long       dateSent;
  private final long       dateReceived;
  private final long       dateDeliveryReceived;
  private final long       threadId;
  private final Body       body;
  private final int        deliveryStatus;

  public DisplayRecord(Context context, Body body, Recipients recipients, long dateSent,
                       long dateReceived, long dateDeliveryReceived, long threadId,
                       int deliveryStatus, long type)
  {
    this.context              = context.getApplicationContext();
    this.threadId             = threadId;
    this.recipients           = recipients;
    this.dateSent             = dateSent;
    this.dateReceived         = dateReceived;
    this.dateDeliveryReceived = dateDeliveryReceived;
    this.type                 = type;
    this.body                 = body;
    this.deliveryStatus       = deliveryStatus;
  }

  public Body getBody() {
    return body;
  }

  public boolean isFailed() {
    return
        MmsSmsColumns.Types.isFailedMessageType(type)            ||
        deliveryStatus >= SmsDatabase.Status.STATUS_FAILED;
  }

  public boolean isPending() {
    return MmsSmsColumns.Types.isPendingMessageType(type);
  }

  public boolean isOutgoing() {
    return MmsSmsColumns.Types.isOutgoingMessageType(type);
  }

  public boolean isDuplicateMessageType() {
    return MmsSmsColumns.Types.isDuplicateMessageType(type);
  }

  public abstract SpannableString getDisplayBody();

  public Recipients getRecipients() {
    return recipients;
  }

  public long getDateSent() {
    return dateSent;
  }

  public long getDateReceived() {
    return dateReceived;
  }

  public long getDateDeliveryReceived() {
    return dateDeliveryReceived;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean isKeyExchange() {
    return SmsDatabase.Types.isKeyExchangeType(type);
  }

  public boolean isXmppExchange() {
    return SmsDatabase.Types.isXmppExchangeType(type);
  }

  public boolean isEndSession() {
    return SmsDatabase.Types.isEndSessionType(type);
  }

  public boolean isGroupUpdate() {
    return SmsDatabase.Types.isGroupUpdate(type);
  }

  public boolean isGroupQuit() {
    return SmsDatabase.Types.isGroupQuit(type);
  }

  public boolean isGroupAction() {
    return isGroupUpdate() || isGroupQuit();
  }

  public int getDeliveryStatus() {
    return deliveryStatus;
  }

  public boolean isDelivered() {
    return (deliveryStatus >= SmsDatabase.Status.STATUS_COMPLETE &&
            deliveryStatus < SmsDatabase.Status.STATUS_PENDING);
  }

  public static class Body {
    private final String body;
    private final boolean plaintext;

    public Body(String body, boolean plaintext) {
      this.body      = body;
      this.plaintext = plaintext;
    }

    public boolean isPlaintext() {
      return plaintext;
    }

    public String getBody() {
      return body == null ? "" : body;
    }
  }
}
