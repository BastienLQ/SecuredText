package org.smssecure.smssecure.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.database.NotInDirectoryException;
import org.smssecure.smssecure.database.SMSSecureDirectory;
import org.smssecure.smssecure.push.SMSSecureCommunicationFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.securesms.textsecure.api.TextSecureAccountManager;
import org.securesms.textsecure.api.push.ContactTokenDetails;
import org.securesms.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class DirectoryHelper {
  private static final String TAG = DirectoryHelper.class.getSimpleName();

  public static void refreshDirectoryWithProgressDialog(final Context context, final DirectoryUpdateFinishedListener listener) {
    if (!SMSSecurePreferences.isPushRegistered(context)) {
      Toast.makeText(context.getApplicationContext(),
                     context.getString(R.string.SingleContactSelectionActivity_you_are_not_registered_with_the_push_service),
                     Toast.LENGTH_LONG).show();
      return;
    }

    new ProgressDialogAsyncTask<Void,Void,Void>(context,
                                                R.string.SingleContactSelectionActivity_updating_directory,
                                                R.string.SingleContactSelectionActivity_updating_push_directory)
    {
      @Override
      protected Void doInBackground(Void... voids) {
        try {
          DirectoryHelper.refreshDirectory(context.getApplicationContext());
        } catch (IOException e) {
          Log.w(TAG, e);
        }
        return null;
      }

      @Override
      protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (listener != null) listener.onUpdateFinished();
      }
    }.execute();

  }

  public static void refreshDirectory(final Context context) throws IOException {
    refreshDirectory(context, SMSSecureCommunicationFactory.createManager(context));
  }

  public static void refreshDirectory(final Context context, final TextSecureAccountManager accountManager)
      throws IOException
  {
    refreshDirectory(context, accountManager, SMSSecurePreferences.getLocalNumber(context));
  }

  public static void refreshDirectory(final Context context, final TextSecureAccountManager accountManager, final String localNumber)
      throws IOException
  {
    SMSSecureDirectory       directory              = SMSSecureDirectory.getInstance(context);
    Set<String>               eligibleContactNumbers = directory.getPushEligibleContactNumbers(localNumber);
    List<ContactTokenDetails> activeTokens           = accountManager.getContacts(eligibleContactNumbers);

    if (activeTokens != null) {
      for (ContactTokenDetails activeToken : activeTokens) {
        eligibleContactNumbers.remove(activeToken.getNumber());
        activeToken.setNumber(activeToken.getNumber());
      }

      directory.setNumbers(activeTokens, eligibleContactNumbers);
    }
  }

  public static boolean isPushDestination(Context context, Recipients recipients) {
    try {
      if (recipients == null) {
        return false;
      }

      if (!SMSSecurePreferences.isPushRegistered(context)) {
        return false;
      }

      if (!recipients.isSingleRecipient()) {
        return false;
      }

      if (recipients.isGroupRecipient()) {
        return true;
      }

      final String number = recipients.getPrimaryRecipient().getNumber();

      if (number == null) {
        return false;
      }

      final String e164number = Util.canonicalizeNumber(context, number);

      return SMSSecureDirectory.getInstance(context).isActiveNumber(e164number);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    } catch (NotInDirectoryException e) {
      return false;
    }
  }

  public static boolean isSmsFallbackAllowed(Context context, Recipients recipients) {
    try {
      if (recipients == null || !recipients.isSingleRecipient() || recipients.isGroupRecipient()) {
        return false;
      }

      final String number = recipients.getPrimaryRecipient().getNumber();

      if (number == null) {
        return false;
      }

      final String e164number = Util.canonicalizeNumber(context, number);

      return SMSSecureDirectory.getInstance(context).isSmsFallbackSupported(e164number);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  public static interface DirectoryUpdateFinishedListener {
    public void onUpdateFinished();
  }
}
