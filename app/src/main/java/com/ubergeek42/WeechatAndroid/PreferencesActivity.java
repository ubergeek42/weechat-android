package com.ubergeek42.WeechatAndroid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ClearCertPreference;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.EditTextPreferenceFix;
import android.support.v7.preference.FilePreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.RingtonePreferenceFix;
import android.support.v7.preference.ThemePreference;
import android.view.MenuItem;

import android.support.v7.preference.FontPreference;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.utils.Utils;
import static com.ubergeek42.WeechatAndroid.utils.Constants.*;

public class PreferencesActivity extends AppCompatActivity implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
    final static private String KEY = "key";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.preferences);

        setSupportActionBar(findViewById(R.id.toolbar));
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
        private Preference sslGroup = null;
        private Preference sshGroup = null;
        private Preference wsPath = null;

        private Preference resumePreference;

        // don't check permissions if preference is null, instead use resumePreference
        @Override public void onDisplayPreferenceDialog(Preference preference) {
            final DialogFragment f;

            if (preference == null) {
                preference = resumePreference;
            } else if (preference instanceof FontPreference || preference instanceof ThemePreference) {
                boolean granted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (!granted) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    resumePreference = preference;
                    return;
                }
            }

            if (preference instanceof FontPreference)
                f = FontPreference.FontPreferenceFragment.newInstance(preference.getKey());
            else if (preference instanceof ThemePreference)
                f = ThemePreference.ThemePreferenceFragment.newInstance(preference.getKey());
            else if (preference instanceof FilePreference)
                f = FilePreference.FilePreferenceFragment.newInstance(preference.getKey(), PREF_SSH_KEY.equals(preference.getKey()) ? 1 : 2);
            else if (preference instanceof EditTextPreferenceFix)
                f = EditTextPreferenceFix.EditTextPreferenceFixFragment.newInstance(preference.getKey());
            else if (preference instanceof ClearCertPreference)
                f = ClearCertPreference.ClearCertPreferenceFragment.newInstance(preference.getKey());
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

        @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            onDisplayPreferenceDialog(null);
        }

        // this makes fragment display preferences. key is the key of the preference screen
        // that this fragment is supposed to display. the key is set in activity's onCreate
        @Override public void onCreatePreferences(Bundle bundle, String key) {
            setPreferencesFromResource(R.xml.preferences, this.key = key);
            String[] listenTo = {};
            if (PREF_CONNECTION_GROUP.equals(key)) {
                sslGroup = findPreference(PREF_SSL_GROUP);
                sshGroup = findPreference(PREF_SSH_GROUP);
                wsPath = findPreference(PREF_WS_PATH);
                showHideStuff(getPreferenceScreen().getSharedPreferences().getString(PREF_CONNECTION_TYPE, PREF_CONNECTION_TYPE_D));
                listenTo = new String[] {PREF_CONNECTION_TYPE, PREF_HOST, PREF_PORT};
            } else if (PREF_SSH_GROUP.equals(key))
                listenTo = new String[] {PREF_SSH_HOST, PREF_SSH_PORT};
            else if (PREF_PING_GROUP.equals(key))
                listenTo = new String[] {PREF_PING_IDLE, PREF_PING_TIMEOUT};
            else if (PREF_LOOKFEEL_GROUP.equals(key))
                listenTo = new String[] {PREF_TEXT_SIZE, PREF_MAX_WIDTH, PREF_TIMESTAMP_FORMAT};

            for (String p : listenTo)
                findPreference(p).setOnPreferenceChangeListener(this);
        }

        // this only sets the title of the action bar
        @Override public void onActivityCreated(Bundle savedInstanceState) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null)
                actionBar.setTitle((key == null) ? getString(R.string.preferences) : findPreference(key).getTitle());
            super.onActivityCreated(savedInstanceState);
        }

        // this is required for RingtonePreferenceFix, which requires an activity to operate
        @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == RESULT_OK) {
                switch (requestCode) {
                    case 0: ((RingtonePreferenceFix) findPreference(PREF_NOTIFICATION_SOUND)).onActivityResult(data); break;
                    case 1: ((FilePreference) findPreference(PREF_SSH_KEY)).onActivityResult(data); break;
                    case 2: ((FilePreference) findPreference(PREF_SSH_KNOWN_HOSTS)).onActivityResult(data); break;
                }
            }
        }

        @Override public boolean onPreferenceChange(Preference preference, Object o) {
            String key = preference.getKey();
            boolean valid = true;
            int toast = -1;
            if (Utils.isAnyOf(key, PREF_HOST, PREF_SSH_HOST)) {
                valid = !((String) o).contains(" ");
                toast = R.string.pref_hostname_invalid;
            } else if (Utils.isAnyOf(key, PREF_TEXT_SIZE, PREF_MAX_WIDTH, PREF_PORT, PREF_SSH_PORT, PREF_PING_IDLE, PREF_PING_TIMEOUT)) {
                valid = Utils.isAllDigits((String) o);
                toast = R.string.pref_number_invalid;
            } else if (PREF_TIMESTAMP_FORMAT.equals(key)) {
                valid = Utils.isValidTimestampFormat((String) o);
                toast = R.string.pref_timestamp_invalid;
            } else if (PREF_CONNECTION_TYPE.equals(key))
                showHideStuff((String) o);
            if (!valid)
                Toast.makeText(getContext(), toast, Toast.LENGTH_SHORT).show();
            return valid;
         }

        // this hides and shows ssl / websocket / ssh preference screens
        // must not be called when the settings do not exist in the tree
        private void showHideStuff(String type) {
            if (Utils.isAnyOf(type, PREF_TYPE_SSL, PREF_TYPE_WEBSOCKET_SSL)) getPreferenceScreen().addPreference(sslGroup);
            else getPreferenceScreen().removePreference(sslGroup);
            if (PREF_TYPE_SSH.equals(type)) getPreferenceScreen().addPreference(sshGroup);
            else getPreferenceScreen().removePreference(sshGroup);
            if (Utils.isAnyOf(type, PREF_TYPE_WEBSOCKET, PREF_TYPE_WEBSOCKET_SSL)) getPreferenceScreen().addPreference(wsPath);
            else getPreferenceScreen().removePreference(wsPath);
        }
    }
}