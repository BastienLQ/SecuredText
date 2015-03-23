package org.smssecure.smssecure.jobs.requirements;

import android.content.Context;

import org.smssecure.jobqueue.dependencies.ContextDependent;
import org.smssecure.jobqueue.requirements.NetworkRequirement;
import org.smssecure.jobqueue.requirements.Requirement;

public class NetworkOrServiceRequirement implements Requirement, ContextDependent {

  private transient Context context;

  public NetworkOrServiceRequirement(Context context) {
    this.context = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    NetworkRequirement networkRequirement = new NetworkRequirement(context);
    ServiceRequirement serviceRequirement = new ServiceRequirement(context);

    return networkRequirement.isPresent() || serviceRequirement.isPresent();
  }
}
