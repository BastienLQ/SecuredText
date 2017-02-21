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
    int subscriptionId = SilencePreferences.getAppSubscriptionId(context, deviceSubscriptionId);

    this.context              = context;
    this.deviceSubscriptionId = deviceSubscriptionId;
    this.subscriptionId       = subscriptionId;
    this.displayName          = displayName;
    this.number               = number;
    this.iccId                = iccId;
    this.iccSlot              = iccSlot;
    this.duplicateDisplayName = duplicateDisplayName;
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
}
