package org.smssecure.smssecure.preferences;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.ContactsContract;
import android.support.v4.preference.PreferenceFragment;
import android.util.Log;

import org.smssecure.smssecure.ApplicationPreferencesActivity;
import org.smssecure.smssecure.LogSubmitActivity;
import org.smssecure.smssecure.R;
import org.smssecure.smssecure.contacts.ContactAccessor;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.util.SilencePreferences;

public class AdvancedPreferenceFragment extends ListSummaryPreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String SYSTEM_EMOJI_PREF     = SilencePreferences.SYSTEM_EMOJI_PREF;
  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    addPreferencesFromResource(R.xml.preferences_advanced);

    this.findPreference(SUBMIT_DEBUG_LOG_PREF)
      .setOnPreferenceClickListener(new SubmitDebugLogListener());

    this.findPreference(SilencePreferences.ENTER_KEY_TYPE).setOnPreferenceChangeListener(new ListSummaryListener());
    initializeListSummary((ListPreference) this.findPreference(SilencePreferences.ENTER_KEY_TYPE));
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), LogSubmitActivity.class);
      startActivity(intent);
      return true;
    }
  }
}
