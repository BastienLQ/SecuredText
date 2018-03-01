package org.smssecure.smssecure.notifications;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;

public class MarkReadReceiver extends MasterSecretBroadcastReceiver {

  private static final String TAG                   = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION          = "org.smssecure.smssecure.notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA      = "thread_ids";
  public static final  String NOTIFICATION_ID_EXTRA = "notification_id";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           @Nullable final MasterSecret masterSecret)
  {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      NotificationManagerCompat.from(context).cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1));

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          for (long threadId : threadIds) {
            Log.w(TAG, "Marking as read: " + threadId);
            DatabaseFactory.getThreadDatabase(context).setRead(threadId);
            DatabaseFactory.getThreadDatabase(context).setLastSeen(threadId);
          }

          MessageNotifier.updateNotification(context, masterSecret);
          return null;
        }
      }.execute();
    }
  }
}
