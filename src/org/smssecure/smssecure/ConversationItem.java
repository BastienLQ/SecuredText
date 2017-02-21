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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.smssecure.smssecure.components.AudioView;
import org.smssecure.smssecure.components.AvatarImageView;
import org.smssecure.smssecure.components.DeliveryStatusView;
import org.smssecure.smssecure.components.AlertView;
import org.smssecure.smssecure.components.ThumbnailView;
import org.smssecure.smssecure.crypto.KeyExchangeInitiator;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.AttachmentDatabase;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.MmsDatabase;
import org.smssecure.smssecure.database.MmsSmsDatabase;
import org.smssecure.smssecure.database.SmsDatabase;
import org.smssecure.smssecure.database.model.MediaMmsMessageRecord;
import org.smssecure.smssecure.database.model.MessageRecord;
import org.smssecure.smssecure.database.model.NotificationMmsMessageRecord;
import org.smssecure.smssecure.jobs.MmsDownloadJob;
import org.smssecure.smssecure.jobs.MmsSendJob;
import org.smssecure.smssecure.jobs.SmsSendJob;
import org.smssecure.smssecure.mms.PartAuthority;
import org.smssecure.smssecure.mms.Slide;
import org.smssecure.smssecure.mms.SlideClickListener;
import org.smssecure.smssecure.protocol.AutoInitiate;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.util.DateUtils;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;
import org.smssecure.smssecure.util.views.Stub;
import org.smssecure.smssecure.util.DynamicTheme;
import org.smssecure.smssecure.util.TelephonyUtil;
import org.smssecure.smssecure.util.Util;
import org.smssecure.smssecure.util.SilencePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout
    implements Recipient.RecipientModifiedListener, Recipients.RecipientsModifiedListener, BindableConversationItem
{
  private final static String TAG = ConversationItem.class.getSimpleName();

  private MessageRecord messageRecord;
  private MasterSecret  masterSecret;
  private Locale        locale;
  private boolean       groupThread;
  private Recipient     recipient;

  protected View             bodyBubble;
  private TextView           bodyText;
  private TextView           dateText;
  private TextView           simInfoText;
  private TextView           indicatorText;
  private TextView           groupStatusText;
  private ImageView          secureImage;
  private AvatarImageView    contactPhoto;
  private DeliveryStatusView deliveryStatusIndicator;
  private AlertView          alertView;

  private @NonNull  Set<MessageRecord>  batchSelected = new HashSet<>();
  private @Nullable Recipients          conversationRecipients;
  private @NonNull  ThumbnailView       mediaThumbnail;
  private @NonNull  Stub<AudioView>     audioViewStub;
  private @NonNull  Button              mmsDownloadButton;
  private @NonNull  TextView            mmsDownloadingLabel;

  private int defaultBubbleColor;

  private final MmsDownloadClickListener        mmsDownloadClickListener    = new MmsDownloadClickListener();
  private final MmsPreferencesClickListener     mmsPreferencesClickListener = new MmsPreferencesClickListener();
  private final PassthroughClickListener        passthroughClickListener    = new PassthroughClickListener();
  private final AttachmentDownloadClickListener downloadClickListener       = new AttachmentDownloadClickListener();

  private final Context context;

  private static final Pattern XMPP_PATTERN    = Pattern.compile("xmpp:[^ \t\n\"\':,<>]+",              Pattern.CASE_INSENSITIVE);
  private static final Pattern GEO_URI_PATTERN = Pattern.compile("geo:[-0-9.]+,[-0-9.]+[^ \t\n\"\':]*", Pattern.CASE_INSENSITIVE);

  private static final Linkify.TransformFilter WEBURL_TRANSFORM = new Linkify.TransformFilter() {
    @Override
    public String transformUrl(Matcher matcher, String url) {
      if (url == null) {
        return null;
      }

      String[] split = url.split(":", 2);
      if (split.length == 2){
        return split[0].toLowerCase() + ":" + split[1];
      }
      else{
        return "http://" + url;
      }
    }
  };

  public ConversationItem(Context context) {
    this(context, null);
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  public void setOnClickListener(OnClickListener l) {
    super.setOnClickListener(new ClickListener(l));
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    initializeAttributes();

    this.bodyText                = (TextView)           findViewById(R.id.conversation_item_body);
    this.dateText                = (TextView)           findViewById(R.id.conversation_item_date);
    this.simInfoText             = (TextView)           findViewById(R.id.sim_info);
    this.indicatorText           = (TextView)           findViewById(R.id.indicator_text);
    this.groupStatusText         = (TextView)           findViewById(R.id.group_message_status);
    this.secureImage             = (ImageView)          findViewById(R.id.secure_indicator);
    this.deliveryStatusIndicator = (DeliveryStatusView) findViewById(R.id.delivery_status);
    this.alertView               = (AlertView)          findViewById(R.id.indicators_parent);
    this.mmsDownloadButton       = (Button)             findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel     = (TextView)           findViewById(R.id.mms_label_downloading);
    this.contactPhoto            = (AvatarImageView)    findViewById(R.id.contact_photo);
    this.bodyBubble              =                      findViewById(R.id.body_bubble);
    this.mediaThumbnail          = (ThumbnailView)      findViewById(R.id.image_view);
    this.audioViewStub           = new Stub<>((ViewStub) findViewById(R.id.audio_view_stub));


    setOnClickListener(new ClickListener(null));

    mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
    mediaThumbnail.setThumbnailClickListener(new ThumbnailClickListener());
    mediaThumbnail.setDownloadClickListener(downloadClickListener);
    mediaThumbnail.setOnLongClickListener(passthroughClickListener);
    mediaThumbnail.setOnClickListener(passthroughClickListener);
    bodyText.setOnLongClickListener(passthroughClickListener);
    bodyText.setOnClickListener(passthroughClickListener);
  }

  @Override
  public void bind(@NonNull MasterSecret       masterSecret,
                   @NonNull MessageRecord      messageRecord,
                   @NonNull Locale             locale,
                   @NonNull Set<MessageRecord> batchSelected,
                   @NonNull Recipients         conversationRecipients)
  {
    this.masterSecret           = masterSecret;
    this.messageRecord          = messageRecord;
    this.locale                 = locale;
    this.batchSelected          = batchSelected;
    this.conversationRecipients = conversationRecipients;
    this.groupThread            = !conversationRecipients.isSingleRecipient() || conversationRecipients.isGroupRecipient();
    this.recipient              = messageRecord.getIndividualRecipient();

    this.recipient.addListener(this);
    this.conversationRecipients.addListener(this);

    setInteractionState(messageRecord);
    setBodyText(messageRecord);
    setBubbleState(messageRecord, recipient);
    setStatusIcons(messageRecord);
    setContactPhoto(recipient);
    setGroupMessageStatus(messageRecord, recipient);
    checkForAutoInitiate(messageRecord);
    setMinimumWidth();
    setMediaAttributes(messageRecord);
    setSimInfo(messageRecord);
  }

  private void initializeAttributes() {
    final int[]      attributes = new int[] {R.attr.conversation_item_bubble_background,
                                             R.attr.conversation_list_item_background_selected,
                                             R.attr.conversation_item_background};
    final TypedArray attrs      = context.obtainStyledAttributes(attributes);

    defaultBubbleColor = attrs.getColor(0, Color.WHITE);
    attrs.recycle();
  }

  @Override
  public void unbind() {
    if (recipient != null) {
      recipient.removeListener(this);
    }
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  /// MessageRecord Attribute Parsers

  private void setBubbleState(MessageRecord messageRecord, Recipient recipient) {
    if (messageRecord.isOutgoing()) {
      bodyBubble.getBackground().setColorFilter(defaultBubbleColor, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(defaultBubbleColor);
    } else {
      int color = recipient.getColor().toConversationColor(context);
      bodyBubble.getBackground().setColorFilter(color, PorterDuff.Mode.MULTIPLY);
      mediaThumbnail.setBackgroundColorHint(color);
    }


    if (audioViewStub.resolved()) {
      setAudioViewTint(messageRecord, conversationRecipients);
    }
  }

  private void setAudioViewTint(MessageRecord messageRecord, Recipients recipients) {
    if (messageRecord.isOutgoing()) {
      if (DynamicTheme.LIGHT.equals(SilencePreferences.getTheme(context))) {
        audioViewStub.get().setTint(recipients.getColor().toConversationColor(context), defaultBubbleColor);
      } else {
        audioViewStub.get().setTint(Color.WHITE, defaultBubbleColor);
      }
    } else {
      audioViewStub.get().setTint(Color.WHITE, recipients.getColor().toConversationColor(context));
    }
  }

  private void setInteractionState(MessageRecord messageRecord) {
    setSelected(batchSelected.contains(messageRecord));
    mediaThumbnail.setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
    mediaThumbnail.setClickable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
    mediaThumbnail.setLongClickable(batchSelected.isEmpty());

    if (audioViewStub.resolved()) {
      audioViewStub.get().setFocusable(!shouldInterceptClicks(messageRecord) && batchSelected.isEmpty());
      audioViewStub.get().setClickable(batchSelected.isEmpty());
      audioViewStub.get().setEnabled(batchSelected.isEmpty());
    }
  }

  private boolean isCaptionlessMms(MessageRecord messageRecord) {
    return TextUtils.isEmpty(messageRecord.getDisplayBody()) && messageRecord.isMms();
  }

  private boolean hasAudio(MessageRecord messageRecord) {
    return messageRecord.isMms() &&
           !messageRecord.isMmsNotification() &&
           ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getAudioSlide() != null;
  }

  private boolean hasThumbnail(MessageRecord messageRecord) {
    return messageRecord.isMms()              &&
           !messageRecord.isMmsNotification() &&
           ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide() != null;
  }

  private void setBodyText(MessageRecord messageRecord) {
    bodyText.setClickable(false);
    bodyText.setFocusable(false);

    if (isCaptionlessMms(messageRecord)) {
      bodyText.setVisibility(View.GONE);
    } else {
      bodyText.setText(messageRecord.getDisplayBody());
      bodyText.setVisibility(View.VISIBLE);
      linkifyBodyText();
    }
  }

  private void linkifyBodyText() {
    if (batchSelected.isEmpty()) {
      Linkify.addLinks(bodyText, XMPP_PATTERN, "xmpp:");
      Linkify.addLinks(bodyText, GEO_URI_PATTERN, "geo:");

      /*
       * Linkify.addLinks(bodyText, Linkify.ALL) conflicts with custom patterns, so
       * we recreate patterns by hand.
       */
      Linkify.addLinks(bodyText, Patterns.WEB_URL, null, Linkify.sUrlMatchFilter, WEBURL_TRANSFORM);
      Linkify.addLinks(bodyText, Patterns.EMAIL_ADDRESS, "mailto:");
      Linkify.addLinks(bodyText, Patterns.PHONE, "tel:");
    } else {
      Log.w(TAG, "batchSelected is not empty!");
      bodyText.setAutoLinkMask(0);
    }
  }

  private void setMediaAttributes(MessageRecord messageRecord) {
    boolean showControls = !messageRecord.isFailed() && (!messageRecord.isOutgoing() || messageRecord.isPending());

    if (messageRecord.isMmsNotification()) {
      mediaThumbnail.setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);

      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
      setNotificationMmsAttributes((NotificationMmsMessageRecord) messageRecord);
    } else if (hasAudio(messageRecord)) {
      audioViewStub.get().setVisibility(View.VISIBLE);
      mediaThumbnail.setVisibility(View.GONE);

      //noinspection ConstantConditions
      audioViewStub.get().setAudio(masterSecret, ((MediaMmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide(), showControls);
      audioViewStub.get().setDownloadClickListener(downloadClickListener);
      audioViewStub.get().setOnLongClickListener(passthroughClickListener);

      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    } else if (hasThumbnail(messageRecord)) {
      mediaThumbnail.setVisibility(View.VISIBLE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);

      //noinspection ConstantConditions
      mediaThumbnail.setImageResource(masterSecret,
                                      ((MediaMmsMessageRecord)messageRecord).getSlideDeck().getThumbnailSlide(),
                                      showControls);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    } else {
      mediaThumbnail.setVisibility(View.GONE);
      if (audioViewStub.resolved()) audioViewStub.get().setVisibility(View.GONE);
      bodyText.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
  }

  private void setContactPhoto(Recipient recipient) {
    if (! messageRecord.isOutgoing()) {
      setContactPhotoForRecipient(recipient);
    }
  }

  private void setStatusIcons(MessageRecord messageRecord) {
    mmsDownloadButton.setVisibility(View.GONE);
    mmsDownloadingLabel.setVisibility(View.GONE);
    indicatorText.setVisibility(View.GONE);

    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);

    dateText.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getTimestamp()));

    if (messageRecord.isFailed()) {
      setFailedStatusIcons();
    } else {
      alertView.setNone();

      if      (!messageRecord.isOutgoing()) deliveryStatusIndicator.setNone();
      else if (messageRecord.isPending())   deliveryStatusIndicator.setPending();
      else if (messageRecord.isDelivered()) deliveryStatusIndicator.setDelivered();
      else                                  deliveryStatusIndicator.setSent();
    }
  }

  private void setSimInfo(MessageRecord messageRecord) {
    SubscriptionManagerCompat subscriptionManager = SubscriptionManagerCompat.from(context);

    if (messageRecord.getSubscriptionId() == -1 || subscriptionManager.getActiveSubscriptionInfoList().size() < 2) {
      simInfoText.setVisibility(View.GONE);
    } else {
      Optional<SubscriptionInfoCompat> subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(messageRecord.getSubscriptionId());

      if (subscriptionInfo.isPresent() && messageRecord.isOutgoing()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_from_s, subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else if (subscriptionInfo.isPresent()) {
        simInfoText.setText(getContext().getString(R.string.ConversationItem_to_s,  subscriptionInfo.get().getDisplayName()));
        simInfoText.setVisibility(View.VISIBLE);
      } else {
        simInfoText.setVisibility(View.GONE);
      }
    }
  }

  private void setFailedStatusIcons() {
    alertView.setFailed();
    deliveryStatusIndicator.setNone();
    dateText.setText(R.string.ConversationItem_error_not_delivered);

    if (messageRecord.isOutgoing()) {
      indicatorText.setText(R.string.ConversationItem_click_for_details);
      indicatorText.setVisibility(View.VISIBLE);
    }
  }

  private void setMinimumWidth() {
    if (indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
      final float density = getResources().getDisplayMetrics().density;
      bodyBubble.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density) + (int) (22.0 * density));
    } else {
      bodyBubble.setMinimumWidth(0);
    }
  }

  private boolean shouldInterceptClicks(MessageRecord messageRecord) {
    return batchSelected.isEmpty() &&
           ((messageRecord.isFailed() && !messageRecord.isMmsNotification()) ||
           shouldInterceptKeyExchangeMessage(messageRecord));
  }

  private boolean shouldInterceptKeyExchangeMessage(MessageRecord keyExchangeMessage) {
    return keyExchangeMessage.isKeyExchange()          &&
          !keyExchangeMessage.isProcessedKeyExchange() &&
          !keyExchangeMessage.isOutgoing();
  }

  private void setGroupMessageStatus(MessageRecord messageRecord, Recipient recipient) {
    if (groupThread && !messageRecord.isOutgoing()) {
      this.groupStatusText.setText(recipient.toShortString());
      this.groupStatusText.setVisibility(View.VISIBLE);
    } else {
      this.groupStatusText.setVisibility(View.GONE);
    }
  }

  private void setNotificationMmsAttributes(NotificationMmsMessageRecord messageRecord) {
    String messageSize = String.format(context.getString(R.string.ConversationItem_message_size_d_kb),
                                       messageRecord.getMessageSize());
    String expires     = String.format(context.getString(R.string.ConversationItem_expires_s),
                                       DateUtils.getRelativeTimeSpanString(getContext(),
                                                                           messageRecord.getExpiration(),
                                                                           false));

    dateText.setText(messageSize + "\n" + expires);

    if (MmsDatabase.Status.isDisplayDownloadButton(context, messageRecord.getStatus())) {
      mmsDownloadButton.setVisibility(View.VISIBLE);
      mmsDownloadingLabel.setVisibility(View.GONE);
    } else {
      mmsDownloadingLabel.setText(MmsDatabase.Status.getLabelForStatus(context, messageRecord.getStatus()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);

      if (MmsDatabase.Status.isHardError(messageRecord.getStatus()) && !messageRecord.isOutgoing())
        setOnClickListener(mmsDownloadClickListener);
      else if (MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE == messageRecord.getStatus() && !messageRecord.isOutgoing())
        setOnClickListener(mmsPreferencesClickListener);
    }
  }

  /// Helper Methods

  private void checkForAutoInitiate(MessageRecord messageRecord) {
    if (!messageRecord.isOutgoing()                       &&
        messageRecord.getRecipients().isSingleRecipient() &&
        !messageRecord.isSecure())
    {
      final Recipients recipients = messageRecord.getRecipients();
      final int subscriptionId    = messageRecord.getSubscriptionId();

      Recipient recipient = recipients.getPrimaryRecipient();
      String    body      = messageRecord.getBody().getBody();
      long      threadId  = messageRecord.getThreadId();


      if (!groupThread &&
          !TelephonyUtil.isMyPhoneNumber(context, recipient.getNumber()) &&
          AutoInitiate.isValidAutoInitiateSituation(context, masterSecret, recipient, body, threadId))
      {
        AutoInitiate.exemptThread(context, threadId);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.ConversationActivity_initiate_secure_session_question);
        builder.setMessage(R.string.ConversationActivity_detected_silence_initiate_session_question);
        builder.setIconAttribute(R.attr.dialog_info_icon);
        builder.setCancelable(true);
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            KeyExchangeInitiator.initiate(context, masterSecret, recipients, true);
          }
        });
        builder.show();
      }
    }
  }

  private void setContactPhotoForRecipient(final Recipient recipient) {
    if (contactPhoto == null) return;

    contactPhoto.setAvatar(recipient, true);
    contactPhoto.setVisibility(View.VISIBLE);
  }

  /// Event handlers

  private void handleKeyExchangeClicked() {
    new ReceiveKeyDialog(context, masterSecret, messageRecord).show();
  }

  private void handleLegacyKeyExchangeClicked() {
    KeyExchangeInitiator.initiate(context, masterSecret, RecipientFactory.getRecipientsFor(context, recipient, false), false, messageRecord.getSubscriptionId());
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(context);
    smsDatabase.markAsProcessedKeyExchange(messageRecord.getId());
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setBubbleState(messageRecord, recipient);
        setContactPhoto(recipient);
        setGroupMessageStatus(messageRecord, recipient);
      }
    });
  }

  @Override
  public void onModified(Recipients recipient) {
    onModified(recipient.getPrimaryRecipient());
  }

  private class AttachmentDownloadClickListener implements SlideClickListener {
    @Override public void onClick(View v, final Slide slide) {
      DatabaseFactory.getAttachmentDatabase(context).setTransferState(messageRecord.getId(),
                                                                      slide.asAttachment(),
                                                                      AttachmentDatabase.TRANSFER_PROGRESS_STARTED);
    }
  }

  private class ThumbnailClickListener implements SlideClickListener {
    private void fireIntent(Slide slide) {
      Log.w(TAG, "Clicked: " + slide.getUri() + " , " + slide.getContentType());
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(PartAuthority.getAttachmentPublicUri(slide.getUri()), slide.getContentType());
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, "No activity existed to view the media.");
        Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
      }
    }

    public void onClick(final View v, final Slide slide) {
      if (shouldInterceptClicks(messageRecord) || !batchSelected.isEmpty()) {
        performClick();
      } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
        Intent intent = new Intent(context, MediaPreviewActivity.class);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(slide.getUri(), slide.getContentType());
        if (!messageRecord.isOutgoing()) intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, recipient.getRecipientId());
        intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getTimestamp());
        intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, slide.asAttachment().getSize());
        intent.putExtra(MediaPreviewActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());

        context.startActivity(intent);
      } else {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.ConversationItem_view_secure_media_question);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationItem_this_media_has_been_stored_in_an_encrypted_database_external_viewer_warning);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            fireIntent(slide);
          }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
      }
    }
  }

  private class MmsDownloadClickListener implements View.OnClickListener {
    public void onClick(View v) {
      NotificationMmsMessageRecord notificationRecord = (NotificationMmsMessageRecord)messageRecord;
      Log.w(TAG, "Content location: " + new String(notificationRecord.getContentLocation()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MmsDownloadJob(context, messageRecord.getId(),
                                                messageRecord.getThreadId(), false));
    }
  }

  private class MmsPreferencesClickListener implements View.OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(context, PromptMmsActivity.class);
      intent.putExtra("message_id", messageRecord.getId());
      intent.putExtra("thread_id", messageRecord.getThreadId());
      intent.putExtra("automatic", true);
      context.startActivity(intent);
    }
  }

  private class PassthroughClickListener implements View.OnLongClickListener, View.OnClickListener {

    @Override
    public boolean onLongClick(View v) {
      performLongClick();
      return true;
    }

    @Override
    public void onClick(View v) {
      performClick();
    }
  }
  private class ClickListener implements View.OnClickListener {
    private OnClickListener parent;

    public ClickListener(@Nullable OnClickListener parent) {
      this.parent = parent;
    }

    public void onClick(View v) {
      if (!shouldInterceptClicks(messageRecord) && parent != null) {
        parent.onClick(v);
      } else if (messageRecord.isFailed()) {
        Intent intent = new Intent(context, MessageDetailsActivity.class);
        intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, messageRecord.getId());
        intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, messageRecord.getThreadId());
        intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, messageRecord.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
        intent.putExtra(MessageDetailsActivity.RECIPIENTS_IDS_EXTRA, conversationRecipients.getIds());
        context.startActivity(intent);
      } else if (messageRecord.isKeyExchange()           &&
                 !messageRecord.isOutgoing()             &&
                 !messageRecord.isProcessedKeyExchange() &&
                 !messageRecord.isStaleKeyExchange()     &&
                 !messageRecord.isLegacyMessage())
      {
        handleKeyExchangeClicked();
      } else if (shouldInterceptKeyExchangeMessage(messageRecord)) {
        handleLegacyKeyExchangeClicked();
      }
    }
  }

}
