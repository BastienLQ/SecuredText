package org.smssecure.smssecure.util.dualsim;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.smssecure.smssecure.util.ServiceUtil;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class SubscriptionManagerCompat {
  private static final String TAG = SubscriptionManagerCompat.class.getSimpleName();

  private static SubscriptionManagerCompat instance;

  private final Context                      context;
  private       List<String>                 displayNameList;
  private       List<SubscriptionInfoCompat> compatList;

  public static SubscriptionManagerCompat from(Context context) {
    Log.w(TAG, "from()");

    if (instance == null) {
      instance = new SubscriptionManagerCompat(context);
    }
    return instance;
  }

  private SubscriptionManagerCompat(Context context) {
    this.context = context.getApplicationContext();
    this.displayNameList = new LinkedList<String>();
  }

  public Optional<SubscriptionInfoCompat> getActiveSubscriptionInfo(int subscriptionId) {
    if (getActiveSubscriptionInfoList().size() <= 0) {
      return Optional.absent();
    }

    for (SubscriptionInfoCompat subscriptionInfo : getActiveSubscriptionInfoList()) {
      if (subscriptionInfo.getSubscriptionId() == subscriptionId) return Optional.of(subscriptionInfo);
    }

    return Optional.absent();
  }

  public Optional<SubscriptionInfoCompat> getActiveSubscriptionInfoFromDeviceSubscriptionId(int subscriptionId) {
    if (getActiveSubscriptionInfoList().size() <= 0) {
      return Optional.absent();
    }

    for (SubscriptionInfoCompat subscriptionInfo : getActiveSubscriptionInfoList()) {
      if (subscriptionInfo.getDeviceSubscriptionId() == subscriptionId) return Optional.of(subscriptionInfo);
    }

    return Optional.absent();
  }

  @TargetApi(22)
  private void updateDisplayNameList(List<SubscriptionInfo> activeSubscriptions) {
    displayNameList = new LinkedList<String>();

    if (activeSubscriptions != null) {
      for (SubscriptionInfo subscriptionInfo : activeSubscriptions) {
        displayNameList.add(subscriptionInfo.getDisplayName().toString());
      }
    }
  }

  public boolean knowThisDisplayNameTwice(CharSequence displayName) {
    if (displayName == null) return false;

    boolean found = false;

    for (String potentialDisplayName : displayNameList) {
      if (found && potentialDisplayName != null && potentialDisplayName.equals(displayName.toString()))
        return true;
      if (potentialDisplayName != null && potentialDisplayName.equals(displayName.toString()))
        found = true;
    }
    return false;
  }

  public @NonNull List<SubscriptionInfoCompat> getActiveSubscriptionInfoList() {
    if (compatList == null) return updateActiveSubscriptionInfoList();
    return compatList;
  }

  public @NonNull List<SubscriptionInfoCompat> updateActiveSubscriptionInfoList() {
    compatList = new LinkedList<>();

    if (Build.VERSION.SDK_INT < 22) {
      TelephonyManager telephonyManager = ServiceUtil.getTelephonyManager(context);
      compatList.add(new SubscriptionInfoCompat(context,
                                                -1,
                                                telephonyManager.getSimOperatorName(),
                                                telephonyManager.getLine1Number(),
                                                telephonyManager.getSimSerialNumber(),
                                                1,
                                                false));
      return compatList;
    }

    List<SubscriptionInfo> subscriptionInfos = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
    updateDisplayNameList(subscriptionInfos);

    if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
      return compatList;
    }

    for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
      compatList.add(new SubscriptionInfoCompat(context,
                                                subscriptionInfo.getSubscriptionId(),
                                                subscriptionInfo.getDisplayName(),
                                                subscriptionInfo.getNumber(),
                                                subscriptionInfo.getIccId(),
                                                subscriptionInfo.getSimSlotIndex()+1,
                                                knowThisDisplayNameTwice(subscriptionInfo.getDisplayName())));
    }

    return compatList;
  }

  public static Optional<Integer> getDefaultMessagingSubscriptionId() {
    if (Build.VERSION.SDK_INT < 22) {
      return Optional.absent();
    }
    if(SmsManager.getDefaultSmsSubscriptionId() < 0) {
      return Optional.absent();
    }

    return Optional.of(SmsManager.getDefaultSmsSubscriptionId());
  }

}
