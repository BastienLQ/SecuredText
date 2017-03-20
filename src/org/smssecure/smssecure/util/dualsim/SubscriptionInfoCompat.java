package org.smssecure.smssecure.util.dualsim;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.util.SilencePreferences;

public class SubscriptionInfoCompat {

  private final Context                context;
  private final int                    deviceSubscriptionId;
  private       int                    subscriptionId;
  private final @Nullable CharSequence displayName;
  private final @Nullable String       number;
  private final @Nullable String       iccId;
  private final           int          iccSlot;
  private final           boolean      duplicateDisplayName;

  public SubscriptionInfoCompat(Context      context,
                                int          deviceSubscriptionId,
                      @Nullable CharSequence displayName,
                      @Nullable String       number,
                      @Nullable String       iccId,
                                int          iccSlot,
                                boolean      duplicateDisplayName)
  {
    this.context              = context;
    this.deviceSubscriptionId = deviceSubscriptionId;
    this.subscriptionId       = subscriptionId;
    this.displayName          = displayName;
    this.number               = number;
    this.iccId                = iccId;
    this.iccSlot              = iccSlot;
    this.duplicateDisplayName = duplicateDisplayName;
    this.subscriptionId       = findAppId(context, number, iccId);
  }

  public @NonNull CharSequence getDisplayName() {
    return (displayName != null && !displayName.equals("")) ? getEligibleDisplayName() : context.getString(R.string.SubscriptionInfoCompat_slot, iccSlot);
  }

  private String getEligibleDisplayName() {
    if (duplicateDisplayName && !getNumber().equals("")) {
      return getNumber();
    } else if (duplicateDisplayName) {
      return context.getString(R.string.SubscriptionInfoCompat_display_name, displayName, iccSlot);
    } else {
      return displayName.toString();
    }
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(int subscriptionId) {
    SilencePreferences.setAppSubscriptionId(context, deviceSubscriptionId, subscriptionId);
    this.subscriptionId = subscriptionId;
  }

  public int getDeviceSubscriptionId() {
    return deviceSubscriptionId;
  }

  public String getNumber() {
    return number != null ? number : "";
  }

  public String getIccId() {
    return iccId;
  }

  private static int findAppId(Context context, String number, String iccId) {
    int appSubscriptionId = -1;

    appSubscriptionId = findAppIdFromNumber(context, number);
    if (appSubscriptionId == -1) appSubscriptionId = findAppIdFromIccId(context, iccId);
    if (appSubscriptionId == -1) appSubscriptionId = bumpAppSubcriptionId(context);

    saveInfo(context, appSubscriptionId, number, iccId);

    return appSubscriptionId;
  }

  private static int findAppIdFromNumber(Context context, String number) {
    if (number == null || number.equals("")) return -1;

    int lastAppSubscriptionId = SilencePreferences.getLastAppSubscriptionId(context);
    for( int i = 0; i <= lastAppSubscriptionId; i++ ) {
      String eligibleNumber = SilencePreferences.getNumberForSubscriptionId(context, i);
      if (eligibleNumber != null && eligibleNumber.equals(number)) return i;
    }

    return -1;
  }

  private static int findAppIdFromIccId(Context context, String iccId) {
    if (iccId == null || iccId.equals("")) return -1;

    int lastAppSubscriptionId = SilencePreferences.getLastAppSubscriptionId(context);
    for( int i = 0; i <= lastAppSubscriptionId; i++ ) {
      String eligibleIccId = SilencePreferences.getIccIdForSubscriptionId(context, i);
      if (eligibleIccId != null && eligibleIccId.equals(iccId)) return i;
    }

    return -1;
  }

  private static int bumpAppSubcriptionId(Context context) {
    int lastAppSubscriptionId = SilencePreferences.getLastAppSubscriptionId(context);
    SilencePreferences.setLastAppSubscriptionId(context, lastAppSubscriptionId+1);

    return lastAppSubscriptionId+1;
  }

  private static void saveInfo(Context context, int appSubscriptionId, String number, String iccId) {
    if (number != null && !number.equals(""))
      SilencePreferences.setNumberForSubscriptionId(context, appSubscriptionId, number);

    if (iccId != null && !iccId.equals(""))
      SilencePreferences.setIccIdForSubscriptionId(context, appSubscriptionId, iccId);
  }
}
