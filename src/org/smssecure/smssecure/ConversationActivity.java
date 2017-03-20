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

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.ByteString;

import org.smssecure.smssecure.TransportOptions.OnTransportChangedListener;
import org.smssecure.smssecure.audio.AudioSlidePlayer;
import org.smssecure.smssecure.color.MaterialColor;
import org.smssecure.smssecure.components.AnimatingToggle;
import org.smssecure.smssecure.components.ComposeText;
import org.smssecure.smssecure.components.KeyboardAwareLinearLayout;
import org.smssecure.smssecure.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.smssecure.smssecure.components.SendButton;
import org.smssecure.smssecure.components.InputAwareLayout;
import org.smssecure.smssecure.components.emoji.EmojiDrawer.EmojiEventListener;
import org.smssecure.smssecure.components.emoji.EmojiDrawer;
import org.smssecure.smssecure.components.emoji.EmojiToggle;
import org.smssecure.smssecure.contacts.ContactAccessor;
import org.smssecure.smssecure.contacts.ContactAccessor.ContactData;
import org.smssecure.smssecure.crypto.KeyExchangeInitiator;
import org.smssecure.smssecure.crypto.MasterCipher;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SecurityEvent;
import org.smssecure.smssecure.crypto.SessionUtil;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.DraftDatabase;
import org.smssecure.smssecure.database.DraftDatabase.Draft;
import org.smssecure.smssecure.database.DraftDatabase.Drafts;
import org.smssecure.smssecure.database.MmsSmsColumns.Types;
import org.smssecure.smssecure.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.smssecure.smssecure.database.ThreadDatabase;
import org.smssecure.smssecure.mms.AttachmentManager;
import org.smssecure.smssecure.mms.AttachmentManager.MediaType;
import org.smssecure.smssecure.mms.AttachmentTypeSelectorAdapter;
import org.smssecure.smssecure.mms.MediaConstraints;
import org.smssecure.smssecure.mms.MmsMediaConstraints;
import org.smssecure.smssecure.mms.OutgoingMediaMessage;
import org.smssecure.smssecure.mms.OutgoingSecureMediaMessage;
import org.smssecure.smssecure.mms.Slide;
import org.smssecure.smssecure.notifications.MessageNotifier;
import org.smssecure.smssecure.protocol.AutoInitiate;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.RecipientFormattingException;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.recipients.Recipients.RecipientsModifiedListener;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.sms.MessageSender;
import org.smssecure.smssecure.sms.OutgoingEncryptedMessage;
import org.smssecure.smssecure.sms.OutgoingTextMessage;
import org.smssecure.smssecure.util.concurrent.AssertedSuccessListener;
import org.smssecure.smssecure.util.CharacterCalculator.CharacterState;
import org.smssecure.smssecure.util.Dialogs;
import org.smssecure.smssecure.util.DynamicLanguage;
import org.smssecure.smssecure.util.DynamicTheme;
import org.smssecure.smssecure.util.MediaUtil;
import org.smssecure.smssecure.util.SilencePreferences;
import org.smssecure.smssecure.util.views.Stub;
import org.smssecure.smssecure.util.TelephonyUtil;
import org.smssecure.smssecure.util.Util;
import org.smssecure.smssecure.util.ViewUtil;
import org.smssecure.smssecure.util.concurrent.ListenableFuture;
import org.smssecure.smssecure.util.concurrent.SettableFuture;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;

import ws.com.google.android.mms.ContentType;

import static org.smssecure.smssecure.TransportOption.Type;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationActivity extends PassphraseRequiredActionBarActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               RecipientsModifiedListener,
               OnKeyboardShownListener,
               ComposeText.MediaListener
{
  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String RECIPIENTS_EXTRA        = "recipients";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String IS_ARCHIVED_EXTRA       = "is_archived";
  public static final String TEXT_EXTRA              = "draft_text";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";
  public static final String TIMING_EXTRA            = "timing";
  public static final String LAST_SEEN_EXTRA         = "last_seen";

  private static final int PICK_IMAGE        = 1;
  private static final int PICK_VIDEO        = 2;
  private static final int PICK_AUDIO        = 3;
  private static final int PICK_CONTACT_INFO = 4;
  private static final int GROUP_EDIT        = 5;
  private static final int TAKE_PHOTO        = 6;
  private static final int ADD_CONTACT       = 7;

  private   MasterSecret          masterSecret;
  protected ComposeText           composeText;
  private   AnimatingToggle       buttonToggle;
  private   SendButton            sendButton;
  private   ImageButton           attachButton;
  protected ConversationTitleView titleView;
  private   TextView              charactersLeft;
  private   ConversationFragment  fragment;
  private   Button                unblockButton;
  private   InputAwareLayout      container;
  private   View                  composePanel;
  private   View                  composeBubble;

  private   AttachmentTypeSelectorAdapter attachmentAdapter;
  private   AttachmentManager             attachmentManager;
  private   BroadcastReceiver             securityUpdateReceiver;
  private   Stub<EmojiDrawer>             emojiDrawerStub;
  private   EmojiToggle                   emojiToggle;

  private Recipients recipients;
  private long       threadId;
  private int        distributionType;
  private boolean    isEncryptedConversation;
  private boolean    isSecureSmsDestination;
  private boolean    archived;
  private boolean    isMmsEnabled = true;

  private DynamicTheme    dynamicTheme    = new DynamicTheme();
  private DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private List<SubscriptionInfoCompat> activeSubscriptions;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    Log.w(TAG, "onCreate()");
    this.masterSecret = masterSecret;
    this.activeSubscriptions = SubscriptionManagerCompat.from(this).getActiveSubscriptionInfoList();

    supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
    setContentView(R.layout.conversation_activity);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(),
                            masterSecret, dynamicLanguage.getCurrentLocale());

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeSecurity();
    updateRecipientPreferences();
    initializeDraft();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.w(TAG, "onNewIntent()");

    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
      saveDraft();
      attachmentManager.clear();
      composeText.setText("");
    }

    setIntent(intent);
    initializeResources();
    initializeSecurity();
    updateRecipientPreferences();
    initializeDraft();

    if (fragment != null) {
      fragment.onNewIntent();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    initializeEnabledCheck();
    initializeMmsEnabledCheck();
    composeText.setTransport(sendButton.getSelectedTransport());

    titleView.setTitle(recipients);
    setActionBarColor(recipients.getColor());
    setBlockedUserState(recipients);
    calculateCharactersRemaining();

    MessageNotifier.setVisibleThread(threadId);
    markThreadAsRead();

    Log.w(TAG, "onResume() Finished: " + (System.currentTimeMillis() - getIntent().getLongExtra(TIMING_EXTRA, 0)));
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    AudioSlidePlayer.stopAll();
  }

  @Override public void onConfigurationChanged(Configuration newConfig) {
    Log.w(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setTransport(sendButton.getSelectedTransport());

    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (recipients != null) recipients.removeListener(this);
    if (securityUpdateReceiver != null) unregisterReceiver(securityUpdateReceiver);
    super.onDestroy();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null && reqCode != TAKE_PHOTO || resultCode != RESULT_OK) return;

    switch (reqCode) {
    case PICK_IMAGE:
      boolean isGif = MediaUtil.isGif(MediaUtil.getMimeType(this, data.getData()));
      setMedia(data.getData(), isGif ? MediaType.GIF : MediaType.IMAGE);
      break;
    case PICK_VIDEO:
      setMedia(data.getData(), MediaType.VIDEO);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), MediaType.AUDIO);
      break;
    case PICK_CONTACT_INFO:
      addAttachmentContactInfo(data.getData());
      break;
    case TAKE_PHOTO:
      if (attachmentManager.getCaptureUri() != null) {
        setMedia(attachmentManager.getCaptureUri(), MediaType.IMAGE);
      }
      break;
    case ADD_CONTACT:
      recipients = RecipientFactory.getRecipientsForIds(ConversationActivity.this, recipients.getIds(), true);
      recipients.addListener(this);
      fragment.reloadList();
      break;
    }
  }

  @Override
  public void startActivity(Intent intent) {
    try {
      if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
        intent.removeExtra(Browser.EXTRA_APPLICATION_ID);
      }
      super.startActivity(intent);
      Log.d(TAG, "Opened link: " + intent.getDataString());
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "No app found to view the link '" + intent.getDataString() + "', ignoring...");
      Toast.makeText(this, R.string.ConversationActivity_cant_open_link, Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    boolean isEncryptedForAllSubscriptionIdsConversation = SessionUtil.hasSession(this, masterSecret, recipients.getPrimaryRecipient().getNumber(), activeSubscriptions);

    if (isSingleConversation() && isEncryptedConversation) {
      inflater.inflate(R.menu.conversation_secure_identity, menu);
      inflateSubMenuVerifyIdentity(menu);
      inflater.inflate(R.menu.conversation_secure_sms, menu.findItem(R.id.menu_security).getSubMenu());
      inflateSubMenuAbortSecureSession(menu);
    } else if (isSingleConversation() && !isEncryptedConversation) {
      inflater.inflate(R.menu.conversation_insecure_no_push, menu);
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (isSingleConversation() && !isEncryptedForAllSubscriptionIdsConversation) {
      inflateSubMenuStartSecureSession(menu);
    } else {
      MenuItem item = menu.findItem(R.id.menu_start_secure_session);
      if (item != null) item.setVisible(false);
    }

    if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_callable, menu);
    } else if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      }
    }

    inflater.inflate(R.menu.conversation, menu);

    if (recipients != null && recipients.isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                            inflater.inflate(R.menu.conversation_unmuted, menu);

    if (isSingleConversation() && getRecipients().getPrimaryRecipient().getContactUri() == null) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }

    if (archived) menu.findItem(R.id.menu_archive_conversation)
                      .setTitle(R.string.conversation__menu_unarchive_conversation);

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call:                          handleDial(getRecipients().getPrimaryRecipient()); return true;
    case R.id.menu_delete_conversation:           handleDeleteConversation();                        return true;
    case R.id.menu_archive_conversation:          handleArchiveConversation();                       return true;
    case R.id.menu_add_attachment:                handleAddAttachment();                             return true;
    case R.id.menu_view_media:                    handleViewMedia();                                 return true;
    case R.id.menu_add_to_contacts:               handleAddToContacts();                             return true;
    case R.id.menu_start_secure_session:          handleStartSecureSession();                        return true;
    case R.id.menu_start_secure_session_dual_sim: handleStartSecureSession();                        return true;
    case R.id.menu_abort_session:                 handleAbortSecureSession();                        return true;
    case R.id.menu_abort_session_dual_sim:        handleAbortSecureSession();                        return true;
    case R.id.menu_verify_identity:               handleVerifyIdentity();                            return true;
    case R.id.menu_verify_identity_dual_sim:      handleVerifyIdentity();                            return true;
    case R.id.menu_group_recipients:              handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:        handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation:     handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_invite:                        handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:            handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:          handleUnmuteNotifications();                       return true;
    case R.id.menu_conversation_settings:         handleConversationSettings();                      return true;
    case android.R.id.home:                       handleReturnToConversationList();                  return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    Log.w(TAG, "onBackPressed()");
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else                         super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    emojiToggle.setToEmoji();
  }

  private void inflateSubMenuVerifyIdentity(Menu menu) {
    if (Build.VERSION.SDK_INT >= 22 && activeSubscriptions.size() > 1) {
      menu.findItem(R.id.menu_verify_identity).setVisible(false);
      SubMenu identitiesMenu = menu.findItem(R.id.menu_verify_identity_dual_sim).getSubMenu();

      for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
        final int subscriptionId = subscriptionInfo.getSubscriptionId();
        identitiesMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, subscriptionInfo.getDisplayName())
                      .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                          handleVerifyIdentity(subscriptionId);
                          return true;
                        }
                      });
      }
    } else {
      menu.findItem(R.id.menu_verify_identity_dual_sim).setVisible(false);
    }
  }

  private void inflateSubMenuStartSecureSession(Menu menu) {
    if (Build.VERSION.SDK_INT >= 22 && activeSubscriptions.size() > 1) {
      menu.findItem(R.id.menu_start_secure_session).setVisible(false);
      SubMenu startSecureSessionMenu = menu.findItem(R.id.menu_start_secure_session_dual_sim).getSubMenu();

      for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
        final int subscriptionId = subscriptionInfo.getSubscriptionId();

        if (!SessionUtil.hasSession(this, masterSecret, recipients.getPrimaryRecipient().getNumber(), subscriptionId)) {

          startSecureSessionMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, subscriptionInfo.getDisplayName())
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                  @Override
                                  public boolean onMenuItemClick(MenuItem item) {
                                    handleStartSecureSession(subscriptionId);
                                    return true;
                                  }
                                });
        }
      }
    } else {
      menu.findItem(R.id.menu_start_secure_session_dual_sim).setVisible(false);
    }
  }

  private void inflateSubMenuAbortSecureSession(Menu menu) {
    if (Build.VERSION.SDK_INT >= 22 && activeSubscriptions.size() > 1) {
      menu.findItem(R.id.menu_abort_session).setVisible(false);
      SubMenu abortSecureSessionMenu = menu.findItem(R.id.menu_abort_session_dual_sim).getSubMenu();

      for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
        final int subscriptionId = subscriptionInfo.getSubscriptionId();
        Log.w(TAG, "inflateSubMenuAbortSecureSession( " + subscriptionId + " )");

        if (SessionUtil.hasSession(this, masterSecret, recipients.getPrimaryRecipient().getNumber(), subscriptionId)) {
          Log.w(TAG, "Subscription ID " + subscriptionId + " has a secure session.");

          abortSecureSessionMenu.add(Menu.NONE, Menu.NONE, Menu.NONE, subscriptionInfo.getDisplayName())
                                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                  @Override
                                  public boolean onMenuItemClick(MenuItem item) {
                                    handleAbortSecureSession(subscriptionId);
                                    return true;
                                  }
                                });
        }
      }
    } else {
      menu.findItem(R.id.menu_abort_session).setVisible(false);
    }
  }

  //////// Event Handlers

  private void handleReturnToConversationList() {
    Intent intent = new Intent(this, (archived ? ConversationListArchiveActivity.class : ConversationListActivity.class));
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, new MuteDialog.MuteSelectionListener() {
      @Override
      public void onMuted(final long until) {
        recipients.setMuted(until);

        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                           .setMuted(recipients, until);

            return null;
          }
        }.execute();
      }
    });
  }

  private void handleConversationSettings() {
    titleView.performClick();
  }

  private void handleUnmuteNotifications() {
    recipients.setMuted(0);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                       .setMuted(recipients, 0);

        return null;
      }
    }.execute();
  }

  private void handleUnblock() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
        .setMessage(R.string.RecipientPreferenceActivity_are_you_sure_you_want_to_unblock_this_contact)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.RecipientPreferenceActivity_unblock, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            recipients.setBlocked(false);

            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                               .setBlocked(recipients, false);
                return null;
              }
            }.execute();
          }
        }).show();
  }

  private void handleInviteLink() {
    composeText.appendInvite(getString(R.string.ConversationActivity_install_smssecure, "https://silence.im"));
  }

  private void handleVerifyIdentity() {
    if (activeSubscriptions.size() < 2) {
      int subscriptionId = activeSubscriptions.get(0).getSubscriptionId();
      handleVerifyIdentity(subscriptionId);
    }
  }

  private void handleVerifyIdentity(int subscriptionId) {
    Intent verifyIdentityIntent = new Intent(this, VerifyIdentityActivity.class);
    verifyIdentityIntent.putExtra("subscription_id", subscriptionId);
    verifyIdentityIntent.putExtra("recipient", getRecipients().getPrimaryRecipient().getRecipientId());
    startActivity(verifyIdentityIntent);
  }

  private void handleStartSecureSession() {
    if (activeSubscriptions.size() < 2) {
      int subscriptionId = activeSubscriptions.get(0).getSubscriptionId();
      handleStartSecureSession(subscriptionId);
    }
  }

  private void handleStartSecureSession(final int subscriptionId) {
    if (getRecipients() == null) {
      Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    if (TelephonyUtil.isMyPhoneNumber(this, recipients.getPrimaryRecipient().getNumber())) {
      Toast.makeText(this, getString(R.string.ConversationActivity_recipient_self),
              Toast.LENGTH_LONG).show();
      return;
    }

    final Recipients recipients = getRecipients();
    final Recipient recipient   = recipients.getPrimaryRecipient();
    String recipientName        = (recipient.getName() == null ? recipient.getNumber() : recipient.getName());

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_initiate_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setCancelable(true);
    builder.setMessage(String.format(getString(R.string.ConversationActivity_initiate_secure_session_with_s_question),
                       recipientName));
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        KeyExchangeInitiator.initiate(ConversationActivity.this, masterSecret, recipients, true, subscriptionId);
        long allocatedThreadId;
        if (threadId == -1) {
          allocatedThreadId = DatabaseFactory.getThreadDatabase(getApplicationContext()).getThreadIdFor(recipients);
        } else {
          allocatedThreadId = threadId;
        }
        Log.w(TAG, "Refreshing thread "+allocatedThreadId+"...");
        sendComplete(allocatedThreadId);
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleAbortSecureSession() {
    if (activeSubscriptions.size() < 2) {
      int subscriptionId = activeSubscriptions.get(0).getSubscriptionId();
      handleAbortSecureSession(subscriptionId);
    }
  }

  private void handleAbortSecureSession(final int subscriptionId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_abort_secure_session_confirmation);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_are_you_sure_that_you_want_to_abort_this_secure_session_question);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (isSingleConversation()) {
          Recipients recipients = getRecipients();
          KeyExchangeInitiator.abort(ConversationActivity.this, masterSecret, recipients, subscriptionId);

          long allocatedThreadId;
          if (threadId == -1) {
            allocatedThreadId = DatabaseFactory.getThreadDatabase(getApplicationContext()).getThreadIdFor(recipients);
          } else {
            allocatedThreadId = threadId;
          }
          Log.w(TAG, "Refreshing thread "+allocatedThreadId+"...");
          sendComplete(allocatedThreadId);
        }
      }
    });
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleViewMedia() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MediaOverviewActivity.RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
    startActivity(intent);
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.execute();
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.execute();
    }
  }

  private void handleDial(Recipient recipient) {
    try {
      if (recipient == null) return;

      Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                              Uri.parse("tel:" + recipient.getNumber()));
      startActivity(dialIntent);
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, anfe);
      Dialogs.showAlertDialog(this,
                           getString(R.string.ConversationActivity_calls_not_supported),
                           getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
    }
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipients()).display();
  }

  private void handleDeleteConversation() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_delete_thread_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_will_permanently_delete_all_messages_in_this_conversation);
    builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (threadId > 0) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this).deleteConversation(threadId);
        }
        composeText.getText().clear();
        threadId = -1;
        finish();
      }
    });

    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleArchiveConversation() {
    if (threadId > 0) {
      if (!archived) DatabaseFactory.getThreadDatabase(ConversationActivity.this).archiveConversation(threadId);
      else           DatabaseFactory.getThreadDatabase(ConversationActivity.this).unarchiveConversation(threadId);
    }
    composeText.getText().clear();
    threadId = -1;
    finish();
  }

  private void handleAddToContacts() {
    try {
      final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
      intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipients.getPrimaryRecipient().getNumber());
      intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
      startActivityForResult(intent, ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled) {
      new AlertDialog.Builder(this).setAdapter(attachmentAdapter, new AttachmentTypeListener())
                                          .show();
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Intent intent = new Intent(this, PromptMmsActivity.class);
    intent.putExtras(getIntent().getExtras());
    startActivity(intent);
  }

  ///// Initializers

  private void initializeDraft() {
    final String    draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri       draftMedia     = getIntent().getData();
    final MediaType draftMediaType = MediaType.from(getIntent().getType());

    if (draftText != null)                            composeText.setText(draftText);
    if (draftMedia != null && draftMediaType != null) setMedia(draftMedia, draftMediaType);

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
    }
  }

  private void initializeEnabledCheck() {
    boolean enabled = !(isPushGroupConversation() && !isActiveGroup());
    composeText.setEnabled(enabled);
    sendButton.setEnabled(enabled);
  }

  private void initializeDraftFromDatabase() {
    new AsyncTask<Void, Void, List<Draft>>() {
      @Override
      protected List<Draft> doInBackground(Void... params) {
        MasterCipher masterCipher   = new MasterCipher(masterSecret);
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<Draft> results         = draftDatabase.getDrafts(masterCipher, threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<Draft> drafts) {
        for (Draft draft : drafts) {
          if (draft.getType().equals(Draft.TEXT)) {
            composeText.setText(draft.getValue());
          } else if (draft.getType().equals(Draft.IMAGE)) {
            setMedia(Uri.parse(draft.getValue()), MediaType.IMAGE);
          } else if (draft.getType().equals(Draft.AUDIO)) {
            setMedia(Uri.parse(draft.getValue()), MediaType.AUDIO);
          } else if (draft.getType().equals(Draft.VIDEO)) {
            setMedia(Uri.parse(draft.getValue()), MediaType.VIDEO);
          }
        }

        updateToggleButtonState();
      }
    }.execute();
  }

  private void initializeSecurity() {
    Recipient    primaryRecipient = getRecipients() == null ? null : getRecipients().getPrimaryRecipient();
    boolean      isMediaMessage   = !recipients.isSingleRecipient() || attachmentManager.isAttachmentPresent();

    isSecureSmsDestination = isSingleConversation() && SessionUtil.hasAtLeastOneSession(this, masterSecret, primaryRecipient.getNumber(), activeSubscriptions);

    if (isSecureSmsDestination) {
      this.isEncryptedConversation = true;
    } else {
      this.isEncryptedConversation = false;
    }

    sendButton.resetAvailableTransports(isMediaMessage);
    if (!isSecureSmsDestination      ) sendButton.disableTransport(Type.SECURE_SMS);
    if (recipients.isGroupRecipient()) sendButton.disableTransport(Type.INSECURE_SMS);

    if (Build.VERSION.SDK_INT >= 22) {
      sendButton.disableTransport(Type.SECURE_SMS, SessionUtil.getSubscriptionIdWithoutSession(this, masterSecret, primaryRecipient.getNumber(), activeSubscriptions));
    }

    if (isSecureSmsDestination) {
      sendButton.setDefaultTransport(Type.SECURE_SMS);
    } else {
      sendButton.setDefaultTransport(Type.INSECURE_SMS);
    }

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
  }

  private void updateRecipientPreferences() {
    if (recipients.getPrimaryRecipient() != null &&
        recipients.getPrimaryRecipient().getContactUri() != null)
    {
      new RecipientPreferencesTask().execute(recipients);
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.w(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orNull() + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId);
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return Util.isMmsCapable(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.execute();
  }

  private void initializeViews() {
    titleView       = (ConversationTitleView) getSupportActionBar().getCustomView();
    buttonToggle    = ViewUtil.findById(this, R.id.button_toggle);
    sendButton      = ViewUtil.findById(this, R.id.send_button);
    attachButton    = ViewUtil.findById(this, R.id.attach_button);
    composeText     = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft  = ViewUtil.findById(this, R.id.space_left);
    emojiToggle     = ViewUtil.findById(this, R.id.emoji_toggle);
    emojiDrawerStub = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    unblockButton   = ViewUtil.findById(this, R.id.unblock_button);
    composePanel    = ViewUtil.findById(this, R.id.bottom_panel);
    composeBubble   = ViewUtil.findById(this, R.id.compose_bubble);
    container       = ViewUtil.findById(this, R.id.layout_container);

    if (SilencePreferences.isEmojiDrawerDisabled(this))
      emojiToggle.setVisibility(View.GONE);

    container.addOnKeyboardShownListener(this);
    composeText.setMediaListener(this);

    int[]      attributes   = new int[]{R.attr.conversation_item_bubble_background};
    TypedArray colors       = obtainStyledAttributes(attributes);
    int        defaultColor = colors.getColor(0, Color.WHITE);
    composeBubble.getBackground().setColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY);
    colors.recycle();

    attachmentAdapter = new AttachmentTypeSelectorAdapter(this);
    attachmentManager = new AttachmentManager(this, this);

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    emojiToggle.attach(emojiDrawerStub.get());
    emojiToggle.setOnClickListener(new EmojiToggleListener());
    emojiDrawerStub.get().setEmojiEventListener(new EmojiEventListener() {
      @Override public void onKeyEvent(KeyEvent keyEvent) {
        composeText.dispatchKeyEvent(keyEvent);
      }

      @Override public void onEmojiSelected(String emoji) {
        composeText.insertEmoji(emoji);
      }
    });

    composeText.setOnEditorActionListener(sendButtonListener);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnTransportChangedListener(new OnTransportChangedListener() {
      @Override
      public void onChange(TransportOption newTransport, boolean manuallySelected) {
        calculateCharactersRemaining();
        composeText.setTransport(newTransport);
        buttonToggle.getBackground().setColorFilter(newTransport.getBackgroundColor(), Mode.MULTIPLY);
        buttonToggle.getBackground().invalidateSelf();
        if (manuallySelected) recordSubscriptionIdPreference(newTransport.getSimSubscriptionId());
      }
    });

    titleView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(ConversationActivity.this, RecipientPreferenceActivity.class);
        intent.putExtra(RecipientPreferenceActivity.RECIPIENTS_EXTRA, recipients.getIds());

        startActivitySceneTransition(intent, titleView.findViewById(R.id.title), "recipient_name");
      }
    });

    unblockButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        handleUnblock();
      }
    });

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);
  }

  protected void initializeActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setCustomView(R.layout.conversation_title_view);
    getSupportActionBar().setDisplayShowCustomEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipients != null) recipients.removeListener(this);

    recipients       = RecipientFactory.getRecipientsForIds(this, getIntent().getLongArrayExtra(RECIPIENTS_EXTRA), true);
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    archived         = getIntent().getBooleanExtra(IS_ARCHIVED_EXTRA, false);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
      LinearLayout conversationContainer = ViewUtil.findById(this, R.id.conversation_container);
      conversationContainer.setClipChildren(true);
      conversationContainer.setClipToPadding(true);
    }

    recipients.addListener(this);
  }

  @Override
  public void onModified(final Recipients recipients) {
    titleView.post(new Runnable() {
      @Override
      public void run() {
        titleView.setTitle(recipients);
        setBlockedUserState(recipients);
        setActionBarColor(recipients.getColor());
        updateRecipientPreferences();
      }
    });
  }

  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        long eventThreadId = intent.getLongExtra("thread_id", -1);

        if (eventThreadId == threadId || eventThreadId == -2) {
          initializeSecurity();
          updateRecipientPreferences();
          calculateCharactersRemaining();
        }
      }
    };

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);
  }

  //////// Helper Methods

  private void addAttachment(int type) {
    Log.w("ComposeMessageActivity", "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelectorAdapter.ADD_IMAGE:
      AttachmentManager.selectImage(this, PICK_IMAGE); break;
    case AttachmentTypeSelectorAdapter.ADD_VIDEO:
      AttachmentManager.selectVideo(this, PICK_VIDEO); break;
    case AttachmentTypeSelectorAdapter.ADD_SOUND:
      AttachmentManager.selectAudio(this, PICK_AUDIO); break;
    case AttachmentTypeSelectorAdapter.ADD_CONTACT_INFO:
      AttachmentManager.selectContactInfo(this, PICK_CONTACT_INFO); break;
    case AttachmentTypeSelectorAdapter.TAKE_PHOTO:
      attachmentManager.capturePhoto(this, TAKE_PHOTO); break;
    }
  }

  private void setMedia(@Nullable Uri uri, @NonNull MediaType mediaType) {
    if (uri == null) return;
    attachmentManager.setMedia(masterSecret, uri, mediaType, getCurrentMediaConstraints());
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        composeText.append(numbers[which]);
      }
    });
    builder.show();
  }

  private Drafts getDraftsForCurrentState() {
    Drafts drafts = new Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getText().toString()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio()) drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo()) drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasImage()) drafts.add(new Draft(Draft.IMAGE, slide.getUri().toString()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipients == null || this.recipients.isEmpty()) {
      future.set(threadId);
      return future;
    }

    final Drafts       drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final MasterSecret thisMasterSecret     = this.masterSecret.parcelClone();
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipients(), thisDistributionType);

          draftDatabase.insertDrafts(new MasterCipher(thisMasterSecret), threadId, drafts);
          threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this),
                                       drafts.getUriSnippet(ConversationActivity.this),
                                       System.currentTimeMillis(), Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.execute(thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Window window = getWindow();
      window.setStatusBarColor(color.toStatusBarColor(this));
      window.setNavigationBarColor(getResources().getColor(android.R.color.black));
    }
  }

  private void setBlockedUserState(Recipients recipients) {
    if (recipients.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      composePanel.setVisibility(View.GONE);
    } else {
      composePanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    String          messageBody     = composeText.getText().toString();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(characterState.charactersRemaining + "/" + characterState.maxMessageSize
                                 + " (" + characterState.messagesSpent + ")");
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private boolean isSingleConversation() {
    return getRecipients() != null && getRecipients().isSingleRecipient() && !getRecipients().isGroupRecipient();
  }

  private boolean isActiveGroup() {
    return false;
  }

  private boolean isGroupConversation() {
    return getRecipients() != null &&
        (!getRecipients().isSingleRecipient() || getRecipients().isGroupRecipient());
  }

  private boolean isPushGroupConversation() {
    return getRecipients() != null && getRecipients().isGroupRecipient();
  }

  protected Recipients getRecipients() {
    return this.recipients;
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getText().toString();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    if (!isEncryptedConversation &&
        AutoInitiate.isTaggableMessage(rawText) &&
        AutoInitiate.isTaggableDestination(getRecipients())) {
      rawText = AutoInitiate.getTaggedMessage(rawText);
    }

    return rawText;
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return MediaConstraints.MMS_CONSTRAINTS;
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setRead(params[0]);
        MessageNotifier.updateNotification(ConversationActivity.this, masterSecret);
        return null;
      }
    }.execute(threadId);
  }

  private void markLastSeen() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setLastSeen(params[0]);
        return null;
      }
    }.execute(threadId);
  }

  protected void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    fragment.setLastSeen(0);

    if (refreshFragment) {
      fragment.reload(recipients, threadId);

      initializeSecurity();
      updateRecipientPreferences();
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();
  }

  private void sendMessage() {
    try {
      Recipients recipients = getRecipients();

      if (recipients == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      boolean    forcePlaintext = sendButton.getSelectedTransport().isPlaintext();
      int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);

      Log.w(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.w(TAG, "forcePlaintext: " + forcePlaintext);

      if ((!recipients.isSingleRecipient() || recipients.isEmailRecipient()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (attachmentManager.isAttachmentPresent() || !recipients.isSingleRecipient() || recipients.isGroupRecipient() || recipients.isEmailRecipient()) {
        sendMediaMessage(forcePlaintext, subscriptionId);
      } else {
        sendTextMessage(forcePlaintext, subscriptionId);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(final boolean forcePlaintext, final int subscriptionId)
      throws InvalidMessageException
  {
    final Context context                = getApplicationContext();
    OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(recipients,
                                                                    attachmentManager.buildSlideDeck(),
                                                                    getMessage(),
                                                                    System.currentTimeMillis(),
                                                                    subscriptionId,
                                                                    distributionType);

    if (isEncryptedConversation && !forcePlaintext) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
    }

    attachmentManager.clear();
    composeText.setText("");

    new AsyncTask<OutgoingMediaMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingMediaMessage... messages) {
        return MessageSender.send(context, masterSecret, messages[0], threadId, true);
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
      }
    }.execute(outgoingMessage);
  }

  private void sendTextMessage(boolean forcePlaintext, final int subscriptionId)
      throws InvalidMessageException
  {
    final Context context = getApplicationContext();
    OutgoingTextMessage message;

    if (isEncryptedConversation && !forcePlaintext) {
      message = new OutgoingEncryptedMessage(recipients, getMessage(), subscriptionId);
    } else {
      message = new OutgoingTextMessage(recipients, getMessage(), subscriptionId);
    }

    this.composeText.setText("");

    new AsyncTask<OutgoingTextMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingTextMessage... messages) {
        return MessageSender.send(context, masterSecret, messages[0], threadId, true);
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
      }
    }.execute(message);
  }

  private void updateToggleButtonState() {
    if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
    } else {
      buttonToggle.display(sendButton);
    }
  }

  private void recordSubscriptionIdPreference(final Optional<Integer> subscriptionId) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                       .setDefaultSubscriptionId(recipients, subscriptionId.or(-1));
        return null;
      }
    }.execute();
  }

  // Listeners

  private class AttachmentTypeListener implements DialogInterface.OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      addAttachment(attachmentAdapter.buttonToCommand(which));
      dialog.dismiss();
    }
  }

  private class EmojiToggleListener implements OnClickListener {

    @Override public void onClick(View v) {
      if (container.getCurrentInput() == emojiDrawerStub.get()) {
        container.showSoftkey(composeText);
      } else {
        container.show(composeText, emojiDrawerStub.get());
      }
    }
  }

  @Override
  public void onMediaSelected(@NonNull Uri uri, String contentType) {
    if (!TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif")) {
      setMedia(uri, MediaType.GIF);
    } else if (ContentType.isImageType(contentType)) {
      setMedia(uri, MediaType.IMAGE);
    } else if (ContentType.isVideoType(contentType)) {
      setMedia(uri, MediaType.VIDEO);
    } else if (ContentType.isAudioType(contentType)) {
      setMedia(uri, MediaType.AUDIO);
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (SilencePreferences.getEnterKeyType(ConversationActivity.this).equals("send")) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getText().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();

      if (composeText.getText().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(new Runnable() {
          @Override
          public void run() {
            updateToggleButtonState();
          }
        }, 50);
      }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void onAttachmentChanged() {
    initializeSecurity();
    updateRecipientPreferences();
    updateToggleButtonState();
  }

  private class RecipientPreferencesTask extends AsyncTask<Recipients, Void, Pair<Recipients,RecipientsPreferences>> {
    @Override
    protected Pair<Recipients, RecipientsPreferences> doInBackground(Recipients... recipients) {
      if (recipients.length != 1 || recipients[0] == null) {
        throw new AssertionError("task needs exactly one Recipients object");
      }

      Optional<RecipientsPreferences> prefs = DatabaseFactory.getRecipientPreferenceDatabase(ConversationActivity.this)
                                                             .getRecipientsPreferences(recipients[0].getIds());
      return new Pair<>(recipients[0], prefs.orNull());
    }

    @Override
    protected void onPostExecute(@NonNull  Pair<Recipients, RecipientsPreferences> result) {
      if (result.first == recipients) {
        updateDefaultSubscriptionId(result.second != null ? result.second.getDefaultSubscriptionId() : SubscriptionManagerCompat.getDefaultMessagingSubscriptionId());
      }
    }
  }
}
