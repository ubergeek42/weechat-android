package com.ubergeek42.WeechatAndroid;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.EditTextPreferenceFix;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.RingtonePreferenceFix;
import android.support.v7.preference.ThemePreference;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import android.support.v7.preference.FontPreference;

public class PreferencesActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    final static private String KEY = "key";

    final static private String PREF_CONNECTION_GROUP = "connection_group";
    final static private String PREF_CONNECTION_TYPE = "connection_type";
    final static private String PREF_TYPE_SSH = "ssh";
    final static private String PREF_TYPE_STUNNEL = "stunnel";
    final static private String PREF_TYPE_PLAIN = "plain";
    final static private String PREF_STUNNEL_GROUP = "stunnel_group";
    final static private String PREF_SSH_GROUP = "ssh_group";
    final static private String PREF_NOTIFICATION_SOUND = "notification_sound";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.preferences);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        // this is exactly the place for the following statement. why? no idea.
        if (savedInstanceState != null)
            return;

        Fragment p = getSupportFragmentManager().findFragmentByTag(null);
        if (p == null) p = new PreferencesFragment();

        String key = getIntent().getStringExtra(KEY);
        if (key != null) {
            Bundle args = new Bundle();
            args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);
            p.setArguments(args);
        }

        getSupportFragmentManager().beginTransaction()
                .add(R.id.preferences, p, null)
                .commit();
    }

    @Override public boolean onPreferenceStartScreen(PreferenceFragmentCompat preferenceFragmentCompat, PreferenceScreen preferenceScreen) {
        Intent intent = new Intent(PreferencesActivity.this, PreferencesActivity.class);
        intent.putExtra(KEY, preferenceScreen.getKey());
        startActivity(intent);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class PreferencesFragment extends PreferenceFragmentCompat implements DialogPreference.TargetFragment, Preference.OnPreferenceChangeListener {

        private static final String FRAGMENT_DIALOG_TAG = "android.support.v7.preference.PreferenceFragment.DIALOG";
        private String key;
        private Preference stunnelGroup = null;
        private Preference sshGroup = null;

        @Override public void onDisplayPreferenceDialog(Preference preference) {
            final DialogFragment f;

            if (preference instanceof FontPreference)
                f = FontPreference.FontPreferenceFragment.newInstance(preference.getKey());
            else if (preference instanceof ThemePreference)
                f = ThemePreference.ThemePreferenceFragment.newInstance(preference.getKey());
            else if (preference instanceof EditTextPreferenceFix)
                f = EditTextPreferenceFix.EditTextPreferenceFixFragment.newInstance(preference.getKey());
            else if (preference instanceof RingtonePreferenceFix) {
                Intent intent = ((RingtonePreferenceFix) preference).makeRingtoneRequestIntent();
                startActivityForResult(intent, 0);
                return;
            } else {
                super.onDisplayPreferenceDialog(preference);
                return;
            }

            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), FRAGMENT_DIALOG_TAG);
        }

        // this makes fragment display preferences. rootKey is the key of the preference screen
        // that this fragment is supposed to display. the key is set in activity's onCreate
        @Override public void onCreatePreferences(Bundle bundle, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, key = rootKey);
            if (PREF_CONNECTION_GROUP.equals(rootKey)) {
                stunnelGroup = findPreference(PREF_STUNNEL_GROUP);
                sshGroup = findPreference(PREF_SSH_GROUP);
                findPreference(PREF_CONNECTION_TYPE).setOnPreferenceChangeListener(this);
                showHideStuff(getPreferenceScreen().getSharedPreferences().getString(PREF_CONNECTION_TYPE, PREF_TYPE_PLAIN));
            }
        }

        // this only sets the title of the action bar
        @Override public void onActivityCreated(Bundle savedInstanceState) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) actionBar.setTitle((key == null) ? "Settings" : findPreference(key).getTitle());
            super.onActivityCreated(savedInstanceState);
        }

        // this is required for RingtonePreferenceFix, which requires an activity to operate
        @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == RESULT_OK)
                ((RingtonePreferenceFix) findPreference(PREF_NOTIFICATION_SOUND)).onActivityResult(data);
        }

        @Override public boolean onPreferenceChange(Preference preference, Object o) {
            showHideStuff((String) o);
            return true;
        }

        // this hides and shows stunnel / ssh preference screens
        // must not be called when the settings do not exist in the tree
        private void showHideStuff(String type) {
            if (PREF_TYPE_STUNNEL.equals(type)) getPreferenceScreen().addPreference(stunnelGroup);
            else getPreferenceScreen().removePreference(stunnelGroup);
            if (PREF_TYPE_SSH.equals(type)) getPreferenceScreen().addPreference(sshGroup);
            else getPreferenceScreen().removePreference(sshGroup);
        }
    }
}