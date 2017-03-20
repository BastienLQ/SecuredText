package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.util.dualsim.DualSimUtil;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.jobqueue.JobParameters;


import java.util.List;

public class GenerateKeysJob extends MasterSecretJob {
  private static final String TAG = GenerateKeysJob.class.getSimpleName();

  private List<SubscriptionInfoCompat> activeSubscriptions;

  public GenerateKeysJob(Context context) {
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
    List<SubscriptionInfoCompat> activeSubscriptions = SubscriptionManagerCompat.from(context).updateActiveSubscriptionInfoList();
    DualSimUtil.generateKeysIfDoNotExist(context, masterSecret, activeSubscriptions);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {}
}
