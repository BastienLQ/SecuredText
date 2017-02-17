package org.smssecure.smssecure.protocol;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SessionUtil;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;

import java.util.Locale;

public class AutoInitiate {
  private static final String TAG = AutoInitiate.class.getSimpleName();

  public static final String WHITESPACE_TAG = "             ";

  public static boolean isTaggableMessage(String message) {
    return message.matches(".*[^\\s].*") &&
           message.replaceAll("\\s+$", "").length() + WHITESPACE_TAG.length() <= 158;
  }

  public static boolean isTaggableDestination(Recipients recipients){
    // Be safe - err on the side of not tagging

    if (recipients.isGroupRecipient())
      return false;

    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    try {
      PhoneNumber num = util.parse(recipients.getPrimaryRecipient().getNumber(),
                                   Locale.getDefault().getCountry());
      PhoneNumberType type = util.getNumberType(num);

      Log.d(TAG, "Number type: " + type.toString());

      return type == PhoneNumberType.FIXED_LINE ||
             type == PhoneNumberType.MOBILE     ||
             type == PhoneNumberType.FIXED_LINE_OR_MOBILE;
    }
    catch (NumberParseException e){
      Log.w(TAG, "Couldn't get number type (country: " + Locale.getDefault().getCountry() + ")");
      return false;
    }
  }

  public static boolean isTagged(String message) {
    return message != null && message.matches(".*[^\\s]" + WHITESPACE_TAG + "$");
  }

  public static String getTaggedMessage(String message) {
    return message.replaceAll("\\s+$", "") + WHITESPACE_TAG;
  }

  public static String stripTag(String message) {
    if (isTagged(message))
      return message.substring(0, message.length() - WHITESPACE_TAG.length());

    return message;
  }

  public static void exemptThread(Context context, long threadId) {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    sp.edit().putBoolean("pref_thread_auto_init_exempt_" + threadId, true).apply();
  }

  public static boolean isValidAutoInitiateSituation(Context context, MasterSecret masterSecret,
                                                     Recipient recipient, String message, long threadId)
  {
    return
        AutoInitiate.isTagged(message)       &&
        isThreadQualified(context, threadId) &&
        isExchangeQualified(context, masterSecret, recipient);
  }

  private static boolean isThreadQualified(Context context, long threadId) {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
    return !sp.getBoolean("pref_thread_auto_init_exempt_" + threadId, false);
  }

  private static boolean isExchangeQualified(Context context,
                                             MasterSecret masterSecret,
                                             Recipient recipient)
  {
    return !SessionUtil.hasSession(context, masterSecret, recipient.getNumber(), SubscriptionManagerCompat.from(context).getActiveSubscriptionInfoList());
  }

}
