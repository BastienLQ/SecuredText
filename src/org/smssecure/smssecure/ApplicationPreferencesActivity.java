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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.preference.PreferenceFragment;
import android.widget.Toast;

import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.preferences.AdvancedPreferenceFragment;
import org.smssecure.smssecure.preferences.AppProtectionPreferenceFragment;
import org.smssecure.smssecure.preferences.AppearancePreferenceFragment;
import org.smssecure.smssecure.preferences.NotificationsPreferenceFragment;
import org.smssecure.smssecure.preferences.SmsMmsPreferenceFragment;
import org.smssecure.smssecure.preferences.ChatsPreferenceFragment;
import org.smssecure.smssecure.service.KeyCachingService;
import org.smssecure.smssecure.util.DynamicLanguage;
import org.smssecure.smssecure.util.DynamicTheme;
import org.smssecure.smssecure.util.SilencePreferences;

/**
 * The Activity for application preference display and management.
 *
 * @author Moxie Marlinspike
 *
 */

public class ApplicationPreferencesActivity extends PassphraseRequiredActionBarActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
  private static final String TAG = ApplicationPreferencesActivity.class.getSimpleName();

  private static final String PREFERENCE_CATEGORY_SMS_MMS        = "preference_category_sms_mms";
  private static final String PREFERENCE_CATEGORY_NOTIFICATIONS  = "preference_category_notifications";
  private static final String PREFERENCE_CATEGORY_APP_PROTECTION = "preference_category_app_protection";
  private static final String PREFERENCE_CATEGORY_APPEARANCE     = "preference_category_appearance";
  private static final String PREFERENCE_CATEGORY_CHATS          = "preference_category_chats";
  private static final String PREFERENCE_CATEGORY_ADVANCED       = "preference_category_advanced";
  private static final String PREFERENCE_ABOUT                   = "preference_about";
  private static final String PREFERENCE_PRIVACY_POLICY          = "preference_privacy_policy";

  private static final String PUSH_MESSAGING_PREF = "pref_toggle_push_messaging";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    if (icicle == null) {
      initFragment(android.R.id.content, new ApplicationPreferenceFragment(), masterSecret);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    Fragment fragment = getSupportFragmentManager().findFragmentById(android.R.id.content);
    fragment.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public boolean onSupportNavigateUp() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    if (fragmentManager.getBackStackEntryCount() > 0) {
      fragmentManager.popBackStack();
    } else {
      Intent intent = new Intent(this, ConversationListActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      startActivity(intent);
      finish();
    }
    return true;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    if (key.equals(SilencePreferences.THEME_PREF)) {
      if (VERSION.SDK_INT >= 11) recreate();
      else                       dynamicTheme.onResume(this);
    } else if (key.equals(SilencePreferences.LANGUAGE_PREF)) {
      if (VERSION.SDK_INT >= 11) recreate();
      else                       dynamicLanguage.onResume(this);

      Intent intent = new Intent(this, KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCALE_CHANGE_EVENT);
      startService(intent);
    }
  }

  public static class ApplicationPreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle icicle) {
      super.onCreate(icicle);

      addPreferencesFromResource(R.xml.preferences);

      MasterSecret masterSecret = getArguments().getParcelable("master_secret");
      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
        .setOnPreferenceClickListener(new CategoryClickListener(masterSecret, PREFERENCE_CATEGORY_SMS_MMS));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
        .setOnPreferenceClickListener(new CategoryClickListener(masterSecret, PREFERENCE_CATEGORY_NOTIFICATIONS));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
        .setOnPreferenceClickListener(new CategoryClickListener(masterSecret, PREFERENCE_CATEGORY_APP_PROTECTION));
      this.findPreference(PREFERENCE_CATEGORY_APPEARANCE)
        .setOnPreferenceClickListener(new CategoryClickListener(masterSecret, PREFERENCE_CATEGORY_APPEARANCE));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
        .setOnPreferenceClickListener(new CategoryClickListener(masterSecret, PREFERENCE_CATEGORY_CHATS));
      this.findPreference(PREFERENCE_CATEGORY_ADVANCED)
        .setOnPreferenceClickListener(new CategoryClickListener(masterSecret, PREFERENCE_CATEGORY_ADVANCED));

      this.findPreference(PREFERENCE_PRIVACY_POLICY)
        .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            handlePrivacyPolicy();
            return true;
          }
        });
    }

    @Override
    public void onResume() {
      super.onResume();
      ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.text_secure_normal__menu_settings);
      setCategorySummaries();
    }

    private void handlePrivacyPolicy() {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://silence.im/privacy")));
      } catch (ActivityNotFoundException e) {
        Toast.makeText(((ApplicationPreferencesActivity) getActivity()).getApplicationContext(), R.string.ConversationActivity_cant_open_link, Toast.LENGTH_LONG).show();
      }
    }

    private void setCategorySummaries() {
      this.findPreference(PREFERENCE_CATEGORY_SMS_MMS)
          .setSummary(SmsMmsPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_NOTIFICATIONS)
          .setSummary(NotificationsPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_APP_PROTECTION)
          .setSummary(AppProtectionPreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_APPEARANCE)
          .setSummary(AppearancePreferenceFragment.getSummary(getActivity()));
      this.findPreference(PREFERENCE_CATEGORY_CHATS)
          .setSummary(ChatsPreferenceFragment.getSummary(getActivity()));

      String version = String.format(this.getString(R.string.preferences__about_version),  BuildConfig.VERSION_NAME);

      this.findPreference(PREFERENCE_ABOUT)
          .setSummary(version);
    }

    private class CategoryClickListener implements Preference.OnPreferenceClickListener {
      private MasterSecret masterSecret;
      private String       category;

      public CategoryClickListener(MasterSecret masterSecret, String category) {
        this.masterSecret = masterSecret;
        this.category     = category;
      }

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Fragment fragment;

        switch (category) {
        case PREFERENCE_CATEGORY_SMS_MMS:
          fragment = new SmsMmsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_NOTIFICATIONS:
          fragment = new NotificationsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_APP_PROTECTION:
          fragment = new AppProtectionPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_APPEARANCE:
          fragment = new AppearancePreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_CHATS:
          fragment = new ChatsPreferenceFragment();
          break;
        case PREFERENCE_CATEGORY_ADVANCED:
          fragment = new AdvancedPreferenceFragment();
          break;
        default:
          throw new AssertionError();
        }

        Bundle args = new Bundle();
        args.putParcelable("master_secret", masterSecret);
        fragment.setArguments(args);

        FragmentManager     fragmentManager     = getActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(android.R.id.content, fragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();

        return true;
      }
    }
  }
}
