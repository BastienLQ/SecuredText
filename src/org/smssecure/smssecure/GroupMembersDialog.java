package org.smssecure.smssecure;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.util.GroupUtil;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.smssecure.util.Util;
import org.smssecure.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, Recipients> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipients recipients;
  private final Context    context;

  private ProgressDialog progress = null;

  public GroupMembersDialog(Context context, Recipients recipients) {
    this.recipients = recipients;
    this.context    = context;
  }

  @Override
  public void onPreExecute() {
    progress = ProgressDialog.show(context, context.getString(R.string.GroupMembersDialog_members), context.getString(R.string.GroupMembersDialog_members), true, false);
  }

  @Override
  protected Recipients doInBackground(Void... params) {
    try {
      String groupId = recipients.getPrimaryRecipient().getNumber();
      return DatabaseFactory.getGroupDatabase(context)
                            .getGroupMembers(GroupUtil.getDecodedId(groupId), true);
    } catch (IOException e) {
      Log.w("ConverstionActivity", e);
      return new Recipients(new LinkedList<Recipient>());
    }
  }

  @Override
  public void onPostExecute(Recipients members) {
    if (progress != null) {
      progress.dismiss();
    }

    List<String> recipientStrings = new LinkedList<>();
    recipientStrings.add(context.getString(R.string.GroupMembersDialog_me));

    for (Recipient recipient : members.getRecipientsList()) {
      if (!isLocalNumber(recipient)) {
        recipientStrings.add(recipient.toShortString());
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationActivity_group_conversation_recipients);
    builder.setIcon(R.drawable.ic_menu_groups_holo_dark);
    builder.setCancelable(true);
    builder.setItems(recipientStrings.toArray(new String[]{}), null);
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    if (recipients.isGroupRecipient()) execute();
    else                               onPostExecute(recipients);
  }

  private boolean isLocalNumber(Recipient recipient) {
    try {
      String localNumber = SMSSecurePreferences.getLocalNumber(context);
      String e164Number  = Util.canonicalizeNumber(context, recipient.getNumber());

      return e164Number != null && e164Number.equals(localNumber);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      return false;
    }
  }
}
