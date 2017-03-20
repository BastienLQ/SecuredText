/**
 * Copyright (C) 2013 Open Whisper Systems
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

package org.smssecure.smssecure;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.util.dualsim.DualSimUtil;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.smssecure.smssecure.util.ParcelUtil;
import org.smssecure.smssecure.util.Util;
import org.smssecure.smssecure.util.VersionTracker;
import org.smssecure.smssecure.util.SilencePreferences;
import org.whispersystems.jobqueue.EncryptionKeys;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class DatabaseUpgradeActivity extends BaseActivity {
  private static final String TAG = DatabaseUpgradeActivity.class.getSimpleName();

  public static final int MULTI_SIM_MULTI_KEYS_VERSION = 129;

  private static final SortedSet<Integer> UPGRADE_VERSIONS = new TreeSet<Integer>() {{
    add(MULTI_SIM_MULTI_KEYS_VERSION);
  }};

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = getIntent().getParcelableExtra("master_secret");

    if (needsUpgradeTask()) {
      Log.w(TAG, "Upgrading...");
      setContentView(R.layout.database_upgrade_activity);

      ProgressBar indeterminateProgress = (ProgressBar)findViewById(R.id.indeterminate_progress);
      ProgressBar determinateProgress   = (ProgressBar)findViewById(R.id.determinate_progress);

      new DatabaseUpgradeTask(indeterminateProgress, determinateProgress)
          .execute(VersionTracker.getLastSeenVersion(this));
    } else {
      VersionTracker.updateLastSeenVersion(this);
      ApplicationContext.getInstance(this)
                        .getJobManager()
                        .setEncryptionKeys(new EncryptionKeys(ParcelUtil.serialize(masterSecret)));
//      DecryptingQueue.schedulePendingDecrypts(DatabaseUpgradeActivity.this, masterSecret);
      updateNotifications(this, masterSecret);
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }
  }

  private boolean needsUpgradeTask() {
    int currentVersionCode = Util.getCurrentApkReleaseVersion(this);
    int lastSeenVersion    = VersionTracker.getLastSeenVersion(this);

    Log.w(TAG, "LastSeenVersion: " + lastSeenVersion);

    if (lastSeenVersion >= currentVersionCode)
      return false;

    for (int version : UPGRADE_VERSIONS) {
      Log.w(TAG, "Comparing: " + version);
      if (lastSeenVersion < version)
        return true;
    }

    return false;
  }

  public static boolean isUpdate(Context context) {
    try {
      int currentVersionCode  = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
      int previousVersionCode = VersionTracker.getLastSeenVersion(context);

      return previousVersionCode < currentVersionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  private void updateNotifications(final Context context, final MasterSecret masterSecret) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(context, masterSecret);
        return null;
      }
    }.execute();
  }

  public interface DatabaseUpgradeListener {
    public void setProgress(int progress, int total);
  }

  private class DatabaseUpgradeTask extends AsyncTask<Integer, Double, Void>
      implements DatabaseUpgradeListener
  {

    private final ProgressBar indeterminateProgress;
    private final ProgressBar determinateProgress;

    public DatabaseUpgradeTask(ProgressBar indeterminateProgress, ProgressBar determinateProgress) {
      this.indeterminateProgress = indeterminateProgress;
      this.determinateProgress   = determinateProgress;
    }

    @Override
    protected Void doInBackground(Integer... params) {
      Context context = DatabaseUpgradeActivity.this.getApplicationContext();

      Log.w(TAG, "Running background upgrade..");
      DatabaseFactory.getInstance(DatabaseUpgradeActivity.this)
                     .onApplicationLevelUpgrade(context, masterSecret, params[0], this);

      if (params[0] < MULTI_SIM_MULTI_KEYS_VERSION) {
        if (Build.VERSION.SDK_INT >= 22) {
          SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
          List<SubscriptionInfo> activeSubscriptions = subscriptionManager.getActiveSubscriptionInfoList();

          /*
           * getDefaultSubscriptionId() is available for API 24+ only, so we
           * move keys and sessions to SIM card in slot 1, not to the default one.
           */
          int defaultSubscriptionId = activeSubscriptions.get(0).getSubscriptionId();

          List<SubscriptionInfoCompat> activeSubscriptionsCompat = SubscriptionManagerCompat.from(context).getActiveSubscriptionInfoList();

          DualSimUtil.moveIdentityKeysAndSessionsToSubscriptionId(context, -1, defaultSubscriptionId);
          DualSimUtil.generateKeysIfDoNotExist(context, masterSecret, activeSubscriptionsCompat);
          SubscriptionManagerCompat.from(context).updateActiveSubscriptionInfoList();
        }
      }

      return null;
    }

    @Override
    protected void onProgressUpdate(Double... update) {
      indeterminateProgress.setVisibility(View.GONE);
      determinateProgress.setVisibility(View.VISIBLE);

      double scaler = update[0];
      determinateProgress.setProgress((int)Math.floor(determinateProgress.getMax() * scaler));
    }

    @Override
    protected void onPostExecute(Void result) {
      VersionTracker.updateLastSeenVersion(DatabaseUpgradeActivity.this);
//      DecryptingQueue.schedulePendingDecrypts(DatabaseUpgradeActivity.this, masterSecret);
      ApplicationContext.getInstance(DatabaseUpgradeActivity.this)
                        .getJobManager()
                        .setEncryptionKeys(new EncryptionKeys(ParcelUtil.serialize(masterSecret)));

      updateNotifications(DatabaseUpgradeActivity.this, masterSecret);

      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }

    @Override
    public void setProgress(int progress, int total) {
      publishProgress(((double)progress / (double)total));
    }
  }

}
