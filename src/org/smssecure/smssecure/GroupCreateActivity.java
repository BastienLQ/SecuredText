/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import org.smssecure.smssecure.components.PushRecipientsPanel;
import org.smssecure.smssecure.contacts.RecipientsEditor;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.ThreadDatabase;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.util.DynamicLanguage;
import org.smssecure.smssecure.util.DynamicTheme;
import org.smssecure.smssecure.util.InvalidNumberException;
import org.smssecure.smssecure.util.SelectedRecipientsAdapter;
import org.smssecure.smssecure.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


/**
 * Activity to create and update groups
 *
 * @author Jake McGinty
 */
public class GroupCreateActivity extends PassphraseRequiredActionBarActivity {

  private final static String TAG = GroupCreateActivity.class.getSimpleName();

  public static final String GROUP_RECIPIENT_EXTRA = "group_recipient";
  public static final String GROUP_THREAD_EXTRA    = "group_thread";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  private static final int PICK_AVATAR  = 2;
  public static final  int AVATAR_SIZE  = 210;

  private ListView            lv;
  private PushRecipientsPanel recipientsPanel;

  private Recipient      groupRecipient    = null;
  private long           groupThread       = -1;

  private MasterSecret   masterSecret;
  private Bitmap         avatarBmp;
  private Set<Recipient> selectedContacts;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    setContentView(R.layout.group_create_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    selectedContacts = new HashSet<>();
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_title);
  }

  private void addSelectedContact(Recipient contact) {
    if (!selectedContacts.contains(contact)) {
      selectedContacts.add(contact);
    }
  }

  private void addAllSelectedContacts(Collection<Recipient> contacts) {
    for (Recipient contact : contacts) {
      addSelectedContact(contact);
    }
  }

  private void removeSelectedContact(Recipient contact) {
    selectedContacts.remove(contact);
  }

  private void initializeResources() {
    lv              = (ListView)            findViewById(R.id.selected_contacts_list);
    recipientsPanel = (PushRecipientsPanel) findViewById(R.id.recipients);

    SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this, android.R.id.text1, new ArrayList<SelectedRecipientsAdapter.RecipientWrapper>());
    adapter.setOnRecipientDeletedListener(new SelectedRecipientsAdapter.OnRecipientDeletedListener() {
      @Override
      public void onRecipientDeleted(Recipient recipient) {
        removeSelectedContact(recipient);
      }
    });
    lv.setAdapter(adapter);

    recipientsPanel.setPanelChangeListener(new PushRecipientsPanel.RecipientsPanelChangedListener() {
      @Override
      public void onRecipientsPanelUpdate(Recipients recipients) {
        Log.i(TAG, "onRecipientsPanelUpdate received.");
        if (recipients != null) {
          addAllSelectedContacts(recipients.getRecipientsList());
          syncAdapterWithSelectedContacts();
        }
      }
    });
    (findViewById(R.id.contacts_button)).setOnClickListener(new AddRecipientButtonListener());

    ((RecipientsEditor)findViewById(R.id.recipients_text)).setHint(R.string.recipients_panel__add_member);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.group_create, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.menu_create_group:
        handleGroupCreate();
        return true;
    }

    return false;
  }

  private void handleGroupCreate() {
    if (selectedContacts.size() < 1) {
      Log.i(TAG, getString(R.string.GroupCreateActivity_contacts_no_members));
      Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_no_members, Toast.LENGTH_SHORT).show();
      return;
    }
    new CreateMmsGroupAsyncTask().execute();
  }

  private void syncAdapterWithSelectedContacts() {
    SelectedRecipientsAdapter adapter = (SelectedRecipientsAdapter)lv.getAdapter();
    adapter.clear();
    for (Recipient contact : selectedContacts) {
      adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, true));
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, final Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
      case PICK_CONTACT:
        List<String> selected = data.getStringArrayListExtra("contacts");
        for (String contact : selected) {
          Recipient recipient = RecipientFactory.getRecipientsFromString(this, contact, false).getPrimaryRecipient();

          if (!selectedContacts.contains(recipient) && recipient != null){
            addSelectedContact(recipient);
          }
        }
        syncAdapterWithSelectedContacts();
        break;
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, PushContactSelectionActivity.class);
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private long handleCreateMmsGroup(Set<Recipient> members) {
    Recipients recipients = RecipientFactory.getRecipientsFor(this, new LinkedList<>(members), false);
    return DatabaseFactory.getThreadDatabase(this)
                          .getThreadIdFor(recipients,
                                          ThreadDatabase.DistributionTypes.CONVERSATION);
  }

  private static <T> ArrayList<T> setToArrayList(Set<T> set) {
    ArrayList<T> arrayList = new ArrayList<T>(set.size());
      arrayList.addAll(set);
    return arrayList;
  }

  private Set<String> getE164Numbers(Set<Recipient> recipients)
      throws InvalidNumberException
  {
    Set<String> results = new HashSet<String>();

    for (Recipient recipient : recipients) {
      results.add(Util.canonicalizeNumber(this, recipient.getNumber()));
    }

    return results;
  }

  private class CreateMmsGroupAsyncTask extends AsyncTask<Void,Void,Long> {

    @Override
    protected Long doInBackground(Void... voids) {
      return handleCreateMmsGroup(selectedContacts);
    }

    @Override
    protected void onPostExecute(Long resultThread) {
      if (resultThread > -1) {
        Intent intent = new Intent(GroupCreateActivity.this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, resultThread.longValue());
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

        ArrayList<Recipient> selectedContactsList = setToArrayList(selectedContacts);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, RecipientFactory.getRecipientsFor(GroupCreateActivity.this, selectedContactsList, true).getIds());
        startActivity(intent);
        finish();
      } else {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_mms_exception, Toast.LENGTH_LONG).show();
        finish();
      }
    }

    @Override
    protected void onProgressUpdate(Void... values) {
      super.onProgressUpdate(values);
    }
  }
}
