/**
 * Copyright (C) 2011 Whisper Systems
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

package org.smssecure.smssecure.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;
import android.util.Log;

import org.smssecure.smssecure.attachments.Attachment;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SessionUtil;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.MessagingDatabase;
import org.smssecure.smssecure.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.smssecure.smssecure.mms.OutgoingMediaMessage;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.sms.MessageSender;
import org.smssecure.smssecure.sms.OutgoingEncryptedMessage;
import org.smssecure.smssecure.sms.OutgoingTextMessage;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

/**
 * Get the response text from the Android Auto and sends an message as a reply
 */
public class AndroidAutoReplyReceiver extends MasterSecretBroadcastReceiver {

  public static final String TAG                 = AndroidAutoReplyReceiver.class.getSimpleName();
  public static final String REPLY_ACTION        = "org.smssecure.smssecure.notifications.ANDROID_AUTO_REPLY";
  public static final String RECIPIENT_IDS_EXTRA = "car_recipient_ids";
  public static final String VOICE_REPLY_KEY     = "car_voice_reply_key";
  public static final String THREAD_ID_EXTRA     = "car_reply_thread_id";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           final @Nullable MasterSecret masterSecret)
  {
    if (!REPLY_ACTION.equals(intent.getAction())) return;

    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

    if (remoteInput == null) return;

    final long[]       recipientIds = intent.getLongArrayExtra(RECIPIENT_IDS_EXTRA);
    final long         threadId     = intent.getLongExtra(THREAD_ID_EXTRA, -1);
    final CharSequence responseText = getMessageText(intent);
    final Recipients   recipients   = RecipientFactory.getRecipientsForIds(context, recipientIds, false);

    if (responseText != null) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {

          long replyThreadId;

          Optional<RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(recipientIds);
          int  subscriptionId = preferences.isPresent() ? preferences.get().getDefaultSubscriptionId().or(-1) : -1;

          if (recipients.isGroupRecipient()) {
            Log.i(TAG, "GroupRecipient, Sending media message");
            OutgoingMediaMessage reply = new OutgoingMediaMessage(recipients, responseText.toString(), new LinkedList<Attachment>(), System.currentTimeMillis(), subscriptionId, 0);
            replyThreadId = MessageSender.send(context, masterSecret, reply, threadId, false);
          } else {
            Log.i(TAG, "Sending regular message");
            boolean secure = SessionUtil.hasSession(context, masterSecret, recipients.getPrimaryRecipient().getNumber(), subscriptionId);

            OutgoingTextMessage reply;
            if (!secure) {
              reply = new OutgoingTextMessage(recipients, responseText.toString(), subscriptionId);
            } else {
              reply = new OutgoingEncryptedMessage(recipients, responseText.toString(), subscriptionId);
            }

            replyThreadId = MessageSender.send(context, masterSecret, reply, threadId, false);
          }

          DatabaseFactory.getThreadDatabase(context).setRead(replyThreadId);
          //DatabaseFactory.getThreadDatabase(context).setLastSeen(replyThreadId);
          MessageNotifier.updateNotification(context, masterSecret);

          return null;
        }
      }.execute();
    }
  }

  private CharSequence getMessageText(Intent intent) {
    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
    if (remoteInput != null) {
      return remoteInput.getCharSequence(VOICE_REPLY_KEY);
    }
    return null;
  }

}
