package org.smssecure.smssecure.jobs;

import android.content.Context;

import org.smssecure.jobqueue.Job;
import org.smssecure.jobqueue.JobParameters;
import org.smssecure.jobqueue.dependencies.ContextDependent;

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
