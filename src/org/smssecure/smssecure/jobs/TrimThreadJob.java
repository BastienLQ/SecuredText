/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.jobqueue.Job;
import org.smssecure.jobqueue.JobParameters;

public class TrimThreadJob extends Job {

  private static final String TAG = TrimThreadJob.class.getSimpleName();

  private final Context context;
  private final long    threadId;

  public TrimThreadJob(Context context, long threadId) {
    super(JobParameters.newBuilder().withGroupId(TrimThreadJob.class.getSimpleName()).create());
    this.context  = context;
    this.threadId = threadId;
  }

  @Override
  public void onAdded() {

  }

  @Override
  public void onRun() {
    boolean trimmingEnabled   = SMSSecurePreferences.isThreadLengthTrimmingEnabled(context);
    int     threadLengthLimit = SMSSecurePreferences.getThreadTrimLength(context);

    if (!trimmingEnabled)
      return;

    DatabaseFactory.getThreadDatabase(context).trimThread(threadId, threadLengthLimit);
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Canceling trim attempt: " + threadId);
  }
}
