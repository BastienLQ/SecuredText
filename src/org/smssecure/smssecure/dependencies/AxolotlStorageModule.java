package org.smssecure.smssecure.dependencies;

import android.content.Context;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.storage.SMSSecureAxolotlStore;
import org.smssecure.smssecure.jobs.CleanPreKeysJob;
import org.smssecure.libaxolotl.state.SignedPreKeyStore;

import dagger.Module;
import dagger.Provides;

@Module (complete = false, injects = {CleanPreKeysJob.class})
public class AxolotlStorageModule {

  private final Context context;

  public AxolotlStorageModule(Context context) {
    this.context = context;
  }

  @Provides SignedPreKeyStoreFactory provideSignedPreKeyStoreFactory() {
    return new SignedPreKeyStoreFactory() {
      @Override
      public SignedPreKeyStore create(MasterSecret masterSecret) {
        return new SMSSecureAxolotlStore(context, masterSecret);
      }
    };
  }

  public static interface SignedPreKeyStoreFactory {
    public SignedPreKeyStore create(MasterSecret masterSecret);
  }
}
