package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.os.PowerManager;

import org.smssecure.smssecure.util.DirectoryHelper;
import org.securesms.jobqueue.JobParameters;
import org.securesms.jobqueue.requirements.NetworkRequirement;
import org.securesms.textsecure.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class DirectoryRefreshJob extends ContextJob {

  public DirectoryRefreshJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withGroupId(DirectoryRefreshJob.class.getSimpleName())
                                .withRequirement(new NetworkRequirement(context))
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    PowerManager          powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Directory Refresh");

    try {
      wakeLock.acquire();
      DirectoryHelper.refreshDirectory(context);
    } finally {
      if (wakeLock.isHeld()) wakeLock.release();
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {}
}
