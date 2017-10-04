/**
 * Copyright (C) 2015 Open Whisper Systems
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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;

import org.smssecure.smssecure.attachments.Attachment;
import org.smssecure.smssecure.attachments.UriAttachment;
import org.smssecure.smssecure.ConversationListAdapter.ItemClickListener;
import org.smssecure.smssecure.components.reminder.DefaultSmsReminder;
import org.smssecure.smssecure.components.reminder.DeliveryReportsReminder;
import org.smssecure.smssecure.components.reminder.Reminder;
import org.smssecure.smssecure.components.reminder.ReminderView;
import org.smssecure.smssecure.components.reminder.StoreRatingReminder;
import org.smssecure.smssecure.components.reminder.SystemSmsImportReminder;
import org.smssecure.smssecure.crypto.MasterCipher;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SessionUtil;
import org.smssecure.smssecure.database.AttachmentDatabase;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.DraftDatabase;
import org.smssecure.smssecure.database.ThreadDatabase;
import org.smssecure.smssecure.database.loaders.ConversationListLoader;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.mms.OutgoingMediaMessage;
import org.smssecure.smssecure.mms.OutgoingSecureMediaMessage;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.sms.MessageSender;
import org.smssecure.smssecure.sms.OutgoingEncryptedMessage;
import org.smssecure.smssecure.sms.OutgoingTextMessage;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.smssecure.smssecure.util.Util;
import org.smssecure.smssecure.util.ViewUtil;
import org.smssecure.smssecure.util.task.SnackbarAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Set;

public class ConversationListFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback, ItemClickListener
{

  private static final String TAG = ConversationListFragment.class.getSimpleName();

  public static final String ARCHIVE = "archive";

  private MasterSecret         masterSecret;
  private ActionMode           actionMode;
  private RecyclerView         list;
  private ReminderView         reminderView;
  private FloatingActionButton fab;
  private Locale               locale;
  private String               queryFilter  = "";
  private boolean              archive;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    masterSecret = getArguments().getParcelable("master_secret");
    locale       = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
    archive      = getArguments().getBoolean(ARCHIVE, false);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_list_fragment, container, false);
    reminderView = ViewUtil.findById(view, R.id.reminder);
    list         = ViewUtil.findById(view, R.id.list);
    fab          = ViewUtil.findById(view, R.id.fab);

    if (archive) fab.setVisibility(View.GONE);
    else         fab.setVisibility(View.VISIBLE);

    list.setHasFixedSize(true);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));

    new ItemTouchHelper(new ArchiveListenerCallback()).attachToRecyclerView(list);

    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    fab.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivity(new Intent(getActivity(), NewConversationActivity.class));
      }
    });
    initializeListAdapter();
  }

  @Override
  public void onResume() {
    super.onResume();

    initializeReminders();
    list.getAdapter().notifyDataSetChanged();
  }

  public ConversationListAdapter getListAdapter() {
    return (ConversationListAdapter) list.getAdapter();
  }

  public void setQueryFilter(String query) {
    this.queryFilter = query;
    getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    if (!TextUtils.isEmpty(this.queryFilter)) {
      setQueryFilter("");
    }
  }

  private void initializeReminders() {
    reminderView.hide();
    new AsyncTask<Context, Void, Optional<? extends Reminder>>() {
      @Override protected Optional<? extends Reminder> doInBackground(Context... params) {
        final Context context = params[0];
         if (DefaultSmsReminder.isEligible(context)) {
          return Optional.of(new DefaultSmsReminder(context));
        } else if (Util.isDefaultSmsProvider(context) && SystemSmsImportReminder.isEligible(context)) {
          return Optional.of((new SystemSmsImportReminder(context, masterSecret)));
        } else if (DeliveryReportsReminder.isEligible(context)) {
          return Optional.of((new DeliveryReportsReminder(context)));
        } else if (StoreRatingReminder.isEligible(context)) {
          return Optional.of((new StoreRatingReminder(context)));
        } else {
          return Optional.absent();
        }
      }

      @Override protected void onPostExecute(Optional<? extends Reminder> reminder) {
        if (reminder.isPresent() && getActivity() != null && !isRemoving()) {
          reminderView.showReminder(reminder.get());
        }
      }
    }.execute(getActivity());
  }

  private void initializeListAdapter() {
    list.setAdapter(new ConversationListAdapter(getActivity(), masterSecret, locale, null, this));
    getLoaderManager().restartLoader(0, null, this);
  }

  private void handleArchiveAllSelected() {
    final Set<Long> selectedConversations = new HashSet<>(getListAdapter().getBatchSelections());
    final boolean   archive               = this.archive;

    int snackBarTitleId;

    if (archive) snackBarTitleId = R.plurals.ConversationListFragment_moved_conversations_to_inbox;
    else         snackBarTitleId = R.plurals.ConversationListFragment_conversations_archived;

    int count            = selectedConversations.size();
    String snackBarTitle = getResources().getQuantityString(snackBarTitleId, count, count);

    new SnackbarAsyncTask<Void>(getView(), snackBarTitle,
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG, true)
    {

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);

        if (actionMode != null) {
          actionMode.finish();
          actionMode = null;
        }
      }

      @Override
      protected void executeAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          if (!archive) DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
          else          DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
        }
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          if (!archive) DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
          else          DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
        }
      }
    }.execute();
  }

  private void handleDeleteAllSelected() {
    int                 conversationsCount = getListAdapter().getBatchSelections().size();
    AlertDialog.Builder alert              = new AlertDialog.Builder(getActivity());
    alert.setIconAttribute(R.attr.dialog_alert_icon);
    alert.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                                  conversationsCount, conversationsCount));
    alert.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                                    conversationsCount, conversationsCount));
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        final Set<Long> selectedConversations = (getListAdapter())
            .getBatchSelections();

        if (!selectedConversations.isEmpty()) {
          new AsyncTask<Void, Void, Void>() {
            private ProgressDialog dialog;

            @Override
            protected void onPreExecute() {
              dialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ConversationListFragment_deleting),
                                           getActivity().getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                           true, false);
            }

            @Override
            protected Void doInBackground(Void... params) {
              DatabaseFactory.getThreadDatabase(getActivity()).deleteConversations(selectedConversations);
              MessageNotifier.updateNotification(getActivity(), masterSecret);
              return null;
            }

            @Override
            protected void onPostExecute(Void result) {
              dialog.dismiss();
              if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
              }
            }
          }.execute();
        }
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handleSelectAllThreads() {
    getListAdapter().selectAllThreads();
    actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                                     getListAdapter().getBatchSelections().size()));
  }

  private void handleCreateConversation(long threadId, Recipients recipients, int distributionType, long lastSeen) {
    ((ConversationSelectedListener)getActivity()).onCreateConversation(threadId, recipients, distributionType, lastSeen);
  }

  private void handleSendDrafts() {
    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
    alert.setIconAttribute(R.attr.dialog_alert_icon);
    alert.setTitle(getString(R.string.ConversationListFragment_send_drafts));
    alert.setMessage(getString(R.string.ConversationListFragment_this_will_send_drafts_of_selected_conversations));
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.ConversationListFragment_send, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        final Set<Long> selectedConversations = new HashSet<>(getListAdapter().getBatchSelections());
        final Context context = getActivity();

        if (!selectedConversations.isEmpty() && masterSecret != null) {
          final MasterCipher masterCipher = new MasterCipher(masterSecret);

          new AsyncTask<Void, Void, Void>() {
            private ProgressDialog dialog;
            private boolean        isSingleConversation;
            private boolean        isSecureDestination;
            private DraftDatabase  draftDatabase;
            private Recipients     recipients;

            @Override
            protected void onPreExecute() {
              dialog = ProgressDialog.show(context,
                                           context.getString(R.string.ConversationListFragment_sending),
                                           context.getString(R.string.ConversationListFragment_sending_selected_drafts),
                                           true, false);
            }

            @Override
            protected Void doInBackground(Void... params) {
              draftDatabase = DatabaseFactory.getDraftDatabase(context);

              for (long threadId : selectedConversations) {
                List<DraftDatabase.Draft> drafts = draftDatabase.getDrafts(masterCipher, threadId);
                recipients = getListAdapter().getRecipientsFromThreadId(threadId);

                if (recipients != null) {
                  int subscriptionId = SubscriptionManagerCompat.getDefaultMessagingSubscriptionId().or(-1);
                  isSingleConversation = recipients.isSingleRecipient() && !recipients.isGroupRecipient();
                  isSecureDestination  = isSingleConversation && SessionUtil.hasSession(context, masterSecret, recipients.getPrimaryRecipient().getNumber(), subscriptionId);

                  Log.w(TAG, "Number of drafts: " + drafts.size());
                  if (drafts.size() > 1 && !drafts.get(1).getType().equals(DraftDatabase.Draft.TEXT)) {
                    sendMediaDraft(drafts.get(1), threadId, drafts.get(0).getValue());
                  } else {
                    for (DraftDatabase.Draft draft : drafts) {
                      Log.w(TAG, "getType(): " + draft.getType());
                      if (draft.getType().equals(DraftDatabase.Draft.TEXT)) {
                        sendTextDraft(draft, threadId);
                      } else {
                        sendMediaDraft(draft, threadId, null);
                      }
                    }
                  }
                } else {
                  Log.w(TAG, "null recipients when sending drafts ?!");
                }
                draftDatabase.clearDrafts(threadId);
              }
              return null;
            }

            @Override
            protected void onPostExecute(Void result) {
              dialog.dismiss();
              if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
              }
            }

            private void sendTextDraft(DraftDatabase.Draft draft, long threadId) {
              OutgoingTextMessage message;
              if (isSecureDestination) {
                message = new OutgoingEncryptedMessage(recipients, draft.getValue(), -1);
              } else {
                message = new OutgoingTextMessage(recipients, draft.getValue(), -1);
              }
              MessageSender.send(context, masterSecret, message, threadId, false);
            }

            private void sendMediaDraft(DraftDatabase.Draft draft, long threadId, @Nullable String forcedValue) {
              List<Attachment> attachment = new LinkedList<Attachment>();
              attachment.add(new UriAttachment(Uri.parse(draft.getValue()), draft.getType() + "/*", AttachmentDatabase.TRANSFER_PROGRESS_DONE));

              OutgoingMediaMessage message = new OutgoingMediaMessage(recipients,
                                                                      forcedValue != null ? forcedValue : "",
                                                                      attachment,
                                                                      System.currentTimeMillis(),
                                                                      -1,
                                                                      ThreadDatabase.DistributionTypes.BROADCAST);

              if (isSecureDestination) {
                message = new OutgoingSecureMediaMessage(message);
              }
              MessageSender.send(context, masterSecret, message, threadId, false);
            }
          }.execute();
        }
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationListLoader(getActivity(), queryFilter, archive);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    getListAdapter().changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    getListAdapter().changeCursor(null);
  }

  @Override
  public void onItemClick(ConversationListItem item) {
    if (actionMode == null) {
      handleCreateConversation(item.getThreadId(), item.getRecipients(),
                               item.getDistributionType(), item.getLastSeen());
    } else {
      ConversationListAdapter adapter = (ConversationListAdapter)list.getAdapter();
      adapter.toggleThreadInBatchSet(item.getThreadId());
      adapter.populateRecipients(item.getThreadId(), item.getRecipients());

      if (adapter.getBatchSelections().size() == 0) {
        actionMode.finish();
      } else {
        actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                                         adapter.getBatchSelections().size()));
      }

      adapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onItemLongClick(ConversationListItem item) {
    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(ConversationListFragment.this);

    getListAdapter().initializeBatchMode(true);
    getListAdapter().toggleThreadInBatchSet(item.getThreadId());
    getListAdapter().populateRecipients(item.getThreadId(), item.getRecipients());
    getListAdapter().notifyDataSetChanged();
  }

  @Override
  public void onSwitchToArchive() {
    ((ConversationSelectedListener)getActivity()).onSwitchToArchive();
  }

  public interface ConversationSelectedListener {
    void onCreateConversation(long threadId, Recipients recipients, int distributionType, long lastSeen);
    void onSwitchToArchive();
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getActivity().getMenuInflater();

    if (archive) inflater.inflate(R.menu.conversation_list_batch_unarchive, menu);
    else         inflater.inflate(R.menu.conversation_list_batch_archive, menu);

    inflater.inflate(R.menu.conversation_list_batch, menu);
    inflater.inflate(R.menu.conversation_send_drafts, menu);

    mode.setTitle(R.string.conversation_fragment_cab__batch_selection_mode);
    mode.setSubtitle(null);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Window window = getActivity().getWindow();
      window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
      window.setNavigationBarColor(getResources().getColor(android.R.color.black));
    }

    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_select_all:       handleSelectAllThreads();   return true;
    case R.id.menu_delete_selected:  handleDeleteAllSelected();  return true;
    case R.id.menu_archive_selected: handleArchiveAllSelected(); return true;
    case R.id.menu_send_drafts:      handleSendDrafts();         return true;
    }

    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    getListAdapter().initializeBatchMode(false);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Window window = getActivity().getWindow();
      TypedArray color = getActivity().getTheme()
        .obtainStyledAttributes(new int[] { android.R.attr.statusBarColor });
      window.setStatusBarColor(color.getColor(0, Color.BLACK));
      window.setNavigationBarColor(getResources().getColor(android.R.color.black));
      color.recycle();
    }

    actionMode = null;
  }

  private class ArchiveListenerCallback extends ItemTouchHelper.SimpleCallback {

    public ArchiveListenerCallback() {
      super(0, ItemTouchHelper.RIGHT);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView,
                          RecyclerView.ViewHolder viewHolder,
                          RecyclerView.ViewHolder target)
    {
      return false;
    }

    @Override
    public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
      if (viewHolder.itemView instanceof ConversationListItemAction) {
        return 0;
      }

      if (actionMode != null) {
        return 0;
      }

      return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
      final long    threadId = ((ConversationListItem)viewHolder.itemView).getThreadId();
      final boolean read     = ((ConversationListItem)viewHolder.itemView).getRead();

      if (archive) {
        new SnackbarAsyncTask<Long>(getView(),
                                    getResources().getQuantityString(R.plurals.ConversationListFragment_moved_conversations_to_inbox, 1, 1),
                                    getString(R.string.ConversationListFragment_undo),
                                    getResources().getColor(R.color.amber_500),
                                    Snackbar.LENGTH_LONG, false)
        {
          @Override
          protected void executeAction(@Nullable Long parameter) {
            DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
          }

          @Override
          protected void reverseAction(@Nullable Long parameter) {
            DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
          }
        }.execute(threadId);
      } else {
        new SnackbarAsyncTask<Long>(getView(),
                                    getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
                                    getString(R.string.ConversationListFragment_undo),
                                    getResources().getColor(R.color.amber_500),
                                    Snackbar.LENGTH_LONG, false)
        {
          @Override
          protected void executeAction(@Nullable Long parameter) {
            DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);

            if (!read) {
              DatabaseFactory.getThreadDatabase(getActivity()).setRead(threadId);
              MessageNotifier.updateNotification(getActivity(), masterSecret);
            }
          }

          @Override
          protected void reverseAction(@Nullable Long parameter) {
            DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);

            if (!read) {
              DatabaseFactory.getThreadDatabase(getActivity()).setUnread(threadId);
              MessageNotifier.updateNotification(getActivity(), masterSecret);
            }
          }
        }.execute(threadId);
      }
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView,
                            RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState,
                            boolean isCurrentlyActive)
    {

      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        View  itemView = viewHolder.itemView;
        Paint p        = new Paint();

        if (dX > 0) {
          Bitmap icon;

          if (archive) icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_unarchive_white_36dp);
          else         icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_archive_white_36dp);

          p.setColor(getResources().getColor(R.color.green_500));

          c.drawRect((float) itemView.getLeft(), (float) itemView.getTop(), dX,
                     (float) itemView.getBottom(), p);

          c.drawBitmap(icon,
                       (float) itemView.getLeft() + getResources().getDimension(R.dimen.conversation_list_fragment_archive_padding),
                       (float) itemView.getTop() + ((float) itemView.getBottom() - (float) itemView.getTop() - icon.getHeight())/2,
                       p);
        }

        if (Build.VERSION.SDK_INT >= 11) {
          float alpha = 1.0f - Math.abs(dX) / (float) viewHolder.itemView.getWidth();
          viewHolder.itemView.setAlpha(alpha);
          viewHolder.itemView.setTranslationX(dX);
        }

      } else {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
      }
    }
  }

}
