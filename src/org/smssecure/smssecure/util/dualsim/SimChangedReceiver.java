package org.smssecure.smssecure.util.dualsim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.smssecure.smssecure.ApplicationContext;
import org.smssecure.smssecure.util.SilencePreferences;
import org.smssecure.smssecure.jobs.CheckSimStateJob;

import java.util.Arrays;
import java.util.List;

public class SimChangedReceiver extends BroadcastReceiver {
  private static final String TAG = SimChangedReceiver.class.getSimpleName();

  private static final String SIM_STATE_CHANGED_EVENT = "android.intent.action.SIM_STATE_CHANGED";

  @Override
  public void onReceive(final Context context, final Intent intent) {
    Log.w(TAG, "onReceive()");

    if (intent.getAction().equals(SIM_STATE_CHANGED_EVENT)) {
      checkSimState(context);
    }
  }

  public static void checkSimState(final Context context) {
    if (hasDifferentSubscriptions(context)) {
      DualSimUpgradeUtil.checkAndFixAppSubscriptionIds(context);
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new CheckSimStateJob(context));
      SilencePreferences.setDeviceSubscriptions(context, getDeviceSubscriptions(context));
    }
  }

  private static boolean hasDifferentSubscriptions(Context context) {
    String subscriptions           = getDeviceSubscriptions(context);
    String registeredSubscriptions = getActiveDeviceSubscriptionIds(context);

    Log.w(TAG, "getDeviceSubscriptions():         " + getDeviceSubscriptions(context));
    Log.w(TAG, "getActiveDeviceSubscriptionIds(): " + getActiveDeviceSubscriptionIds(context));

    return subscriptions != null && !subscriptions.equals(registeredSubscriptions);
  }

  private static String getDeviceSubscriptions(Context context) {
    if (Build.VERSION.SDK_INT < 22) return null;

    SubscriptionManager    subscriptionManager = SubscriptionManager.from(context);
    List<SubscriptionInfo> activeSubscriptions = subscriptionManager.getActiveSubscriptionInfoList();

    if (activeSubscriptions == null) return null;

    String[] subscriptions = new String[activeSubscriptions.size()];
    for(int i=0; i<activeSubscriptions.size(); i++){
      subscriptions[i] = Integer.toString(activeSubscriptions.get(i).getSubscriptionId());
    }

    Arrays.sort(subscriptions);

    return joinString(subscriptions);
  }

  public static String getActiveDeviceSubscriptionIds(Context context) {
    String[] subscriptions = SilencePreferences.getDeviceSubscriptions(context).split(",");
    Arrays.sort(subscriptions);
    return joinString(subscriptions);
  }

  private static String joinString(String[] string) {
    String result = "";
    for(int i=0; i<string.length; i++){
      result = result + string[i];
      if (i != string.length-1) result = result + ",";
    }
    return result;
  }
}
