package org.smssecure.smssecure.jobs;

import android.content.Context;
import android.util.Log;

import org.smssecure.smssecure.crypto.IdentityKeyUtil;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.PreKeyUtil;
import org.smssecure.smssecure.dependencies.InjectableType;
import org.smssecure.smssecure.util.ParcelUtil;
import org.smssecure.smssecure.util.SMSSecurePreferences;
import org.smssecure.jobqueue.EncryptionKeys;
import org.smssecure.jobqueue.JobParameters;
import org.smssecure.jobqueue.requirements.NetworkRequirement;
import org.smssecure.libaxolotl.IdentityKeyPair;
import org.smssecure.libaxolotl.state.SignedPreKeyRecord;
import org.smssecure.textsecure.api.TextSecureAccountManager;
import org.smssecure.textsecure.api.push.exceptions.PushNetworkException;

import java.io.IOException;

import javax.inject.Inject;

public class CreateSignedPreKeyJob extends ContextJob implements InjectableType {

  private static final String TAG = CreateSignedPreKeyJob.class.getSimpleName();

  @Inject transient TextSecureAccountManager accountManager;

  public CreateSignedPreKeyJob(Context context, MasterSecret masterSecret) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new NetworkRequirement(context))
                                .withEncryption(new EncryptionKeys(ParcelUtil.serialize(masterSecret)))
                                .withGroupId(CreateSignedPreKeyJob.class.getSimpleName())
                                .create());
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws IOException {
    MasterSecret masterSecret = ParcelUtil.deserialize(getEncryptionKeys().getEncoded(), MasterSecret.CREATOR);

    if (SMSSecurePreferences.isSignedPreKeyRegistered(context)) {
      Log.w(TAG, "Signed prekey already registered...");
      return;
    }

    IdentityKeyPair    identityKeyPair    = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
    SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, masterSecret, identityKeyPair);

    accountManager.setSignedPreKey(signedPreKeyRecord);
    SMSSecurePreferences.setSignedPreKeyRegistered(context, true);
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }
}
