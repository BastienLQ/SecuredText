package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.util.dualsim.DualSimUpgradeUtil;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.jobqueue.JobParameters;


import java.util.List;

public class CheckSimStateJob extends MasterSecretJob {
  private static final String TAG = CheckSimStateJob.class.getSimpleName();

  private List<SubscriptionInfoCompat> activeSubscriptions;

  public CheckSimStateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());

    this.activeSubscriptions = activeSubscriptions;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) {
    Log.w(TAG, "onRun()");
    List<SubscriptionInfoCompat> activeSubscriptions = SubscriptionManagerCompat.from(context).getActiveSubscriptionInfoList();
    DualSimUpgradeUtil.bindAppSubscriptionId(context, activeSubscriptions);
    DualSimUpgradeUtil.generateKeysIfDoNotExist(context, masterSecret, activeSubscriptions);
    DualSimUpgradeUtil.bindSubscriptionId(context, activeSubscriptions);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {}
}
