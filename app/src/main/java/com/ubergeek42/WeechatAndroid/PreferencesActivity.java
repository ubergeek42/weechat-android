package com.ubergeek42.WeechatAndroid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ClearCertPreference;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreferenceFix;
import androidx.preference.FilePreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.RingtonePreferenceFix;
import androidx.preference.ThemePreference;
import android.view.MenuItem;

import androidx.preference.FontPreference;
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
        if (PREF_NOTIFICATION_GROUP.equals(preferenceScreen.getKey()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
            return true;
        }
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
                boolean granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
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
            f.show(requireFragmentManager(), FRAGMENT_DIALOG_TAG);
        }

        @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            onDisplayPreferenceDialog(null);
        }

        // this makes fragment display preferences. key is the key of the preference screen
        // that this fragment is supposed to display. the key is set in activity's onCreate
        @Override public void onCreatePreferences(Bundle bundle, String key) {
            setPreferencesFromResource(R.xml.preferences, this.key = key);

            fixMultiLineTitles(getPreferenceScreen());

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
                listenTo = new String[] {PREF_TEXT_SIZE, PREF_FIXED_WIDTH, PREF_TIMESTAMP_FORMAT};
            else if (PREF_THEME_GROUP.equals(key)) {
                enableDisableThemeSwitch(getPreferenceScreen().getSharedPreferences().getString(PREF_THEME, PREF_THEME_D));
                listenTo = new String[]{PREF_THEME};
            } else if (PREF_BUFFERLIST_GROUP.equals(key)) {
                enableDisableGestureExclusionZoneSwitch();
            }

            for (String p : listenTo)
                findPreference(p).setOnPreferenceChangeListener(this);
        }

        // this only sets the title of the action bar
        @Override public void onActivityCreated(Bundle savedInstanceState) {
            ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
            if (actionBar != null)
                actionBar.setTitle((key == null) ? getString(R.string.menu_preferences) : findPreference(key).getTitle());
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
            } else if (Utils.isAnyOf(key, PREF_TEXT_SIZE, PREF_FIXED_WIDTH, PREF_PORT, PREF_SSH_PORT, PREF_PING_IDLE, PREF_PING_TIMEOUT)) {
                valid = Utils.isAllDigits((String) o);
                toast = R.string.pref_number_invalid;
            } else if (PREF_TIMESTAMP_FORMAT.equals(key)) {
                valid = Utils.isValidTimestampFormat((String) o);
                toast = R.string.pref_timestamp_invalid;
            } else if (PREF_CONNECTION_TYPE.equals(key)) {
                showHideStuff((String) o);
            } else if (PREF_THEME.equals(key)) {
                enableDisableThemeSwitch((String) o);
            }
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

        // visually disable and uncheck the theme switch preference if the theme is chosen by system
        private void enableDisableThemeSwitch(String theme) {
            CheckBoxPreference themeSwitchPreference = getPreferenceScreen().findPreference(PREF_THEME_SWITCH);
            if (themeSwitchPreference == null) return;

            boolean system = PREF_THEME_SYSTEM.equals(theme);
            themeSwitchPreference.setEnabled(!system);
            if (system) themeSwitchPreference.setChecked(false);
        }

        private void enableDisableGestureExclusionZoneSwitch() {
            Preference p = getPreferenceScreen().findPreference(PREF_USE_GESTURE_EXCLUSION_ZONE);
            if (p != null) p.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        }

        // recursively make all currently visible preference titles multiline
        private static void fixMultiLineTitles(PreferenceGroup screen) {
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                p.setSingleLineTitle(false);
                if (p instanceof PreferenceGroup && !(p instanceof PreferenceScreen)) fixMultiLineTitles((PreferenceGroup) p);
            }
        }
    }
}
