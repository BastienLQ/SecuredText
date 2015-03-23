package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.smssecure.smssecure.Release;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.GroupDatabase;
import org.smssecure.smssecure.jobs.requirements.MasterSecretRequirement;
import org.smssecure.smssecure.push.SMSSecurePushTrustStore;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.RecipientFormattingException;
import org.smssecure.smssecure.util.BitmapDecodingException;
import org.smssecure.smssecure.util.BitmapUtil;
import org.smssecure.smssecure.util.GroupUtil;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.jobqueue.JobParameters;
import org.smssecure.jobqueue.requirements.NetworkRequirement;
import org.smssecure.libaxolotl.InvalidMessageException;
import org.smssecure.textsecure.api.crypto.AttachmentCipherInputStream;
import org.smssecure.textsecure.internal.push.PushServiceSocket;
import org.smssecure.textsecure.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.smssecure.textsecure.internal.util.StaticCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AvatarDownloadJob extends MasterSecretJob {

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private final byte[] groupId;

  public AvatarDownloadJob(Context context, byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    GroupDatabase             database   = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long   avatarId = record.getAvatarId();
        byte[] key      = record.getAvatarKey();
        String relay    = record.getRelay();

        if (avatarId == -1 || key == null) {
          return;
        }

        attachment = downloadAttachment(relay, avatarId);

        InputStream scaleInputStream   = new AttachmentCipherInputStream(attachment, key);
        InputStream measureInputStream = new AttachmentCipherInputStream(attachment, key);
        Bitmap      avatar             = BitmapUtil.createScaledBitmap(measureInputStream, scaleInputStream, 500, 500);

        database.updateAvatar(groupId, avatar);

        Recipient groupRecipient = RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(groupId), true)
                                                   .getPrimaryRecipient();
        groupRecipient.setContactPhoto(avatar);
      }
    } catch (InvalidMessageException | BitmapDecodingException | NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(Release.PUSH_URL,
                                                     new SMSSecurePushTrustStore(context),
                                                     new StaticCredentialsProvider(SMSSecurePreferences.getLocalNumber(context),
                                                                                   SMSSecurePreferences.getPushServerPassword(context),
                                                                                   null));

    File destination = File.createTempFile("avatar", "tmp");

    destination.deleteOnExit();

    socket.retrieveAttachment(relay, contentLocation, destination);

    return destination;
  }

}
