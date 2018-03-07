package org.smssecure.smssecure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.MasterSecretUtil;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.util.SilencePreferences;

import java.util.Locale;

public abstract class PassphraseRequiredActionBarActivity extends BaseActionBarActivity implements MasterSecretListener {
  private static final String TAG = PassphraseRequiredActionBarActivity.class.getSimpleName();

  public static final String LOCALE_EXTRA = "locale_extra";

  private static final int STATE_NORMAL            = 0;
  private static final int STATE_CREATE_PASSPHRASE = 1;
  private static final int STATE_PROMPT_PASSPHRASE = 2;
  private static final int STATE_UPGRADE_DATABASE  = 3;
  private static final int STATE_INTRO_SCREEN      = 4;

  private BroadcastReceiver clearKeyReceiver;
  private boolean           isVisible;

  @Override
  protected final void onCreate(Bundle savedInstanceState) {
    Log.w(TAG, "onCreate(" + savedInstanceState + ")");
    onPreCreate();
    final MasterSecret masterSecret = KeyCachingService.getMasterSecret(this);
    routeApplicationState(masterSecret);
    super.onCreate(savedInstanceState);
    if (!isFinishing()) {
      initializeClearKeyReceiver();
      onCreate(savedInstanceState, masterSecret);
    }
  }

  protected void onPreCreate() {}
  protected void onCreate(Bundle savedInstanceState, @NonNull MasterSecret masterSecret) {}

  @Override
  protected void onResume() {
    Log.w(TAG, "onResume()");
    super.onResume();
    KeyCachingService.registerPassphraseActivityStarted(this);
    isVisible = true;
  }

  @Override
  protected void onPause() {
    Log.w(TAG, "onPause()");
    super.onPause();
    KeyCachingService.registerPassphraseActivityStopped(this);
    isVisible = false;
  }

  @Override
  protected void onDestroy() {
    Log.w(TAG, "onDestroy()");
    super.onDestroy();
    removeClearKeyReceiver(this);
  }

  @Override
  public void onMasterSecretCleared() {
    Log.w(TAG, "onMasterSecretCleared()");
    if (isVisible) routeApplicationState(null);
    else           finish();
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @NonNull MasterSecret masterSecret)
  {
    return initFragment(target, fragment, masterSecret, null);
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @NonNull MasterSecret masterSecret,
                                                @Nullable Locale locale)
  {
    return initFragment(target, fragment, masterSecret, locale, null);
  }

  protected <T extends Fragment> T initFragment(@IdRes int target,
                                                @NonNull T fragment,
                                                @NonNull MasterSecret masterSecret,
                                                @Nullable Locale locale,
                                                @Nullable Bundle extras)
  {
    Bundle args = new Bundle();
    args.putParcelable("master_secret", masterSecret);
    args.putSerializable(LOCALE_EXTRA, locale);

    if (extras != null) {
      args.putAll(extras);
    }

    fragment.setArguments(args);
    getSupportFragmentManager().beginTransaction()
                               .replace(target, fragment)
                               .commit();
    return fragment;
  }

  private void routeApplicationState(MasterSecret masterSecret) {
    Intent intent = getIntentForState(masterSecret, getApplicationState(masterSecret));
    if (intent != null) {
      startActivity(intent);
      finish();
    }
  }

  private Intent getIntentForState(MasterSecret masterSecret, int state) {
    Log.w(TAG, "routeApplicationState(), state: " + state);

    switch (state) {
    case STATE_CREATE_PASSPHRASE: return getCreatePassphraseIntent();
    case STATE_PROMPT_PASSPHRASE: return getPromptPassphraseIntent();
    case STATE_UPGRADE_DATABASE:  return getUpgradeDatabaseIntent(masterSecret);
    case STATE_INTRO_SCREEN:      return getIntroScreenIntent();
    default:                      return null;
    }
  }

  private int getApplicationState(MasterSecret masterSecret) {
    if (!MasterSecretUtil.isPassphraseInitialized(this)) {
      return STATE_CREATE_PASSPHRASE;
    } else if (SilencePreferences.isFirstRun(this)) {
      return STATE_INTRO_SCREEN;
    } else if (masterSecret == null) {
      return STATE_PROMPT_PASSPHRASE;
    } else if (DatabaseUpgradeActivity.isUpdate(this)) {
      return STATE_UPGRADE_DATABASE;
    } else {
      return STATE_NORMAL;
    }
  }

  private Intent getCreatePassphraseIntent() {
    return getRoutedIntent(PassphraseCreateActivity.class, getIntent(), null);
  }

  private Intent getPromptPassphraseIntent() {
    return getRoutedIntent(PassphrasePromptActivity.class, getIntent(), null);
  }

  private Intent getUpgradeDatabaseIntent(MasterSecret masterSecret) {
    return getRoutedIntent(DatabaseUpgradeActivity.class, getConversationListIntent(), masterSecret);
  }

  private Intent getRoutedIntent(Class<?> destination, @Nullable Intent nextIntent, @Nullable MasterSecret masterSecret) {
    final Intent intent = new Intent(this, destination);
    if (nextIntent != null)   intent.putExtra("next_intent", nextIntent);
    if (masterSecret != null) intent.putExtra("master_secret", masterSecret);
    return intent;
  }

  private Intent getConversationListIntent() {
    return new Intent(this, ConversationListActivity.class);
  }

  private Intent getIntroScreenIntent() {
    return getRoutedIntent(IntroScreenActivity.class, getIntent(), null);
  }

  private void initializeClearKeyReceiver() {
    Log.w(TAG, "initializeClearKeyReceiver()");
    this.clearKeyReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Log.w(TAG, "onReceive() for clear key event");
        onMasterSecretCleared();
      }
    };

    IntentFilter filter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);
    registerReceiver(clearKeyReceiver, filter, KeyCachingService.KEY_PERMISSION, null);
  }

  private void removeClearKeyReceiver(Context context) {
    if (clearKeyReceiver != null) {
      context.unregisterReceiver(clearKeyReceiver);
      clearKeyReceiver = null;
    }
  }
}
