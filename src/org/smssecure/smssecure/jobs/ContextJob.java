package org.smssecure.smssecure.jobs;

import android.content.Context;

import org.securesms.jobqueue.Job;
import org.securesms.jobqueue.JobParameters;
import org.securesms.jobqueue.dependencies.ContextDependent;

public abstract class ContextJob extends Job implements ContextDependent {

  protected transient Context context;

  protected ContextJob(Context context, JobParameters parameters) {
    super(parameters);
    this.context = context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  protected Context getContext() {
    return context;
  }
}
