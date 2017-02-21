package org.smssecure.smssecure.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.color.MaterialColor;
import org.smssecure.smssecure.contacts.avatars.ContactColors;
import org.smssecure.smssecure.contacts.avatars.ContactPhotoFactory;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.SessionUtil;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.util.dualsim.SubscriptionInfoCompat;
import org.smssecure.smssecure.util.dualsim.SubscriptionManagerCompat;

import java.util.List;

public class AvatarImageView extends ImageView {

  private boolean inverted;
  private boolean showBadge;

  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(0, false);
      showBadge = typedArray.getBoolean(1, false);
      typedArray.recycle();
    }
  }

  public void setAvatar(final @Nullable Recipients recipients, boolean quickContactEnabled) {
    if (recipients != null) {
      Context       context         = getContext();
      MasterSecret  masterSecret    = KeyCachingService.getMasterSecret(context);
      MaterialColor backgroundColor = recipients.getColor();

      setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor.toConversationColor(getContext()), inverted));
      setAvatarClickHandler(recipients, quickContactEnabled);
      setTag(recipients);
      if (showBadge) new BadgeResolutionTask(context, masterSecret).execute(recipients);
    } else {
      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR.toConversationColor(getContext()), inverted));
      setOnClickListener(null);
      setTag(null);
    }
  }

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatarClickHandler(final Recipients recipients, boolean quickContactEnabled) {
    if (!recipients.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Recipient recipient = recipients.getPrimaryRecipient();

          if (recipient != null && recipient.getContactUri() != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
          } else if (recipient != null) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      setOnClickListener(null);
    }
  }

  private class BadgeResolutionTask extends AsyncTask<Recipients,Void,Pair<Recipients, Boolean>> {
    private final Context context;
    private       MasterSecret masterSecret;
    private final List<SubscriptionInfoCompat> activeSubscriptions;

    public BadgeResolutionTask(Context context, MasterSecret masterSecret) {
      this.context = context;
      this.masterSecret = masterSecret;
      this.activeSubscriptions = SubscriptionManagerCompat.from(context).getActiveSubscriptionInfoList();
    }

    @Override
    protected Pair<Recipients, Boolean> doInBackground(Recipients... recipients) {
      Boolean isSecureSmsDestination = masterSecret != null &&
                                       SessionUtil.hasAtLeastOneSession(context, masterSecret, recipients[0].getPrimaryRecipient().getNumber(), activeSubscriptions);
      return new Pair<>(recipients[0], isSecureSmsDestination);
    }

    @Override
    protected void onPostExecute(Pair<Recipients, Boolean> result) {
      if (getTag() == result.first && result.second) {
        final Drawable badged = new LayerDrawable(new Drawable[] {
            getDrawable(),
            ContextCompat.getDrawable(context, R.drawable.badge_drawable)
        });

        setImageDrawable(badged);
      }
    }
  }
}
