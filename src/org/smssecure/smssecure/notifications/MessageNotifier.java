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

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.Toast;

import org.smssecure.smssecure.ConversationActivity;
import org.smssecure.smssecure.R;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.MmsSmsDatabase;
import org.smssecure.smssecure.database.SmsDatabase;
import org.smssecure.smssecure.database.ThreadDatabase;
import org.smssecure.smssecure.database.model.MediaMmsMessageRecord;
import org.smssecure.smssecure.database.model.MessageRecord;
import org.smssecure.smssecure.mms.SlideDeck;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.util.ListenableFutureTask;
import org.smssecure.smssecure.util.SpanUtil;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.smssecure.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

/**
 * Handles posting system notifications for new messages.
 *
 *
 * @author Moxie Marlinspike
 */

public class MessageNotifier {

  private static final String TAG = MessageNotifier.class.getSimpleName();
  private static boolean bLightsActive = false;

  public static final int NOTIFICATION_ID = 1338;

  private volatile static long visibleThread = -1;

  public static final String EXTRA_VOICE_REPLY = "extra_voice_reply";

  public static void setVisibleThread(long threadId) {
    visibleThread = threadId;
  }

  public static final int MNF_SOUND       = 0x1;
  public static final int MNF_LIGHTS      = 0x2;
  public static final int MNF_LIGHTS_KEEP = 0x4;
  public static final int MNF_DEFAULTS    = MNF_SOUND | MNF_LIGHTS;

  public static boolean notificationsRequested(int flags) {
    int mask = MNF_SOUND | MNF_LIGHTS | MNF_LIGHTS_KEEP;
    return ((flags & mask) != 0);
  }

  public static boolean newNotificationRequested(int flags) {
    int mask = MNF_SOUND | MNF_LIGHTS;
    return ((flags & mask) != 0);
  }

  public static void cancelNotification(Context context) {
    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
            .cancel(NOTIFICATION_ID);
    bLightsActive = false;
  }

  public static void sendDeliveryToast(final Context context, final String recipientName){
    new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        Toast.makeText(context.getApplicationContext(),
                       context.getString(R.string.MessageNotifier_message_received, recipientName),
                       Toast.LENGTH_LONG).show();
        Looper.loop();
      }
    }.start();
  }

  public static void notifyMessageDeliveryFailed(Context context, Recipients recipients, long threadId) {
    if (visibleThread == threadId) {
      sendInThreadNotification(context, recipients);
    } else {
      Intent intent = new Intent(context, ConversationActivity.class);
      intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
      intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
      intent.setData((Uri.parse("custom://" + System.currentTimeMillis())));

      FailedNotificationBuilder builder = new FailedNotificationBuilder(context, SMSSecurePreferences.getNotificationPrivacy(context), intent);
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify((int)threadId, builder.build());
    }
  }

  public static void updateNotificationWithFlags(Context context, MasterSecret masterSecret, int flags) {
    if (!SMSSecurePreferences.isNotificationsEnabled(context)) {
      return;
    }

    updateNotification(context, masterSecret, flags, 0);
  }

  public static void updateNotification(Context context, MasterSecret masterSecret) {
    updateNotificationWithFlags(context, masterSecret, MNF_LIGHTS_KEEP);
  }

  public static void updateNotification(Context context, MasterSecret masterSecret, long threadId) {
    Recipients recipients = DatabaseFactory.getThreadDatabase(context)
                                           .getRecipientsForThreadId(threadId);

    if (!SMSSecurePreferences.isNotificationsEnabled(context) ||
        (recipients != null && recipients.isMuted()))
    {
      return;
    }


    if (visibleThread == threadId) {
      ThreadDatabase threads = DatabaseFactory.getThreadDatabase(context);
      threads.setRead(threadId);
      sendInThreadNotification(context, threads.getRecipientsForThreadId(threadId));
    } else {
      updateNotification(context, masterSecret, MNF_DEFAULTS, 0);
    }
  }

  private static void updateNotification(Context context, MasterSecret masterSecret, int flags, int reminderCount) {
    Cursor telcoCursor = null;
    Cursor pushCursor  = null;

    try {
      telcoCursor = DatabaseFactory.getMmsSmsDatabase(context).getUnread();

      if ((telcoCursor == null || telcoCursor.isAfterLast()) &&
          (pushCursor == null || pushCursor.isAfterLast()))
      {
        cancelNotification(context);
        clearReminder(context);
        return;
      }

      NotificationState notificationState = constructNotificationState(context, masterSecret, telcoCursor);

      if (notificationState.hasMultipleThreads()) {
        sendMultipleThreadNotification(context, notificationState, flags);
      } else {
        sendSingleThreadNotification(context, masterSecret, notificationState, flags);
      }

      if (newNotificationRequested(flags)) {
        scheduleReminder(context, reminderCount);
      }
    } finally {
      if (telcoCursor != null) telcoCursor.close();
      if (pushCursor != null)  pushCursor.close();
    }
  }

  private static void triggerNotificationAlarms(AbstractNotificationBuilder builder, NotificationState notificationState, int flags) {
      if ((flags & MNF_SOUND) == MNF_SOUND)
        builder.setAudibleAlarms(notificationState.getRingtone(), notificationState.getVibrate());

      if ((flags & MNF_LIGHTS) == MNF_LIGHTS) {
        builder.setVisualAlarms();
        bLightsActive = true;
      } else if (((flags & MNF_LIGHTS_KEEP) == MNF_LIGHTS_KEEP) && (bLightsActive)) {
          builder.setVisualAlarms();
      }
  }

  private static void sendSingleThreadNotification(Context context,
                                                   MasterSecret masterSecret,
                                                   NotificationState notificationState,
                                                   int flags)
  {
    if (notificationState.getNotifications().isEmpty()) {
      cancelNotification(context);
      return;
    }

    SingleRecipientNotificationBuilder builder       = new SingleRecipientNotificationBuilder(context, masterSecret, SMSSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>             notifications = notificationState.getNotifications();

    builder.setSender(notifications.get(0).getIndividualRecipient());
    builder.setMessageCount(notificationState.getMessageCount());
    builder.setPrimaryMessageBody(notifications.get(0).getText(), notifications.get(0).getSlideDeck());
    builder.setContentIntent(notifications.get(0).getPendingIntent(context));

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(masterSecret,
                       notificationState.getMarkAsReadIntent(context),
                       notificationState.getQuickReplyIntent(context, notifications.get(0).getRecipients()),
                       notificationState.getWearableReplyIntent(context, notifications.get(0).getRecipients()));

    ListIterator<NotificationItem> iterator = notifications.listIterator(notifications.size());

    while(iterator.hasPrevious()) {
      builder.addMessageBody(iterator.previous().getText());

    }

    if (notificationsRequested(flags)) {
      triggerNotificationAlarms(builder, notificationState, flags);

      builder.setTicker(notifications.get(0).getIndividualRecipient(),
                        notifications.get(0).getText());
    }

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
      .notify(NOTIFICATION_ID, builder.build());
  }

  private static void sendMultipleThreadNotification(Context context,
                                                     NotificationState notificationState,
                                                     int flags)
  {
    MultipleRecipientNotificationBuilder builder       = new MultipleRecipientNotificationBuilder(context, SMSSecurePreferences.getNotificationPrivacy(context));
    List<NotificationItem>               notifications = notificationState.getNotifications();

    builder.setMessageCount(notificationState.getMessageCount(), notificationState.getThreadCount());
    builder.setMostRecentSender(notifications.get(0).getIndividualRecipient());

    long timestamp = notifications.get(0).getTimestamp();
    if (timestamp != 0) builder.setWhen(timestamp);

    builder.addActions(notificationState.getMarkAsReadIntent(context));

    ListIterator<NotificationItem> iterator = notifications.listIterator(0);

    while(iterator.hasNext()) {
      NotificationItem item = iterator.next();
      builder.addMessageBody(item.getIndividualRecipient(), item.getText());
    }

    if (notificationsRequested(flags)) {
      triggerNotificationAlarms(builder, notificationState, flags);

      builder.setTicker(notifications.get(0).getText());
    }

    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
      .notify(NOTIFICATION_ID, builder.build());
  }

  private static void sendInThreadNotification(Context context, Recipients recipients) {
    try {
      if (!SMSSecurePreferences.isInThreadNotifications(context)) {
        return;
      }

      Uri uri = recipients != null ? recipients.getRingtone() : null;

      if (uri == null) {
        String ringtone = SMSSecurePreferences.getNotificationRingtone(context);

        if (ringtone == null) {
          Log.w(TAG, "ringtone preference was null.");
          return;
        } else {
          uri = Uri.parse(ringtone);
        }
      }

      if (uri == null) {
        Log.w(TAG, "couldn't parse ringtone uri " + SMSSecurePreferences.getNotificationRingtone(context));
        return;
      }

      MediaPlayer player = new MediaPlayer();
      player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
      player.setDataSource(context, uri);
      player.setLooping(false);
      player.setVolume(0.25f, 0.25f);
      player.prepare();

      final AudioManager audioManager = ((AudioManager)context.getSystemService(Context.AUDIO_SERVICE));

      audioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION,
                                     AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

      player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
          audioManager.abandonAudioFocus(null);
        }
      });

      player.start();
    } catch (IOException ioe) {
      Log.w("MessageNotifier", ioe);
    }
  }

  private static NotificationState constructNotificationState(Context context,
                                                              MasterSecret masterSecret,
                                                              Cursor cursor)
  {
    NotificationState notificationState = new NotificationState();
    MessageRecord record;
    MmsSmsDatabase.Reader reader;

    if (masterSecret == null) reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor);
    else                      reader = DatabaseFactory.getMmsSmsDatabase(context).readerFor(cursor, masterSecret);

    while ((record = reader.getNext()) != null) {
      Recipient                       recipient        = record.getIndividualRecipient();
      Recipients                      recipients       = record.getRecipients();
      long                            threadId         = record.getThreadId();
      CharSequence                    body             = record.getDisplayBody();
      Recipients                      threadRecipients = null;
      ListenableFutureTask<SlideDeck> slideDeck        = null;
      long            timestamp;

      if (SMSSecurePreferences.showSentTime(context)) timestamp = record.getDateSent();
      else                                            timestamp = record.getDateReceived();

      if (threadId != -1) {
        threadRecipients = DatabaseFactory.getThreadDatabase(context).getRecipientsForThreadId(threadId);
      }

      if (SmsDatabase.Types.isDecryptInProgressType(record.getType()) || !record.getBody().isPlaintext()) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_encrypted_message));
      } else if (record.isMms() && TextUtils.isEmpty(body)) {
        body = SpanUtil.italic(context.getString(R.string.MessageNotifier_media_message));
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeckFuture();
      } else if (record.isMms() && !record.isMmsNotification()) {
        String message      = context.getString(R.string.MessageNotifier_media_message_with_text, body);
        int    italicLength = message.length() - body.length();
        body = SpanUtil.italic(message, italicLength);
        slideDeck = ((MediaMmsMessageRecord)record).getSlideDeckFuture();
      }

      if (threadRecipients == null || !threadRecipients.isMuted()) {
        notificationState.addNotification(new NotificationItem(recipient, recipients, threadRecipients, threadId, body, timestamp, slideDeck));
      }
    }

    reader.close();
    return notificationState;
  }

  private static void scheduleReminder(Context context, int count) {
    if (count >= SMSSecurePreferences.getRepeatAlertsCount(context)) {
      return;
    }

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    Intent       alarmIntent  = new Intent(ReminderReceiver.REMINDER_ACTION);
    alarmIntent.putExtra("reminder_count", count);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    long          timeout       = TimeUnit.MINUTES.toMillis(2);

    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, pendingIntent);
  }

  private static void clearReminder(Context context) {
    Intent        alarmIntent   = new Intent(ReminderReceiver.REMINDER_ACTION);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent, PendingIntent.FLAG_CANCEL_CURRENT);
    AlarmManager  alarmManager  = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    alarmManager.cancel(pendingIntent);
  }

  public static class ReminderReceiver extends BroadcastReceiver {

    public static final String REMINDER_ACTION = "org.smssecure.smssecure.MessageNotifier.REMINDER_ACTION";

    @Override
    public void onReceive(final Context context, final Intent intent) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MasterSecret masterSecret  = KeyCachingService.getMasterSecret(context);
          int          reminderCount = intent.getIntExtra("reminder_count", 0);
          MessageNotifier.updateNotification(context, masterSecret, MNF_DEFAULTS, reminderCount + 1);

          return null;
        }
      }.execute();
    }
  }

  public static class DeleteReceiver extends BroadcastReceiver {

    public static final String DELETE_REMINDER_ACTION = "org.smssecure.smssecure.MessageNotifier.DELETE_REMINDER_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
      clearReminder(context);
    }
  }
}
