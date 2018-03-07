package org.smssecure.smssecure.mms;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.smssecure.smssecure.transport.UndeliverableMessageException;

import java.io.IOException;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.RetrieveConf;
import ws.com.google.android.mms.pdu.SendConf;

public class CompatMmsConnection implements OutgoingMmsConnection, IncomingMmsConnection {
  private static final String TAG = CompatMmsConnection.class.getSimpleName();

  private Context context;

  public CompatMmsConnection(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public SendConf send(@NonNull byte[] pduBytes, int subscriptionId)
      throws UndeliverableMessageException
  {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      try {
        return sendLollipop(context, pduBytes, subscriptionId);
      } catch (UndeliverableMessageException ume) {
        Log.w(TAG, ume);
        try {
          return sendLegacy(context, pduBytes, subscriptionId);
        } catch (UndeliverableMessageException | ApnUnavailableException e) {
          throw new UndeliverableMessageException(e);
        }
      }
    } else {
      try {
        return sendLegacy(context, pduBytes, subscriptionId);
      } catch (UndeliverableMessageException | ApnUnavailableException e) {
        throw new UndeliverableMessageException(e);
      }
    }
  }

  private static SendConf sendLollipop(Context context, @NonNull byte[] pduBytes, int subscriptionId)
    throws UndeliverableMessageException
  {
    Log.w(TAG, "Sending via Lollipop API");
    return new OutgoingLollipopMmsConnection(context).send(pduBytes, subscriptionId);
  }

  private static SendConf sendLegacy(Context context, @NonNull byte[] pduBytes, int subscriptionId)
    throws UndeliverableMessageException, ApnUnavailableException
  {
    Log.w(TAG, "Sending via legacy connection");
    return new OutgoingLegacyMmsConnection(context).send(pduBytes, subscriptionId);
  }

  @Nullable
  @Override
  public RetrieveConf retrieve(@NonNull String contentLocation,
                               byte[] transactionId,
                               int subscriptionId)
      throws MmsException, MmsRadioException, ApnUnavailableException, IOException
  {
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      Log.w(TAG, "Receiving via Lollipop API");
      return new IncomingLollipopMmsConnection(context).retrieve(contentLocation, transactionId, subscriptionId);
    } else {
      Log.w(TAG, "Receiving via legacy connection");
      return new IncomingLegacyMmsConnection(context).retrieve(contentLocation, transactionId, subscriptionId);
    }
  }
}
