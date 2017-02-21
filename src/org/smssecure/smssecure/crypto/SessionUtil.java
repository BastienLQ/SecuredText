package org.smssecure.smssecure.crypto;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.NonNull;
import android.os.Build;

import org.smssecure.smssecure.crypto.storage.SilenceSessionStore;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionStore;

import java.util.List;
import java.util.LinkedList;

public class SessionUtil {

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String number, int subscriptionId) {
    SessionStore   sessionStore   = new SilenceSessionStore(context, masterSecret, subscriptionId);
    SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(number, 1);

    return sessionStore.containsSession(axolotlAddress);
  }

  public static boolean hasSession(Context context, MasterSecret masterSecret, @NonNull String number, List<SubscriptionInfoCompat> activeSubscriptions) {
    if (Build.VERSION.SDK_INT >= 22) {
      for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
        if (!hasSession(context, masterSecret, number, subscriptionInfo.getSubscriptionId())) return false;
      }
      return true;
    } else {
      return hasSession(context, masterSecret, number, -1);
    }
  }

  public static boolean hasAtLeastOneSession(Context context, MasterSecret masterSecret, @NonNull String number, List<SubscriptionInfoCompat> activeSubscriptions) {
    if (Build.VERSION.SDK_INT >= 22) {
      for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
        if (hasSession(context, masterSecret, number, subscriptionInfo.getSubscriptionId())) return true;
      }
      return false;
    } else {
      return hasSession(context, masterSecret, number, -1);
    }
  }

  @TargetApi(22)
  public static List<Integer> getSubscriptionIdWithoutSession(Context context, MasterSecret masterSecret, @NonNull String number, List<SubscriptionInfoCompat> activeSubscriptions) {
    LinkedList<Integer> list = new LinkedList();

    for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
      int subscriptionId = subscriptionInfo.getSubscriptionId();
      if (!hasSession(context, masterSecret, number, subscriptionId)) list.add(subscriptionId);
    }
    return list;
  }
}
