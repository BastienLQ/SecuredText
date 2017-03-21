package org.smssecure.smssecure.util.dualsim;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.smssecure.smssecure.crypto.IdentityKeyUtil;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.MasterSecretUtil;
import org.smssecure.smssecure.crypto.storage.SilenceSessionStore;
import org.smssecure.smssecure.R;
import org.smssecure.smssecure.util.ServiceUtil;
import org.smssecure.smssecure.util.SilencePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class DualSimUtil {
  private static final String TAG = DualSimUtil.class.getSimpleName();

  private static final int NOTIFICATION_ID = 1340;

  public static void moveIdentityKeysAndSessionsToSubscriptionId(Context context, int originalSubscriptionId, int subscriptionId) {
    Log.w(TAG, "moveIdentityKeysMasterSecretAndSessionsToSubscriptionId(" + originalSubscriptionId + ", " + subscriptionId + ")");

    moveIdentityKeysToSubscriptionId(context, originalSubscriptionId, subscriptionId);
    moveSessionsToSubscriptionId(context, originalSubscriptionId, subscriptionId);
  }

  private static void moveIdentityKeysToSubscriptionId(Context context, int originalSubscriptionId, int subscriptionId) {
    String originalIdentityPublicPref  = IdentityKeyUtil.getIdentityPublicKeyDjbPref(originalSubscriptionId);
    String identityPublicPref          = IdentityKeyUtil.getIdentityPublicKeyDjbPref(subscriptionId);
    String originalIdentityPrivatePref = IdentityKeyUtil.getIdentityPrivateKeyDjbPref(originalSubscriptionId);
    String identityPrivatePref         = IdentityKeyUtil.getIdentityPrivateKeyDjbPref(subscriptionId);

    Log.w(TAG, "Moving " + originalIdentityPublicPref  + " to " + identityPublicPref);
    Log.w(TAG, "Moving " + originalIdentityPrivatePref + " to " + identityPrivatePref);

    String identityPublicKey  = IdentityKeyUtil.retrieve(context, originalIdentityPublicPref);
    String identityPrivateKey = IdentityKeyUtil.retrieve(context, originalIdentityPrivatePref);

    IdentityKeyUtil.save(context, identityPublicPref, identityPublicKey);
    IdentityKeyUtil.save(context, identityPrivatePref, identityPrivateKey);

    IdentityKeyUtil.remove(context, originalIdentityPublicPref);
    IdentityKeyUtil.remove(context, originalIdentityPrivatePref);
  }

  private static void moveSessionsToSubscriptionId(Context context, int originalSubscriptionId, int subscriptionId) {
    File sessionDirectory = SilenceSessionStore.getSessionDirectory(context);

    File[] sessionList = sessionDirectory.listFiles();

    String destinationSuffix = subscriptionId != -1 ? "." + subscriptionId : "";

    for (File session : sessionList){
      if (session.isFile()){
        String absolutePath = session.getAbsolutePath();
        String newSessionName = null;

        if (originalSubscriptionId != -1 && absolutePath.endsWith("." + originalSubscriptionId)) {
          newSessionName = absolutePath.replaceAll("/\\." + originalSubscriptionId + "/g", destinationSuffix);
        } else if (originalSubscriptionId == -1) {
          newSessionName = absolutePath + destinationSuffix;
        }

        if (newSessionName != null) {
          Log.w(TAG, "Moving session " + absolutePath + " to " + newSessionName);
          File newFile = new File(newSessionName);
          if (session.renameTo(newFile)) {
            Log.w(TAG, "Done!");
          } else {
            Log.w(TAG, "Failed!");
          }
        }

      }
    }
  }

  public static void generateKeysIfDoNotExist(Context context, MasterSecret masterSecret, List<SubscriptionInfoCompat> activeSubscriptions) {
    generateKeysIfDoNotExist(context, masterSecret, activeSubscriptions, true);
  }

  public static void generateKeysIfDoNotExist(Context context, MasterSecret masterSecret, List<SubscriptionInfoCompat> activeSubscriptions, boolean displayNotification) {
    for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
      int subscriptionId = subscriptionInfo.getSubscriptionId();

      if (!IdentityKeyUtil.hasIdentityKey(context, subscriptionId))
        IdentityKeyUtil.generateIdentityKeys(context, masterSecret, subscriptionId, displayNotification);
    }
  }

  public static int getSubscriptionIdFromAppSubscriptionId(Context context, int appSubscriptionId) {
    Optional<SubscriptionInfoCompat> subscriptionInfo = SubscriptionManagerCompat.from(context).getActiveSubscriptionInfo(appSubscriptionId);
    if (subscriptionInfo.isPresent()) return subscriptionInfo.get().getDeviceSubscriptionId();
    else                              return -1;
  }

  public static int getSubscriptionIdFromDeviceSubscriptionId(Context context, int deviceSubscriptionId) {
    Optional<SubscriptionInfoCompat> subscriptionInfo = SubscriptionManagerCompat.from(context).getActiveSubscriptionInfoFromDeviceSubscriptionId(deviceSubscriptionId);
    if (subscriptionInfo.isPresent()) return subscriptionInfo.get().getSubscriptionId();
    else                              return -1;
  }

  public static void displayNotification(Context context) {
    Intent       targetIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    Notification notification = new NotificationCompat.Builder(context)
                                    .setSmallIcon(R.drawable.icon_notification)
                                    .setColor(context.getResources().getColor(R.color.silence_primary))
                                    .setContentTitle(context.getString(R.string.DualSimUtil__new_sim_card_detected))
                                    .setContentText(context.getString(R.string.DualSimUtil__a_new_key_has_been_generated))
                                    .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.DualSimUtil__a_new_key_has_been_generated_for_that_new_sim_card)))
                                    .setAutoCancel(true)
                                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                                    .setContentIntent(PendingIntent.getActivity(context, 0,
                                                                                targetIntent,
                                                                                PendingIntent.FLAG_UPDATE_CURRENT))
                                    .build();
    ServiceUtil.getNotificationManager(context).notify(NOTIFICATION_ID, notification);
  }
}
