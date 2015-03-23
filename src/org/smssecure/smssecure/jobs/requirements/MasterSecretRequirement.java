package org.smssecure.smssecure.jobs.requirements;

import android.content.Context;

import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.jobqueue.dependencies.ContextDependent;
import org.smssecure.jobqueue.requirements.Requirement;

public class MasterSecretRequirement implements Requirement, ContextDependent {

  private transient Context context;

  public MasterSecretRequirement(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return KeyCachingService.getMasterSecret(context) != null;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
