/**
 * Copyright (C) 2011 Whisper Systems
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
package org.smssecure.smssecure;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import org.smssecure.smssecure.crypto.IdentityKeyParcelable;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.storage.SilenceIdentityKeyStore;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.EncryptingSmsDatabase;
import org.smssecure.smssecure.database.IdentityDatabase;
import org.smssecure.smssecure.database.model.MessageRecord;
import org.smssecure.smssecure.jobs.SmsDecryptJob;
import org.smssecure.smssecure.protocol.KeyExchangeMessage;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.sms.IncomingIdentityUpdateMessage;
import org.smssecure.smssecure.sms.IncomingKeyExchangeMessage;
import org.smssecure.smssecure.sms.IncomingPreKeyBundleMessage;
import org.smssecure.smssecure.sms.IncomingTextMessage;
import org.smssecure.smssecure.util.Base64;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

/**
 * Activity for displaying sent/received session keys.
 *
 * @author Moxie Marlinspike
 */

public class ReceiveKeyDialog extends AlertDialog {
  private static final String TAG = ReceiveKeyDialog.class.getSimpleName();

  private OnClickListener callback;

  public ReceiveKeyDialog(@NonNull Context context,
                          @NonNull MasterSecret masterSecret,
                          @NonNull MessageRecord messageRecord)
  {
    super(context);

    try{
      final IncomingKeyExchangeMessage message = getMessage(messageRecord);
      final IdentityKey identityKey = getIdentityKey(message);

      if (isTrusted(masterSecret, identityKey, messageRecord.getIndividualRecipient(), messageRecord.getSubscriptionId())){
        setMessage(context.getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_trusted_but));
      } else {
        setUntrustedText(messageRecord, identityKey);
      }

      setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.receive_key_activity__complete), new AcceptListener(masterSecret, messageRecord, message, identityKey));
      setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel), new CancelListener());

    } catch (InvalidKeyException | InvalidVersionException | InvalidMessageException | LegacyMessageException e) {
      throw new AssertionError(e);
    }

  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
            .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private void setUntrustedText(final MessageRecord messageRecord, final IdentityKey identityKey){
    String          introText       = getContext().getString(R.string.ReceiveKeyActivity_the_signature_on_this_key_exchange_is_different);
    SpannableString spannableString = new SpannableString(introText + " " +
                                                          getContext().getString(R.string.ConfirmIdentityDialog_you_may_wish_to_verify_this_contact));
    spannableString.setSpan(new ClickableSpan() {
                              @Override
                              public void onClick(View widget) {
                                Intent intent = new Intent(getContext(), VerifyIdentityActivity.class);
                                intent.putExtra("recipient", messageRecord.getIndividualRecipient().getRecipientId());
                                intent.putExtra("remote_identity", new IdentityKeyParcelable(identityKey));
                                getContext().startActivity(intent);
                              }
                            }, introText.length() + 1,
            spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    setMessage(spannableString);
  }

  private boolean isTrusted(MasterSecret masterSecret, IdentityKey identityKey, Recipient recipient, int subscriptionId) {
    IdentityKeyStore identityKeyStore = new SilenceIdentityKeyStore(getContext(), masterSecret, subscriptionId);

    return identityKeyStore.isTrustedIdentity(new SignalProtocolAddress(recipient.getNumber(), 1), identityKey);
  }

  private static IncomingKeyExchangeMessage getMessage(MessageRecord messageRecord)
      throws InvalidKeyException, InvalidVersionException,
             InvalidMessageException, LegacyMessageException
  {
    IncomingTextMessage message = new IncomingTextMessage(messageRecord.getIndividualRecipient().getNumber(),
                                                          messageRecord.getRecipientDeviceId(),
                                                          System.currentTimeMillis(),
                                                          messageRecord.getBody().getBody(),
                                                          messageRecord.getSubscriptionId());

    if (messageRecord.isBundleKeyExchange()) {
      return new IncomingPreKeyBundleMessage(message, message.getMessageBody());
    } else if (messageRecord.isIdentityUpdate()) {
      return new IncomingIdentityUpdateMessage(message, message.getMessageBody());
    } else {
      return new IncomingKeyExchangeMessage(message, message.getMessageBody());
    }
  }

  private static IdentityKey getIdentityKey(IncomingKeyExchangeMessage message)
          throws InvalidKeyException, InvalidVersionException,
          InvalidMessageException, LegacyMessageException
  {
    try {
      if (message.isIdentityUpdate()) {
        return new IdentityKey(Base64.decodeWithoutPadding(message.getMessageBody()), 0);
      } else if (message.isPreKeyBundle()) {
        return new PreKeySignalMessage(Base64.decodeWithoutPadding(message.getMessageBody())).getIdentityKey();
      } else {
        return new KeyExchangeMessage(Base64.decodeWithoutPadding(message.getMessageBody())).getIdentityKey();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private class CancelListener implements OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }

  private class AcceptListener implements OnClickListener {

    private MasterSecret                masterSecret;
    private MessageRecord               messageRecord;
    private IncomingKeyExchangeMessage  message;
    private IdentityKey                 identityKey;

    private AcceptListener(MasterSecret masterSecret,
                           MessageRecord messageRecord,
                           IncomingKeyExchangeMessage message,
                           IdentityKey identityKey)
    {
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.message       = message;
      this.identityKey   = identityKey;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>(){
        @Override
        protected Void doInBackground(Void... params) {

          Context               context          = getContext();
          IdentityDatabase      identityDatabase = DatabaseFactory.getIdentityDatabase(context);
          EncryptingSmsDatabase smsDatabase      = DatabaseFactory.getEncryptingSmsDatabase(context);

          identityDatabase.saveIdentity(masterSecret,
                  messageRecord.getIndividualRecipient().getRecipientId(),
                  identityKey);

          if (message.isIdentityUpdate()) {
            smsDatabase.markAsProcessedKeyExchange(messageRecord.getId());
          } else {
            ApplicationContext.getInstance(getContext())
                    .getJobManager()
                    .add(new SmsDecryptJob(context, messageRecord.getId(), true, false));
          }
          return null;
        }
      }.execute();

      if (callback != null) callback.onClick(null, 0);
    }
  }
}
