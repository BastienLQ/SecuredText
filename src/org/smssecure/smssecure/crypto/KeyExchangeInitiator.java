/**
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure.crypto;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.crypto.SessionBuilder;
import org.smssecure.smssecure.crypto.storage.SilenceIdentityKeyStore;
import org.smssecure.smssecure.crypto.storage.SilencePreKeyStore;
import org.smssecure.smssecure.crypto.storage.SilenceSessionStore;
import org.smssecure.smssecure.protocol.KeyExchangeMessage;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.sms.MessageSender;
import org.smssecure.smssecure.sms.OutgoingEndSessionMessage;
import org.smssecure.smssecure.sms.OutgoingKeyExchangeMessage;
import org.smssecure.smssecure.sms.OutgoingTextMessage;
import org.smssecure.smssecure.util.Base64;
import org.smssecure.smssecure.util.ResUtil;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import java.util.List;

public class KeyExchangeInitiator {

  public static void abort(final Context context, final MasterSecret masterSecret, final Recipients recipients, final int subscriptionId) {
    OutgoingEndSessionMessage endSessionMessage = new OutgoingEndSessionMessage(new OutgoingTextMessage(recipients, "TERMINATE", subscriptionId));
    MessageSender.send(context, masterSecret, endSessionMessage, -1, false);
  }

  public static void initiate(final Context context, final MasterSecret masterSecret, final Recipients recipients, boolean promptOnExisting) {
    if (Build.VERSION.SDK_INT >= 22) {
      List<SubscriptionInfo> listSubscriptionInfo = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
      for (SubscriptionInfo subscriptionInfo : listSubscriptionInfo) {
        initiate(context, masterSecret, recipients, promptOnExisting, subscriptionInfo.getSubscriptionId());
      }
    } else {
      initiate(context, masterSecret, recipients, promptOnExisting, -1);
    }
  }

  public static void initiate(final Context context, final MasterSecret masterSecret, final Recipients recipients, boolean promptOnExisting, final int subscriptionId) {
    if (promptOnExisting && hasInitiatedSession(context, masterSecret, recipients, subscriptionId)) {
      AlertDialog.Builder dialog = new AlertDialog.Builder(context);
      dialog.setTitle(R.string.KeyExchangeInitiator_initiate_despite_existing_request_question);
      dialog.setMessage(R.string.KeyExchangeInitiator_youve_already_sent_a_session_initiation_request_to_this_recipient_are_you_sure);
      dialog.setIconAttribute(R.attr.dialog_alert_icon);
      dialog.setCancelable(true);
      dialog.setPositiveButton(R.string.KeyExchangeInitiator_send, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          initiateKeyExchange(context, masterSecret, recipients, subscriptionId);
        }
      });
      dialog.setNegativeButton(android.R.string.cancel, null);
      dialog.show();
    } else {
      initiateKeyExchange(context, masterSecret, recipients, subscriptionId);
    }
  }

  public static void initiateKeyExchange(Context context, MasterSecret masterSecret, Recipients recipients, int subscriptionId) {
    Recipient         recipient         = recipients.getPrimaryRecipient();
    SessionStore      sessionStore      = new SilenceSessionStore(context, masterSecret, subscriptionId);
    PreKeyStore       preKeyStore       = new SilencePreKeyStore(context, masterSecret, subscriptionId);
    SignedPreKeyStore signedPreKeyStore = new SilencePreKeyStore(context, masterSecret, subscriptionId);
    IdentityKeyStore  identityKeyStore  = new SilenceIdentityKeyStore(context, masterSecret, subscriptionId);

    SessionBuilder    sessionBuilder    = new SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore,
                                                             identityKeyStore, new SignalProtocolAddress(recipient.getNumber(), 1));

    KeyExchangeMessage         keyExchangeMessage = sessionBuilder.process();
    String                     serializedMessage  = Base64.encodeBytesWithoutPadding(keyExchangeMessage.serialize());
    OutgoingKeyExchangeMessage textMessage        = new OutgoingKeyExchangeMessage(recipients, serializedMessage, subscriptionId);

    MessageSender.send(context, masterSecret, textMessage, -1, false);
  }

  private static boolean hasInitiatedSession(Context context, MasterSecret masterSecret,
                                             Recipients recipients, int subscriptionId)
  {
    Recipient     recipient     = recipients.getPrimaryRecipient();
    SessionStore  sessionStore  = new SilenceSessionStore(context, masterSecret, subscriptionId);
    SessionRecord sessionRecord = sessionStore.loadSession(new SignalProtocolAddress(recipient.getNumber(), 1));

    return sessionRecord.getSessionState().hasPendingKeyExchange();
  }
}
