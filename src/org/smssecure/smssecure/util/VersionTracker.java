package org.smssecure.smssecure.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import java.io.IOException;

public class VersionTracker {
  private static final String TAG = VersionTracker.class.getSimpleName();

  public static int getLastSeenVersion(Context context) {
    return SilencePreferences.getLastVersionCode(context);
  }

  public static void updateLastSeenVersion(Context context) {
    try {
      int currentVersionCode = Util.getCurrentApkReleaseVersion(context);
      SilencePreferences.setLastVersionCode(context, currentVersionCode);
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }

  public static boolean isDbUpdated(Context context) {
    try {
      PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      if (packageInfo == null) return true;

      return SilencePreferences.getLastVersionCode(context) >= packageInfo.versionCode;
    } catch (Exception e) {
      Log.w(TAG, e);
      return true;
    }
  }
}
