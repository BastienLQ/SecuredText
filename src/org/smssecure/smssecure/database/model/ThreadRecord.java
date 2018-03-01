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
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.database.MmsSmsColumns;
import org.smssecure.smssecure.database.SmsDatabase;
import org.smssecure.smssecure.recipients.Recipients;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class ThreadRecord extends DisplayRecord {

  private @NonNull  final Context context;
  private @Nullable final Uri     snippetUri;
  private           final long    count;
  private           final boolean read;
  private           final int     distributionType;
  private           final boolean archived;
  private           final long    lastSeen;

  public ThreadRecord(@NonNull Context context, @NonNull Body body, @Nullable Uri snippetUri,
                      @NonNull Recipients recipients, long date, long count, boolean read,
                      long threadId, int status, long snippetType, int distributionType, boolean archived,
                      long lastSeen)
  {
    super(context, body, recipients, date, date, date, threadId, status, snippetType);
    this.context          = context.getApplicationContext();
    this.snippetUri       = snippetUri;
    this.count            = count;
    this.read             = read;
    this.distributionType = distributionType;
    this.archived         = archived;
    this.lastSeen         = lastSeen;
  }

  public @Nullable Uri getSnippetUri() {
    return snippetUri;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (SmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_decrypting_please_wait));
    } else if (isGroupQuit()) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_left_the_group));
    } else if (isKeyExchange()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_key_exchange_message));
    } else if (isDuplicateMessageType()) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (isXmppExchange()) {
      return emphasisAdded(context.getString(R.string.ConversationItem_xmpp_address_update_silence));
    } else if (SmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
    } else if (SmsDatabase.Types.isNoRemoteSessionType(type)) {
      if (SmsDatabase.Types.isEndSessionType(type)) {
        return emphasisAdded(context.getString(R.string.MessageDisplayHelper_end_session_encrypted_for_non_existing_session));
      } else {
        return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
      }
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.MessageNotifier_encrypted_message));
    } else if (SmsDatabase.Types.isEndSessionType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_secure_session_ended));
    } else if (MmsSmsColumns.Types.isLegacyType(type)) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (MmsSmsColumns.Types.isDraftMessageType(type)) {
      String draftText = context.getString(R.string.ThreadRecord_draft);
      return emphasisAdded(draftText + " " + getBody().getBody(), 0, draftText.length());
    } else {
      if (TextUtils.isEmpty(getBody().getBody())) {
        return new SpannableString(emphasisAdded(context.getString(R.string.ThreadRecord_media_message)));
      } else {
        return new SpannableString(getBody().getBody());
      }
    }
  }

  private SpannableString emphasisAdded(String sequence) {
    return emphasisAdded(sequence, 0, sequence.length());
  }

  private SpannableString emphasisAdded(String sequence, int start, int end) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                      start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public long getCount() {
    return count;
  }

  public boolean isRead() {
    return read;
  }

  public long getDate() {
    return getDateReceived();
  }

  public boolean isArchived() {
    return archived;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getLastSeen() {
    return lastSeen;
  }
}
